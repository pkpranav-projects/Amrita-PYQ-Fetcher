package com.amrita.pyqfetcher.data.net

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLDecoder

data class Resource(val name: String, val path: String)

/** Everything useful on one DSpace XMLUI page: child communities/collections/items and PDF files. */
data class PageContent(val title: String, val children: List<Resource>, val files: List<Resource>)

class ScrapeException(message: String) : Exception(message)

/**
 * Jsoup port of cmd/internal/requestclient/requestclient.go. Every function is pure
 * (HTML in, resources out) so it can be unit-tested with fixtures.
 */
object DSpaceScraper {
    private const val COMMUNITY_VIEW = "aspect_artifactbrowser_CommunityViewer_div_community-view"
    private const val DRI_NS = "http://di.tamu.edu/DRI/1.0/"
    private val HANDLE_PAGE = Regex("""^(https?://[^/]+)?/xmlui/handle/\d+/\d+$""")

    /** The numeric handle of a page path (higher = uploaded later), or -1. */
    fun handleNumber(path: String): Long =
        Regex("""/handle/\d+/(\d+)""").find(path)?.groupValues?.get(1)?.toLongOrNull() ?: -1L

    private fun parse(html: String): Document = Jsoup.parse(html)

    private fun communityView(doc: Document): Element? = doc.getElementById(COMMUNITY_VIEW)

    private fun linkResource(a: Element, nameOverride: String? = null): Resource {
        val spanText = a.selectFirst("span")?.text()?.trim().orEmpty()
        val name = nameOverride?.takeIf { it.isNotBlank() }
            ?: spanText.ifBlank { a.text().trim() }
        return Resource(name, a.attr("href"))
    }

    /** Courses / communities: `div.artifact-title` → span text + a[href]. */
    fun parseCourses(html: String): List<Resource> {
        val doc = parse(html)
        val view = communityView(doc) ?: return emptyList()
        return view.select("div.artifact-title").mapNotNull { item ->
            val a = item.selectFirst("a[href]") ?: return@mapNotNull null
            Resource(item.selectFirst("span")?.text()?.trim().orEmpty(), a.attr("href"))
        }
    }

    /** Semesters: first `ul` of the community view, `li > a[href]` with span text. */
    fun parseSemesters(html: String): List<Resource> {
        val view = communityView(parse(html)) ?: throw ScrapeException("no semesters found on the page")
        val ul = view.selectFirst("ul") ?: throw ScrapeException("no semesters found on the page")
        val items = ul.select("li").mapNotNull { it.selectFirst("a[href]")?.let(::linkResource) }
        if (items.isEmpty()) throw ScrapeException("no semesters found on the page")
        return items
    }

    /** Assessments: like semesters, but combines the first two `ul` lists when both exist. */
    fun parseAssessments(html: String): List<Resource> {
        val view = communityView(parse(html)) ?: throw ScrapeException("no assessments found on the page")
        val lists = view.select("ul")
        if (lists.isEmpty()) throw ScrapeException("no assessments found on the page")
        val li = if (lists.size > 1) lists[0].select("li") + lists[1].select("li") else lists[0].select("li")
        return li.mapNotNull { it.selectFirst("a[href]")?.let(::linkResource) }
    }

    /** Sub-communities: `div[xmlns=DRI]` → `ul li a[href]`, de-duplicated by href, document order. */
    fun parseSubCommunities(html: String): List<Resource> {
        val doc = parse(html)
        val containers = doc.select("div[xmlns=\"$DRI_NS\"]")
        val out = LinkedHashMap<String, Resource>()
        for (container in containers) {
            for (li in container.select("ul li")) {
                val a = li.selectFirst("a[href]") ?: continue
                out.putIfAbsent(a.attr("href"), Resource(a.text().trim(), a.attr("href")))
            }
        }
        return out.values.toList()
    }

    /** Exam paper files: `div.file-list` → `div.file-wrapper`; name from `span[title]`, path from `a[href]`. */
    fun parseFiles(html: String): List<Resource> {
        val doc = parse(html)
        val out = ArrayList<Resource>()
        for (wrapper in doc.select("div.file-list div.file-wrapper")) {
            val href = wrapper.selectFirst("a[href]")?.attr("href").orEmpty()
            if (href.isBlank()) continue
            out.add(Resource(fileNameOf(wrapper, href), href))
        }
        return out
    }

    private fun fileNameOf(wrapper: Element, href: String): String {
        // Go parity: second div → first inner div → second span's title.
        val divs = wrapper.select("div").filter { it !== wrapper }
        val spans = divs.getOrNull(1)?.select("span")
        spans?.getOrNull(1)?.attr("title")?.takeIf { it.isNotBlank() }?.let { return it }
        wrapper.select("span[title]").map { it.attr("title") }
            .lastOrNull { it.isNotBlank() && it.contains('.') }?.let { return it }
        wrapper.select("span[title]").map { it.attr("title") }.lastOrNull { it.isNotBlank() }?.let { return it }
        return fileNameFromPath(href)
    }

    /** Discovery search results: `div.artifact-description a`, with title-link fallbacks. */
    fun parseSearchResults(html: String): List<Resource> {
        val doc = parse(html)
        val out = LinkedHashMap<String, Resource>()
        fun collect(selector: String) {
            for (a in doc.select(selector)) {
                val href = a.attr("href")
                if (href.isBlank() || href.startsWith("#")) continue
                val text = a.text().trim()
                if (text.isNotBlank()) out.putIfAbsent(href, Resource(text, href))
            }
        }
        collect("div.artifact-description a")
        if (out.isEmpty()) collect("div.artifact-title a")
        if (out.isEmpty()) collect("h4 a[href*=/handle/]")
        return out.values.toList()
    }

    /** Generic page: every community/collection/item link plus PDF files. Used by the live browser and syncer. */
    fun parsePage(html: String): PageContent {
        val doc = parse(html)
        val title = doc.selectFirst("h2.ds-div-head, h1")?.text()?.trim().orEmpty()
            .ifBlank { doc.title().trim() }
        val children = LinkedHashMap<String, Resource>()
        // Only real community / collection / item pages; the sidebar's browse and filter
        // links (".../browse?type=author", ".../discover?filter=...") are not folders.
        fun add(r: Resource) {
            val path = r.path.trimEnd('/')
            if (HANDLE_PAGE.matches(path)) children.putIfAbsent(path, r.copy(path = path))
        }
        communityView(doc)?.let { view ->
            view.select("div.artifact-title").forEach { item ->
                item.selectFirst("a[href]")?.let { a ->
                    add(Resource(item.selectFirst("span")?.text()?.trim().orEmpty().ifBlank { a.text().trim() }, a.attr("href")))
                }
            }
            view.select("ul li").forEach { li -> li.selectFirst("a[href]")?.let { add(linkResource(it)) } }
        }
        doc.select("div[xmlns=\"$DRI_NS\"] ul li").forEach { li ->
            li.selectFirst("a[href]")?.let { a -> add(Resource(a.text().trim(), a.attr("href"))) }
        }
        doc.select("div.artifact-description a, div.artifact-title a").forEach { a ->
            add(Resource(a.text().trim(), a.attr("href")))
        }
        return PageContent(title, children.values.filter { it.name.isNotBlank() }, parseFiles(html))
    }

    fun fileNameFromPath(path: String): String {
        val last = path.substringBefore('?').substringAfterLast('/')
        return runCatching { URLDecoder.decode(last.replace("+", "%2B"), "UTF-8") }.getOrDefault(last)
    }
}
