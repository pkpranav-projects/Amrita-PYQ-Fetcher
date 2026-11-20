package com.amrita.pyqfetcher.ui.reader

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Thin, thread-safe wrapper around the platform [PdfRenderer] (which only allows one open page
 * at a time) with an in-memory bitmap cache.
 */
class PdfDoc private constructor(
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    /** width / height of every page, read once up-front so list items have stable heights. */
    val aspects: List<Float>,
) : AutoCloseable {
    private val mutex = Mutex()
    val pageCount: Int get() = aspects.size

    private val cache = object : LruCache<String, Bitmap>(64 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    suspend fun render(index: Int, width: Int): Bitmap {
        val key = "$index@$width"
        cache.get(key)?.let { return it }
        return mutex.withLock {
            cache.get(key) ?: withContext(Dispatchers.IO) {
                renderer.openPage(index).use { page ->
                    val height = (width * page.height.toFloat() / page.width).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }.also { cache.put(key, it) }
        }
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { pfd.close() }
        cache.evictAll()
    }

    companion object {
        suspend fun open(file: File): PdfDoc = withContext(Dispatchers.IO) {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                val renderer = PdfRenderer(pfd)
                val aspects = (0 until renderer.pageCount).map { i ->
                    renderer.openPage(i).use { it.width.toFloat() / it.height.toFloat() }
                }
                PdfDoc(pfd, renderer, aspects)
            } catch (e: Exception) {
                runCatching { pfd.close() }
                throw e
            }
        }
    }
}
