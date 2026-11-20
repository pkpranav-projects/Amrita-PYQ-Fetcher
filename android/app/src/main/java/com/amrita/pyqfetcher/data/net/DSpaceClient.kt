package com.amrita.pyqfetcher.data.net

import com.amrita.pyqfetcher.core.DSpaceHosts
import com.amrita.pyqfetcher.core.HostMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DSpaceException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Talks to the Amrita DSpace server. Tries the preferred origin first and, in AUTO mode,
 * falls back between the domain and the direct campus IP when DNS/connection fails.
 */
class DSpaceClient(
    private val http: OkHttpClient,
    private val hosts: DSpaceHosts = DSpaceHosts(),
    private val hostMode: () -> HostMode = { HostMode.AUTO },
) {
    @Volatile
    var lastGoodBase: String? = null
        private set

    private val probeClient: OkHttpClient by lazy {
        http.newBuilder()
            .callTimeout(2500, TimeUnit.MILLISECONDS)
            .connectTimeout(2000, TimeUnit.MILLISECONDS)
            .readTimeout(2500, TimeUnit.MILLISECONDS)
            .build()
    }

    fun candidates(): List<String> = when (hostMode()) {
        HostMode.DOMAIN -> listOf(hosts.domain)
        HostMode.IP -> listOf(hosts.ip)
        HostMode.AUTO -> listOfNotNull(lastGoodBase, hosts.domain, hosts.ip).distinct()
    }

    /** Returns the first reachable origin, or null when the repository cannot be reached. */
    suspend fun probe(): String? {
        for (base in candidates()) {
            try {
                val req = Request.Builder().url(base + "/").get().build()
                probeClient.newCall(req).await().use { resp ->
                    if (resp.isSuccessful) {
                        lastGoodBase = base
                        return base
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // try next candidate
            }
        }
        lastGoodBase = null
        return null
    }

    /** Converts an absolute URL pointing at a known host into a host-relative path. */
    fun toRelative(pathOrUrl: String): String {
        for (h in listOf(hosts.domain, hosts.ip)) {
            if (pathOrUrl.startsWith(h)) return pathOrUrl.removePrefix(h).ifEmpty { "/" }
        }
        return pathOrUrl
    }

    /** Executes a GET. The caller must close the returned response. */
    suspend fun get(pathOrUrl: String, client: OkHttpClient = http): Response {
        val rel = toRelative(pathOrUrl)
        if (rel.startsWith("http://") || rel.startsWith("https://")) {
            return client.newCall(Request.Builder().url(rel).get().build()).await()
        }
        val path = if (rel.startsWith("/")) rel else "/$rel"
        var last: Exception? = null
        for (base in candidates()) {
            try {
                val resp = client.newCall(Request.Builder().url(base + path).get().build()).await()
                lastGoodBase = base
                return resp
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                last = e
            } catch (e: IllegalArgumentException) {
                throw DSpaceException("Invalid address: $path", e)
            }
        }
        throw DSpaceException(
            "Could not reach the Amrita DSpace server. Connect to Amrita campus Wi-Fi or the Amrita VPN.",
            last,
        )
    }

    /** GET and return the HTML body, enforcing HTTP 200. */
    suspend fun fetchHtml(pathOrUrl: String): String = withContext(Dispatchers.IO) {
        get(pathOrUrl).use { resp ->
            if (resp.code != 200) throw DSpaceException("Unexpected HTTP status: ${resp.code}")
            resp.body?.string() ?: throw DSpaceException("Empty response from server")
        }
    }

    fun absoluteUrl(pathOrUrl: String): String {
        val rel = toRelative(pathOrUrl)
        if (rel.startsWith("http://") || rel.startsWith("https://")) return rel
        val base = lastGoodBase ?: candidates().first()
        return base + (if (rel.startsWith("/")) rel else "/$rel")
    }
}

/** Suspending bridge for OkHttp's async API that cancels the call with the coroutine. */
suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            if (cont.isCancelled) response.close() else cont.resume(response)
        }

        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}
