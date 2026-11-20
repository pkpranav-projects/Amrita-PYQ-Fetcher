package com.amrita.pyqfetcher

import com.amrita.pyqfetcher.core.DSpaceHosts
import com.amrita.pyqfetcher.core.HostMode
import com.amrita.pyqfetcher.data.net.DSpaceClient
import com.amrita.pyqfetcher.data.net.PdfDownloader
import com.amrita.pyqfetcher.data.net.PdfResult
import com.amrita.pyqfetcher.data.prefs.SaveMode
import com.amrita.pyqfetcher.work.JobSpec
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DownloadModesTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun jobSpecRoundTripsEveryMode() {
        for (m in SaveMode.entries) {
            val spec = JobSpec("abc-123", m, "24CYS201_Past_Papers")
            assertEquals(spec, JobSpec.decode(spec.encode()))
        }
    }

    @Test fun jobSpecSurvivesTildeInZipName() {
        val decoded = JobSpec.decode(JobSpec("id", SaveMode.ZIP, "a~b").encode())
        assertEquals(SaveMode.ZIP, decoded.mode)
        assertEquals("a_b", decoded.zipName)
    }

    @Test fun legacyJobIdsDecodeAsAppOnly() {
        val decoded = JobSpec.decode("3f1c2b9a-plain-uuid")
        assertEquals(SaveMode.APP_PRIVATE, decoded.mode)
        assertEquals("", decoded.zipName)
    }

    private fun downloaderFor(server: MockWebServer): PdfDownloader {
        val http = OkHttpClient()
        val base = server.url("/").toString().trimEnd('/')
        return PdfDownloader(DSpaceClient(http, DSpaceHosts(base, base)) { HostMode.DOMAIN }, http)
    }

    @Test fun serverErrorsAreRetryableButNotFoundIsNot() = runBlocking {
        val server = MockWebServer().also { it.start() }
        try {
            val d = downloaderFor(server)
            server.enqueue(MockResponse().setResponseCode(503))
            val busy = d.download("/a.pdf", File(tmp.root, "a.pdf")) as PdfResult.Failed
            assertTrue(busy.retryable)

            server.enqueue(MockResponse().setResponseCode(404))
            val missing = d.download("/b.pdf", File(tmp.root, "b.pdf")) as PdfResult.Failed
            assertFalse(missing.retryable)

            server.enqueue(MockResponse().setBody("<html>login</html>"))
            val html = d.download("/c.pdf", File(tmp.root, "c.pdf")) as PdfResult.Failed
            assertFalse(html.retryable)
        } finally {
            server.shutdown()
        }
    }

    @Test fun unreachableServerIsRetryable() = runBlocking {
        val http = OkHttpClient()
        val d = PdfDownloader(DSpaceClient(http, DSpaceHosts("http://127.0.0.1:1", "http://127.0.0.1:1")) { HostMode.DOMAIN }, http)
        val r = d.download("/x.pdf", File(tmp.root, "x.pdf")) as PdfResult.Failed
        assertTrue(r.retryable)
    }
}
