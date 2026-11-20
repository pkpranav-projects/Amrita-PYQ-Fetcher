package com.amrita.pyqfetcher.work

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.Constraints
import androidx.work.OutOfQuotaPolicy
import com.amrita.pyqfetcher.MainActivity
import com.amrita.pyqfetcher.R
import com.amrita.pyqfetcher.core.Config
import com.amrita.pyqfetcher.data.db.AppDatabase
import com.amrita.pyqfetcher.data.db.QueueItemEntity
import com.amrita.pyqfetcher.data.repo.DownloadOutcome
import com.amrita.pyqfetcher.data.repo.DownloadRepository
import com.amrita.pyqfetcher.data.repo.FileStore
import com.amrita.pyqfetcher.data.prefs.SaveMode
import kotlinx.coroutines.Dispatchers
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

const val DOWNLOAD_CHANNEL = "downloads"
private const val WORK_NAME = "bulk-download"
private const val PROGRESS_NOTIFICATION_ID = 4101
private const val DONE_NOTIFICATION_ID = 4102

@Singleton
class DownloadNotifier @Inject constructor(@ApplicationContext private val context: Context) {
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    DOWNLOAD_CHANNEL,
                    context.getString(R.string.channel_downloads),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = context.getString(R.string.channel_downloads_desc) },
            )
        }
    }

    private fun contentIntent() = android.app.PendingIntent.getActivity(
        context, 0, android.content.Intent(context, MainActivity::class.java),
        android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun progress(workId: UUID, index: Int, total: Int, current: String) =
        NotificationCompat.Builder(context, DOWNLOAD_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Amrita PYQ Fetcher")
            .setContentText("[$index/$total] Downloading $current")
            .setProgress(total, (index - 1).coerceAtLeast(0), false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent())
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Pause",
                WorkManager.getInstance(context).createCancelPendingIntent(workId),
            )
            .build()

    @SuppressLint("MissingPermission")
    fun updateProgress(workId: UUID, index: Int, total: Int, current: String) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        runCatching { NotificationManagerCompat.from(context).notify(PROGRESS_NOTIFICATION_ID, progress(workId, index, total, current)) }
    }

    @SuppressLint("MissingPermission")
    fun finished(saved: Int, skipped: Int, failed: Int, extra: String? = null) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val text = buildString {
            append("$saved saved")
            if (skipped > 0) append(", $skipped already saved")
            if (failed > 0) append(", $failed failed")
            if (extra != null) append(". ").append(extra)
        }
        val n = NotificationCompat.Builder(context, DOWNLOAD_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Downloads finished")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(DONE_NOTIFICATION_ID, n) }
    }

    @SuppressLint("MissingPermission")
    fun newPapers(count: Int, semester: Int) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val n = NotificationCompat.Builder(context, DOWNLOAD_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentTitle("$count new paper${if (count == 1) "" else "s"} for Semester $semester")
            .setContentText("Open the app to download them before your exams.")
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(4103, n) }
    }

    @SuppressLint("MissingPermission")
    fun clearProgress() {
        runCatching { NotificationManagerCompat.from(context).cancel(PROGRESS_NOTIFICATION_ID) }
    }
}

@HiltWorker
class BulkDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val db: AppDatabase,
    private val downloads: DownloadRepository,
    private val notifier: DownloadNotifier,
    private val files: FileStore,
) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo = foreground(1, 1, "…")

    private fun foreground(index: Int, total: Int, current: String): ForegroundInfo {
        notifier.ensureChannel()
        val n = notifier.progress(id, index, total, current)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(PROGRESS_NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(PROGRESS_NOTIFICATION_ID, n)
        }
    }

    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB) ?: return Result.failure()
        val demo = inputData.getBoolean(KEY_DEMO, false)
        val spec = JobSpec.decode(jobId)
        val queue = db.queue()

        queue.resetDownloading(jobId)
        val items = queue.pending(jobId)
        if (items.isEmpty()) return Result.success()

        val total = items.size
        val started = AtomicInteger(0)
        val counts = Counts()
        runCatching { setForeground(foreground(1, total, "…")) }

        val gate = Semaphore(Config.DOWNLOAD_PARALLELISM)
        try {
            coroutineScope {
                items.map { item ->
                    async { gate.withPermit { processItem(item, demo, spec, total, started, counts) } }
                }.awaitAll()
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) { queue.resetDownloading(jobId) }
            notifier.clearProgress()
            throw e
        }

        // "One ZIP" mode: bundle every paper of the job that is now on the device.
        var zipNote: String? = null
        if (spec.mode == SaveMode.ZIP) {
            val entries = queue.itemsOf(jobId).mapNotNull { item ->
                val f = downloads.localFile(item.paperId, demo) ?: return@mapNotNull null
                val row = db.catalog().paperRow(item.paperId) ?: return@mapNotNull null
                "${row.subjectCode}/${if (row.paper.year > 0) row.paper.year else "unknown"}" to f
            }
            if (entries.isNotEmpty()) {
                zipNote = withContext(Dispatchers.IO) {
                    val zip = files.zipFiles(spec.zipName.ifBlank { "AmritaPYQ_Papers" }, entries)
                    val ok = files.exportToDownloads(listOf(zip)) == 1
                    val name = zip.name
                    zip.delete()
                    if (ok) "ZIP saved to Downloads/AmritaPYQ/$name" else "Could not save the ZIP"
                }
            }
        }

        notifier.clearProgress()
        notifier.finished(counts.saved.get(), counts.skipped.get(), counts.failed.get(), zipNote)
        return Result.success()
    }

    private class Counts {
        val saved = AtomicInteger(0)
        val skipped = AtomicInteger(0)
        val failed = AtomicInteger(0)
    }

    private suspend fun processItem(
        item: QueueItemEntity,
        demo: Boolean,
        spec: JobSpec,
        total: Int,
        started: AtomicInteger,
        counts: Counts,
    ) {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        val queue = db.queue()
        queue.setState(item.id, "DOWNLOADING", null)
        delay(100) // polite throttling between requests to the campus server
        val row = db.catalog().paperRow(item.paperId)
        val label = row?.let { "${it.subjectCode} ${if (it.paper.year > 0) it.paper.year else ""}".trim() } ?: "paper"
        val index = started.incrementAndGet()
        notifier.updateProgress(id, index.coerceAtMost(total), total, label)

        // Up to 3 attempts for transient failures (timeouts, dropped Wi-Fi, 5xx); none for 404 / non-PDF.
        var outcome: DownloadOutcome = DownloadOutcome.Failed("Not started")
        for (attempt in 1..3) {
            outcome = try {
                downloads.downloadOne(item.paperId, demo)
            } catch (e: CancellationException) {
                withContext(NonCancellable) { queue.setState(item.id, "PENDING", null) }
                throw e
            } catch (e: Exception) {
                DownloadOutcome.Failed(e.message ?: "Unexpected error", retryable = true)
            }
            val failed = outcome as? DownloadOutcome.Failed
            if (failed == null || !failed.retryable || attempt == 3) break
            delay(attempt * 2000L)
        }

        if (outcome !is DownloadOutcome.Failed && spec.mode == SaveMode.ALSO_DOWNLOADS) {
            downloads.localFile(item.paperId, demo)?.let { f -> withContext(Dispatchers.IO) { files.exportToDownloads(listOf(f)) } }
        }
        when (val o = outcome) {
            is DownloadOutcome.Saved -> { counts.saved.incrementAndGet(); queue.setState(item.id, "DONE", null) }
            DownloadOutcome.Skipped -> { counts.skipped.incrementAndGet(); queue.setState(item.id, "SKIPPED", null) }
            is DownloadOutcome.Failed -> { counts.failed.incrementAndGet(); queue.setState(item.id, "FAILED", o.reason) }
        }
    }

    companion object {
        const val KEY_JOB = "jobId"
        const val KEY_DEMO = "demo"
    }
}

