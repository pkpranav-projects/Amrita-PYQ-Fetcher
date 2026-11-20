package com.amrita.pyqfetcher

import com.amrita.pyqfetcher.core.DSpaceHosts
import com.amrita.pyqfetcher.core.HostMode
import com.amrita.pyqfetcher.data.net.DSpaceClient
import com.amrita.pyqfetcher.data.net.DemoPdfGenerator
import com.amrita.pyqfetcher.data.net.PdfDownloader
import com.amrita.pyqfetcher.data.net.PdfResult
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

class PdfDownloaderTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var server: MockWebServer
    private lateinit var downloader: PdfDownloader

    private val pdfBytes = DemoPdfGenerator.generate("test paper")

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        val http = OkHttpClient.Builder().connectTimeout(2, TimeUnit.SECONDS).readTimeout(2, TimeUnit.SECONDS).build()
        val base = server.url("/").toString().trimEnd('/')
        val client = DSpaceClient(http, DSpaceHosts(domain = base, ip = base)) { HostMode.DOMAIN }
        downloader = PdfDownloader(client, http)
    }

    @After fun tearDown() {
        server.shutdown()
    }

    private fun dest() = File(tmp.root, "out/paper.pdf")

    @Test fun savesRealPdf() = runBlocking {
        server.enqueue(MockResponse().setBody(Buffer().write(pdfBytes)).setHeader("Content-Type", "application/pdf"))
        val r = downloader.download("/xmlui/bitstream/handle/123456789/1/a.pdf", dest())
        assertTrue(r is PdfResult.Ok)
        assertTrue(PdfDownloader.isValidPdf(dest()))
        assertEquals(pdfBytes.size.toLong(), dest().length())
        assertFalse(File(dest().path + ".part").exists())
    }

    @Test fun rejects404AndLeavesNoFile() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("%PDF-1.4 not really"))
        val r = downloader.download("/missing.pdf", dest())
        assertTrue(r is PdfResult.Failed)
        assertTrue((r as PdfResult.Failed).reason.contains("404"))
        assertFalse(dest().exists())
        assertFalse(File(dest().path + ".part").exists())
    }

    @Test fun rejectsHtmlLoginPageServedAs200() = runBlocking {
        server.enqueue(
            MockResponse().setBody("<html><body>Please log in to the captive portal</body></html>")
                .setHeader("Content-Type", "text/html"),
        )
        val r = downloader.download("/xmlui/bitstream/handle/123456789/1/a.pdf", dest())
        assertTrue(r is PdfResult.Failed)
        assertFalse(dest().exists())
        assertFalse(File(dest().path + ".part").exists())
    }

    @Test fun rejectsEmptyBody() = runBlocking {
        server.enqueue(MockResponse().setBody(""))
        assertTrue(downloader.download("/empty.pdf", dest()) is PdfResult.Failed)
        assertFalse(dest().exists())
    }

    @Test fun rejectsAlmostPdfMagic() = runBlocking {
        server.enqueue(MockResponse().setBody("%PDF")) // no trailing '-'
        assertTrue(downloader.download("/short.pdf", dest()) is PdfResult.Failed)
        assertFalse(dest().exists())
    }

    @Test fun isValidPdf_checksMagicBytes() {
        val good = tmp.newFile("good.pdf").apply { writeBytes(pdfBytes) }
        val html = tmp.newFile("bad.pdf").apply { writeText("<html></html>") }
        assertTrue(PdfDownloader.isValidPdf(good))
        assertFalse(PdfDownloader.isValidPdf(html))
        assertFalse(PdfDownloader.isValidPdf(File(tmp.root, "nope.pdf")))
    }

    @Test fun clientFallsBackFromDeadDomainToIp() = runBlocking {
        val http = OkHttpClient.Builder().connectTimeout(1, TimeUnit.SECONDS).build()
        val dead = "http://127.0.0.1:1" // nothing listens here
        val live = server.url("/").toString().trimEnd('/')
        val client = DSpaceClient(http, DSpaceHosts(domain = dead, ip = live)) { HostMode.AUTO }
        server.enqueue(MockResponse().setBody("hello"))
        val body = client.fetchHtml("/xmlui/handle/123456789/16")
        assertEquals("hello", body)
        assertEquals(live, client.lastGoodBase)
        assertEquals("/xmlui/handle/123456789/16", server.takeRequest().path)
    }

    @Test fun probeReturnsNullWhenUnreachable() = runBlocking {
        val http = OkHttpClient.Builder().connectTimeout(1, TimeUnit.SECONDS).build()
        val client = DSpaceClient(http, DSpaceHosts("http://127.0.0.1:1", "http://127.0.0.1:2")) { HostMode.AUTO }
        assertNull(client.probe())
    }

    @Test fun hostModeIpOnlyUsesIp() = runBlocking {
        val live = server.url("/").toString().trimEnd('/')
        val client = DSpaceClient(OkHttpClient(), DSpaceHosts(domain = "http://127.0.0.1:1", ip = live)) { HostMode.IP }
        server.enqueue(MockResponse().setBody("ok"))
        assertEquals("ok", client.fetchHtml("/x"))
    }

    @Test fun toRelativeStripsKnownHosts() {
        val client = DSpaceClient(OkHttpClient(), DSpaceHosts()) { HostMode.AUTO }
        assertEquals("/xmlui/a.pdf", client.toRelative("http://dspace.amritanet.edu:8080/xmlui/a.pdf"))
        assertEquals("/xmlui/a.pdf", client.toRelative("http://172.17.18.57:8080/xmlui/a.pdf"))
        assertEquals("/xmlui/a.pdf", client.toRelative("/xmlui/a.pdf"))
    }
}
