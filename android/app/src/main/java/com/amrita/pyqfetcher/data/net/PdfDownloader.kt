package com.amrita.pyqfetcher.data.net

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okio.buffer
import okio.sink
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed interface PdfResult {
    data class Ok(val file: File, val bytes: Long) : PdfResult
    data class Failed(val reason: String, val retryable: Boolean = false) : PdfResult
}

/**
 * Downloads a PDF with the integrity rules from webclient.go:
 *  1. the response code MUST be 200,
 *  2. the first bytes MUST be the `%PDF-` magic,
 *  3. nothing is written to the final path until both checks pass, and any partial
 *     file is deleted on failure — HTML/login/404 pages are never saved as .pdf.
 */
class PdfDownloader(
    private val client: DSpaceClient,
    private val http: OkHttpClient,
) {
    private val downloadClient: OkHttpClient by lazy {
        http.newBuilder()
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    suspend fun download(
        pathOrUrl: String,
        dest: File,
        onProgress: (written: Long, total: Long) -> Unit = { _, _ -> },
    ): PdfResult = withContext(Dispatchers.IO) { downloadOnIo(pathOrUrl, dest, onProgress) }

    // Body reads block, so everything after the request runs on the IO dispatcher.
    private suspend fun downloadOnIo(
        pathOrUrl: String,
        dest: File,
        onProgress: (written: Long, total: Long) -> Unit,
    ): PdfResult {
        val response = try {
            client.get(pathOrUrl, downloadClient)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            return PdfResult.Failed(e.message ?: "Network error", retryable = true)
        }

        response.use { resp ->
            if (resp.code != 200) return PdfResult.Failed("Server returned HTTP ${resp.code}", retryable = resp.code >= 500 || resp.code == 408 || resp.code == 429)
            val body = resp.body ?: return PdfResult.Failed("Empty response from server")
            val source = body.source()

            val hasHeader = try {
                source.request(MAGIC.size.toLong())
            } catch (e: IOException) {
                return PdfResult.Failed(e.message ?: "Network error", retryable = true)
            }
            if (!hasHeader) return PdfResult.Failed("Server response was empty, not a PDF")
            val head = source.peek().readByteArray(MAGIC.size.toLong())
            if (!head.contentEquals(MAGIC)) {
                val type = body.contentType()?.toString() ?: "unknown type"
                return PdfResult.Failed(
                    "Server did not return a PDF ($type). You may be off the campus network or behind a login page.",
                )
            }

            dest.parentFile?.mkdirs()
            val tmp = File(dest.path + ".part")
            val total = body.contentLength()
            var written = 0L
            try {
                tmp.sink().buffer().use { sink ->
                    val buf = okio.Buffer()
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = source.read(buf, 64 * 1024)
                        if (n == -1L) break
                        sink.write(buf, n)
                        written += n
                        onProgress(written, total)
                    }
                }
                if (total > 0 && written != total) {
                    tmp.delete()
                    return PdfResult.Failed("Download was incomplete ($written of $total bytes)", retryable = true)
                }
                if (dest.exists()) dest.delete()
                if (!tmp.renameTo(dest)) {
                    tmp.delete()
                    return PdfResult.Failed("Could not save the file")
                }
                return PdfResult.Ok(dest, written)
            } catch (e: CancellationException) {
                tmp.delete()
                throw e
            } catch (e: IOException) {
                tmp.delete()
                return PdfResult.Failed(e.message ?: "Connection dropped", retryable = true)
            }
        }
    }

    companion object {
        val MAGIC = "%PDF-".toByteArray(Charsets.US_ASCII)

        /** True if the file exists, is non-empty and starts with `%PDF-`. */
        fun isValidPdf(file: File): Boolean {
            if (!file.isFile || file.length() < MAGIC.size) return false
            return try {
                file.inputStream().use { input ->
                    val head = ByteArray(MAGIC.size)
                    input.read(head) == MAGIC.size && head.contentEquals(MAGIC)
                }
            } catch (_: IOException) {
                false
            }
        }
    }
}
