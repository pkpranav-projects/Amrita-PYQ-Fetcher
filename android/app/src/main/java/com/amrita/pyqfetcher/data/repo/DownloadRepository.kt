package com.amrita.pyqfetcher.data.repo

import com.amrita.pyqfetcher.data.db.AppDatabase
import com.amrita.pyqfetcher.data.db.DownloadedPaperEntity
import com.amrita.pyqfetcher.data.db.PaperRow
import com.amrita.pyqfetcher.data.net.DemoPdfGenerator
import com.amrita.pyqfetcher.data.net.PdfDownloader
import com.amrita.pyqfetcher.data.net.PdfResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DownloadOutcome {
    data class Saved(val file: File, val bytes: Long) : DownloadOutcome
    data object Skipped : DownloadOutcome
    data class Failed(val reason: String, val retryable: Boolean = false) : DownloadOutcome
}

data class VerifyReport(val checked: Int, val removed: Int)

/** Live byte-level progress of in-flight downloads (paperId → 0..1, or -1 when the size is unknown). */
@Singleton
class DownloadProgress @Inject constructor() {
    private val _map = MutableStateFlow<Map<String, Float>>(emptyMap())
    val map: StateFlow<Map<String, Float>> = _map.asStateFlow()

    fun set(paperId: String, written: Long, total: Long) {
        val f = if (total > 0) (written.toFloat() / total).coerceIn(0f, 1f) else -1f
        _map.update { it + (paperId to f) }
    }

    fun clear(paperId: String) = _map.update { it - paperId }
}

@Singleton
class DownloadRepository @Inject constructor(
    private val db: AppDatabase,
    private val downloader: PdfDownloader,
    private val files: FileStore,
    private val progress: DownloadProgress,
) {
    private val catalog get() = db.catalog()
    private val dl get() = db.downloads()

    fun fileFor(row: PaperRow, demo: Boolean): File =
        files.fileFor(row.branchCode, row.subjectCode, row.paper.year, row.paper.fileName, demo)

    /** The local copy usable right now (valid PDF), or null. Demo copies only count in demo mode. */
    suspend fun localFile(paperId: String, demoMode: Boolean): File? {
        val rec = dl.get(paperId) ?: return null
        val f = File(rec.localPath)
        return if (PdfDownloader.isValidPdf(f) && (!rec.isDemo || demoMode)) f else null
    }

    /**
     * Downloads one paper. Already-saved valid PDFs are skipped (smart de-duplication).
     * In demo mode a valid demo PDF is generated instead of contacting the server.
     */
    suspend fun downloadOne(paperId: String, demo: Boolean, force: Boolean = false): DownloadOutcome {
        val row = catalog.paperRow(paperId) ?: return DownloadOutcome.Failed("Paper not found in catalog")
        val existing = dl.get(paperId)
        if (existing != null && !force) {
            val f = File(existing.localPath)
            if (PdfDownloader.isValidPdf(f) && (!existing.isDemo || demo)) return DownloadOutcome.Skipped
        }

        val dest = fileFor(row, demo)
        if (demo) {
            return withContext(Dispatchers.IO) {
                try {
                    dest.parentFile?.mkdirs()
                    val bytes = DemoPdfGenerator.generate("${row.subjectCode} - ${row.paper.title}")
                    dest.writeBytes(bytes)
                    record(paperId, dest, bytes.size.toLong(), isDemo = true)
                    DownloadOutcome.Saved(dest, bytes.size.toLong())
                } catch (e: java.io.IOException) {
                    dest.delete()
                    DownloadOutcome.Failed(e.message ?: "Could not write demo PDF")
                }
            }
        }

        progress.set(paperId, 0, 0)
        try {
            return when (val r = downloader.download(row.paper.path, dest) { w, t -> progress.set(paperId, w, t) }) {
                is PdfResult.Failed -> DownloadOutcome.Failed(r.reason, r.retryable)
                is PdfResult.Ok -> {
                    // A real copy supersedes any demo placeholder.
                    if (existing?.isDemo == true && existing.localPath != dest.path) files.delete(existing.localPath)
                    record(paperId, r.file, r.bytes, isDemo = false)
                    DownloadOutcome.Saved(r.file, r.bytes)
                }
            }
        } finally {
            progress.clear(paperId)
        }
    }

    private suspend fun record(paperId: String, file: File, bytes: Long, isDemo: Boolean) {
        dl.upsert(DownloadedPaperEntity(paperId, file.path, bytes, System.currentTimeMillis(), isDemo))
    }

    suspend fun delete(paperIds: List<String>) = withContext(Dispatchers.IO) {
        for (chunk in paperIds.chunked(500)) {
            val recs = chunk.mapNotNull { dl.get(it) }
            recs.forEach { files.delete(it.localPath) }
            dl.delete(chunk)
        }
    }

    /** Removes every demo-mode PDF and the temporary export cache. Returns freed bytes. */
    suspend fun cleanDemoAndCache(): Long = withContext(Dispatchers.IO) {
        val demo = dl.all().filter { it.isDemo }
        val freed = demo.sumOf { it.sizeBytes } + files.cacheSize()
        demo.forEach { files.delete(it.localPath) }
        dl.delete(demo.map { it.paperId })
        files.demoRoot.deleteRecursively()
        files.clearExportCache()
        freed
    }

    /** Re-checks every saved file: missing/corrupt PDFs are removed so they can be re-downloaded. */
    suspend fun verifyAll(): VerifyReport = withContext(Dispatchers.IO) {
        val all = dl.all()
        val bad = all.filter { !PdfDownloader.isValidPdf(File(it.localPath)) }
        bad.forEach { files.delete(it.localPath) }
        dl.delete(bad.map { it.paperId })
        VerifyReport(all.size, bad.size)
    }
}
