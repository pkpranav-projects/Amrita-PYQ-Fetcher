package com.amrita.pyqfetcher.data.repo

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.amrita.pyqfetcher.data.db.VaultRow
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Storage layout, sharing (FileProvider), ZIP bundling and MediaStore export. */
@Singleton
class FileStore @Inject constructor(@ApplicationContext private val context: Context) {

    val papersRoot: File get() = File(context.filesDir, "papers")
    val demoRoot: File get() = File(context.filesDir, "papers_demo")
    private val exportDir: File get() = File(context.cacheDir, "exports").also { it.mkdirs() }

    fun fileFor(branch: String, subjectCode: String, year: Int, fileName: String, demo: Boolean): File {
        val root = if (demo) demoRoot else papersRoot
        val yearDir = if (year > 0) year.toString() else "unknown"
        return File(File(root, "${segment(branch)}/${segment(subjectCode)}/$yearDir"), pdfName(fileName))
    }

    fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun shareIntent(file: File, title: String): Intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uriFor(file))
        putExtra(Intent.EXTRA_SUBJECT, title)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }.let { Intent.createChooser(it, "Share question paper").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }

    fun viewIntent(file: File): Intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uriFor(file), "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }.let { Intent.createChooser(it, "Open with").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }

    /** Bundles the vault rows into one ZIP in the cache directory and returns a share chooser. */
    fun zipShareIntent(zipName: String, rows: List<VaultRow>): Intent {
        val zip = makeZip(zipName, rows)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uriFor(zip))
            putExtra(Intent.EXTRA_SUBJECT, zipName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(intent, "Share ZIP").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun makeZip(zipName: String, rows: List<VaultRow>): File =
        zipFiles(zipName, rows.map { r -> "${segment(r.subjectCode)}/${if (r.year > 0) r.year else "unknown"}" to File(r.localPath) })

    /** Writes the given (folder, file) pairs into one ZIP in the cache directory. */
    fun zipFiles(zipName: String, entries: List<Pair<String, File>>): File {
        val name = if (zipName.endsWith(".zip")) zipName else "$zipName.zip"
        val out = File(exportDir, name.replace(Regex("""[\\/:*?"<>|\s]+"""), "_"))
        val used = HashSet<String>()
        ZipOutputStream(out.outputStream().buffered()).use { zos ->
            for ((folder, f) in entries) {
                if (!f.isFile) continue
                var entry = "$folder/${f.name}"
                var n = 1
                while (!used.add(entry)) entry = "$folder/${n++}_${f.name}"
                zos.putNextEntry(ZipEntry(entry))
                f.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        return out
    }

    /**
     * Copies PDFs (or a ZIP) into the public Downloads/AmritaPYQ folder (MediaStore on Android 10+,
     * the app's external Downloads directory on older versions). Returns how many were copied.
     */
    fun exportToDownloads(files: List<File>): Int {
        var copied = 0
        for (f in files) {
            if (!f.isFile) continue
            val ok = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, f.name)
                        put(MediaStore.Downloads.MIME_TYPE, if (f.name.endsWith(".zip")) "application/zip" else "application/pdf")
                        put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/AmritaPYQ")
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("insert failed")
                    resolver.openOutputStream(uri)?.use { out -> f.inputStream().use { it.copyTo(out) } }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                } else {
                    val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "AmritaPYQ")
                    dir.mkdirs()
                    f.copyTo(File(dir, f.name), overwrite = true)
                }
            }.isSuccess
            if (ok) copied++
        }
        return copied
    }

    fun clearExportCache() {
        exportDir.listFiles()?.forEach { it.delete() }
    }

    fun delete(path: String) {
        runCatching { File(path).delete() }
    }

    fun cacheSize(): Long = exportDir.listFiles()?.sumOf { it.length() } ?: 0L

    private fun pdfName(name: String): String {
        val cleaned = name.replace(Regex("""[\\/:*?"<>|]+"""), "_").trim()
        return if (cleaned.lowercase().endsWith(".pdf")) cleaned else "$cleaned.pdf"
    }

    private fun segment(s: String) = s.replace(Regex("""[^A-Za-z0-9._-]+"""), "_")
}