/**
 * Per-job options. Encoded into the job id so a paused job resumes with the same save mode
 * without needing an extra table.
 */
data class JobSpec(val uuid: String, val mode: SaveMode, val zipName: String) {
    fun encode() = "$uuid~${mode.name}~${zipName.replace("~", "_")}"

    companion object {
        fun decode(jobId: String): JobSpec {
            val parts = jobId.split('~', limit = 3)
            val mode = parts.getOrNull(1)?.let { m -> SaveMode.entries.firstOrNull { it.name == m } } ?: SaveMode.APP_PRIVATE
            return JobSpec(parts[0], mode, parts.getOrNull(2).orEmpty())
        }
    }
}

/** Creates queue rows and schedules the WorkManager worker; also pause / resume / retry. */
@Singleton
class DownloadScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val downloads: DownloadRepository,
) {
    private val wm get() = WorkManager.getInstance(context)

    /** [queued] = papers that will actually be fetched, [alreadySaved] = papers already on the device. */
    data class Enqueued(val queued: Int, val alreadySaved: Int, val scheduled: Boolean)

    /**
     * Queues papers for the background worker. In the plain library mode, papers already on the
     * device are left out (smart de-duplication). For the Downloads / ZIP modes every paper is
     * queued so saved ones still get copied or bundled — they are not downloaded again.
     */
    suspend fun enqueue(paperIds: List<String>, demo: Boolean, mode: SaveMode, zipName: String): Enqueued {
        val distinct = paperIds.distinct()
        val missing = distinct.filter { downloads.localFile(it, demo) == null }
        val toQueue = if (mode == SaveMode.APP_PRIVATE) missing else distinct
        if (toQueue.isEmpty()) return Enqueued(0, distinct.size, scheduled = false)
        // A fresh batch starts a fresh progress list once nothing older is still running.
        if (db.queue().activeJobIds().isEmpty()) db.queue().clearFinished()
        val jobId = JobSpec(UUID.randomUUID().toString(), mode, zipName).encode()
        db.queue().insertAll(toQueue.mapIndexed { i, id -> QueueItemEntity(jobId = jobId, paperId = id, state = "PENDING", position = i) })
        schedule(jobId, demo)
        return Enqueued(missing.size, distinct.size - missing.size, scheduled = true)
    }

    private fun schedule(jobId: String, demo: Boolean) {
        val req = OneTimeWorkRequestBuilder<BulkDownloadWorker>()
            .setInputData(Data.Builder().putString(BulkDownloadWorker.KEY_JOB, jobId).putBoolean(BulkDownloadWorker.KEY_DEMO, demo).build())
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if (demo) NetworkType.NOT_REQUIRED else NetworkType.CONNECTED).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        wm.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, req)
    }

    /** Pause: stops the worker but keeps pending rows so they can be resumed. */
    fun pause() { wm.cancelUniqueWork(WORK_NAME) }

    /** Cancel: stops the worker and drops everything not yet downloaded. */
    suspend fun cancelAll() {
        wm.cancelUniqueWork(WORK_NAME)
        db.queue().dropPending()
    }

    suspend fun resume(demo: Boolean) {
        db.queue().activeJobIds().forEach { schedule(it, demo) }
    }

    suspend fun retryFailed(demo: Boolean) {
        db.queue().retryAllFailed()
        resume(demo)
    }

    suspend fun clearFinished() = db.queue().clearFinished()

    fun isRunning(): Flow<Boolean> = wm.getWorkInfosForUniqueWorkFlow(WORK_NAME).map { list ->
        list.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }
    }
}
