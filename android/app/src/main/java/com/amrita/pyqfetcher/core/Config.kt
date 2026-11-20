package com.amrita.pyqfetcher.core

import java.net.URLEncoder

/** Central constants ported from cmd/internal/configs/configs.go. */
object Config {
    const val APP_NAME = "Amrita PYQ Fetcher"

    const val DOMAIN_BASE = "http://dspace.amritanet.edu:8080"
    const val IP_BASE = "http://172.17.18.57:8080"

    const val HANDLE_PREFIX = "/xmlui/handle/123456789/"
    const val EXAM_ROOT = HANDLE_PREFIX + "16"
    const val BTECH_ROOT = HANDLE_PREFIX + "150"

    const val VPN_GUIDE_URL = "https://intranet.cb.amrita.edu/infrastructure/icts/vpn-access"
    const val CMS_URL = "https://cms.cb.amrita.edu"

    const val DEFAULT_BRANCH = "CYS"
    const val SEED_VERSION = 1

    /** Max parallel downloads in the bulk worker (polite to the campus server). */
    const val DOWNLOAD_PARALLELISM = 3

    fun searchPath(query: String): String =
        "/xmlui/discover?scope=%2F&query=${URLEncoder.encode(query, "UTF-8")}&rpp=50"
}

enum class HostMode(val label: String) {
    AUTO("Automatic (domain, then campus IP)"),
    DOMAIN("Domain only"),
    IP("Campus IP only"),
}

/** The two known DSpace origins. Overridable so tests can point at a MockWebServer. */
data class DSpaceHosts(
    val domain: String = Config.DOMAIN_BASE,
    val ip: String = Config.IP_BASE,
)

/** The three ways the app can operate. */
enum class AppMode(val label: String) {
    LIVE("Live campus / VPN"),
    OFFLINE_VAULT("Offline vault"),
    DEMO("Offline demo"),
}
