package com.amrita.pyqfetcher.ui

import com.amrita.pyqfetcher.core.AppMode
import com.amrita.pyqfetcher.core.ModeProvider
import com.amrita.pyqfetcher.core.PrefsState
import com.amrita.pyqfetcher.data.db.AppDatabase
import com.amrita.pyqfetcher.data.prefs.SaveMode
import com.amrita.pyqfetcher.data.repo.DownloadOutcome
import com.amrita.pyqfetcher.data.repo.DownloadProgress
import com.amrita.pyqfetcher.data.repo.DownloadRepository
import com.amrita.pyqfetcher.domain.StudyStatus
import com.amrita.pyqfetcher.work.DownloadScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/** A snackbar, optionally with one action (e.g. Undo). [onResult] gets true if the action was tapped. */
data class Msg(val text: String, val action: String? = null, val onResult: (Boolean) -> Unit = {})

/** One-shot messages (snackbars) from anywhere in the app. */
@Singleton
class Messages @Inject constructor() {
    private val _flow = MutableSharedFlow<Msg>(extraBufferCapacity = 8)
    val flow: SharedFlow<Msg> = _flow.asSharedFlow()
    fun post(text: String) { _flow.tryEmit(Msg(text)) }
    fun post(msg: Msg) { _flow.tryEmit(msg) }
}

const val OFFLINE_HINT =
    "You're off the campus network. Connect to Amrita Wi-Fi or VPN to download, or turn on Demo mode."

/** What is happening to a paper right now, for row indicators. */
sealed interface PaperActivity {
    data object Queued : PaperActivity
    /** [fraction] is -1 while the size is unknown. */
    data class Downloading(val fraction: Float) : PaperActivity
    data class Failed(val reason: String) : PaperActivity
}

/** Download / queue / progress operations shared by every screen. */
@Singleton
class PaperActions @Inject constructor(
    private val downloads: DownloadRepository,
    private val scheduler: DownloadScheduler,
    private val modeProvider: ModeProvider,
    private val prefsState: PrefsState,
    private val progress: DownloadProgress,
    private val db: AppDatabase,
    private val messages: Messages,
) {
    val mode: AppMode get() = modeProvider.mode.value
    val demo: Boolean get() = mode == AppMode.DEMO
    val canDownload: Boolean get() = mode != AppMode.OFFLINE_VAULT

    /** paperId → live activity, combining the queue table with byte-level progress. */
    val activity: Flow<Map<String, PaperActivity>> = combine(db.queue().observeQueue(), progress.map) { rows, prog ->
        val out = HashMap<String, PaperActivity>()
        for (r in rows) {
            when (r.state) {
                "PENDING" -> out[r.paperId] = PaperActivity.Queued
                "DOWNLOADING" -> out[r.paperId] = PaperActivity.Downloading(prog[r.paperId] ?: -1f)
                "FAILED" -> out[r.paperId] = PaperActivity.Failed(r.error ?: "Failed")
            }
        }
        for ((id, f) in prog) out[id] = PaperActivity.Downloading(f)
        out
    }

    /** Downloads a single paper right now (used when opening an unsaved paper). */
    suspend fun downloadNow(paperId: String): DownloadOutcome {
        downloads.localFile(paperId, demo)?.let { return DownloadOutcome.Skipped }
        if (!canDownload) return DownloadOutcome.Failed(OFFLINE_HINT)
        var last: DownloadOutcome = DownloadOutcome.Failed("Not started")
        for (attempt in 1..2) {
            last = downloads.downloadOne(paperId, demo)
            if (last !is DownloadOutcome.Failed || !last.retryable) break
        }
        return last
    }

    /** Queues papers for the background worker with the chosen save mode (remembered as the default). */
    suspend fun queue(paperIds: List<String>, label: String, mode: SaveMode, zipName: String = "") {
        if (paperIds.isEmpty()) return messages.post("Nothing to download for $label")
        prefsState.prefs.setSaveMode(mode)
        if (!canDownload) {
            val missing = paperIds.count { downloads.localFile(it, demo) == null }
            if (missing > 0) return messages.post(OFFLINE_HINT)
        }
        val r = scheduler.enqueue(paperIds, demo, mode, zipName)
        messages.post(
            when {
                !r.scheduled -> "Already downloaded"
                r.queued == 0 && mode == SaveMode.ZIP -> "Creating ZIP…"
                r.queued == 0 -> "Copying to Downloads…"
                r.alreadySaved > 0 -> "Downloading ${r.queued} papers (${r.alreadySaved} already saved)"
                else -> "Downloading ${r.queued} papers"
            },
        )
    }

    /** Single-paper background download using the remembered save mode (ZIP makes no sense for one file). */
    suspend fun queueOne(paperId: String) {
        val mode = prefsState.state.value?.saveMode ?: SaveMode.APP_PRIVATE
        if (!canDownload) return messages.post(OFFLINE_HINT)
        scheduler.enqueue(listOf(paperId), demo, if (mode == SaveMode.ZIP) SaveMode.ALSO_DOWNLOADS else mode, "")
    }

    suspend fun setStatus(paperId: String, status: StudyStatus) = db.catalog().setStatus(paperId, status.name)

    fun say(text: String) = messages.post(text)
}
