package com.amrita.pyqfetcher.data.repo

import com.amrita.pyqfetcher.core.Config
import com.amrita.pyqfetcher.data.db.AppDatabase
import com.amrita.pyqfetcher.data.db.PaperEntity
import com.amrita.pyqfetcher.data.db.SubjectEntity
import com.amrita.pyqfetcher.data.net.DSpaceClient
import com.amrita.pyqfetcher.data.net.DSpaceScraper
import com.amrita.pyqfetcher.data.net.PageContent
import com.amrita.pyqfetcher.data.net.Resource
import com.amrita.pyqfetcher.data.prefs.UserPrefs
import com.amrita.pyqfetcher.domain.Ids
import com.amrita.pyqfetcher.domain.Names
import com.amrita.pyqfetcher.domain.PaperKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.util.PriorityQueue
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

data class SyncProgress(val pages: Int, val files: Int, val added: Int)

data class SyncResult(
    val pagesVisited: Int,
    val pdfFiles: Int,
    val newPapers: Int,
    val unclassified: Int,
    val errors: Int,
)

/** Live DSpace browsing, discovery search and the "find new papers" crawler. */
@Singleton
class LiveRepository @Inject constructor(
    private val client: DSpaceClient,
    private val db: AppDatabase,
    private val prefs: UserPrefs,
) {
    suspend fun browse(path: String, demo: Boolean): PageContent {
        val html = if (demo) DemoDSpace.html(path) else client.fetchHtml(path)
        return DSpaceScraper.parsePage(html)
    }

    suspend fun search(query: String, demo: Boolean): List<Resource> {
        val html = if (demo) DemoDSpace.searchHtml(query) else client.fetchHtml(Config.searchPath(query))
        return DSpaceScraper.parseSearchResults(html)
    }

    /** Fetches an item/collection page and returns its PDF files. */
    suspend fun filesOf(path: String, demo: Boolean): List<Resource> = browse(path, demo).files

    /**
     * Crawls the programme's community on DSpace and indexes every PDF it finds into Room.
     *
     * DSpace handle numbers grow over time, so pages are visited newest-first. Exam-session
     * pages already indexed by an earlier run are skipped, which lets every run reach further
     * back while the newest sessions are always checked first.
     */
    suspend fun sync(branch: String, onProgress: (SyncProgress) -> Unit): SyncResult {
        val root = rootFor(branch)
        val alreadySynced = prefs.syncedPages()
        val queue = PriorityQueue<Pair<String, Int>>(compareByDescending { DSpaceScraper.handleNumber(it.first) })
        val visited = HashSet<String>()
        queue.add(root to 0)
        visited.add(root)
        val newlySynced = ArrayList<String>()

        var pages = 0
        var pdfs = 0
        var added = 0
        var unclassified = 0
        var errors = 0

        while (queue.isNotEmpty() && pages < MAX_PAGES) {
            coroutineContext.ensureActive()
            val (path, depth) = queue.poll() ?: break
            val content = try {
                browse(path, demo = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errors++
                if (pages == 0) throw e // root unreachable → surface the real error
                continue
            }
            pages++

            if (content.files.isNotEmpty()) {
                val stats = indexFiles(content.files, Names.semesterFromTitle(content.title))
                pdfs += stats.pdfs
                added += stats.added
                unclassified += stats.unclassified
                newlySynced.add(path)
            }

            if (depth < MAX_DEPTH) {
                for (child in content.children) {
                    if (child.path in alreadySynced) continue
                    if (visited.add(child.path)) queue.add(child.path to depth + 1)
                }
            }
            onProgress(SyncProgress(pages, pdfs, added))
            delay(120)
        }
        prefs.addSyncedPages(newlySynced)
        return SyncResult(pages, pdfs, added, unclassified, errors)
    }

    private fun rootFor(branch: String) = when (branch) {
        "MCA" -> Config.HANDLE_PREFIX + "331"
        "MTECH" -> Config.HANDLE_PREFIX + "279"
        else -> Config.BTECH_ROOT
    }

    data class IndexStats(val pdfs: Int, val added: Int, val unclassified: Int)

    /**
     * Maps scraped files onto subjects (by current or legacy code) and stores new papers.
     * Files whose code isn't a known subject or branch (e.g. another programme's common
     * courses) are counted as unclassified rather than guessed, to keep lists clean.
     */
    suspend fun indexFiles(files: List<Resource>, fallbackSemester: Int = 0): IndexStats {
        val pdfFiles = files.filter { it.path.contains("/bitstream/") && it.path.substringBefore('?').lowercase().endsWith(".pdf") }
        if (pdfFiles.isEmpty()) return IndexStats(0, 0, 0)

        val catalog = db.catalog()
        val subjects = catalog.allSubjects()
        val byCode = HashMap<String, SubjectEntity>()
        for (s in subjects) {
            byCode.putIfAbsent(s.code.uppercase(), s)
            s.legacyCodes.split(',').filter { it.isNotBlank() }.forEach { byCode.putIfAbsent(it.uppercase(), s) }
        }

        val now = System.currentTimeMillis()
        val newSubjects = LinkedHashMap<String, SubjectEntity>()
        val papers = ArrayList<PaperEntity>()
        var unclassified = 0
        for (f in pdfFiles) {
            val path = f.path.substringBefore('?')
            val fileName = DSpaceScraper.fileNameFromPath(path)
            val code = Names.courseCode(fileName)
            if (code == null) { unclassified++; continue }
            var subject = byCode[code] ?: newSubjects.values.firstOrNull { it.code == code }
            if (subject == null) {
                val branch = Names.branchOf(code)
                if (branch == null) { unclassified++; continue }
                val sem = Names.semester(fileName).takeIf { it > 0 } ?: fallbackSemester
                subject = SubjectEntity(
                    id = Ids.subjectId(branch, code), branchCode = branch, code = code, name = code,
                    semester = sem, legacyCodes = "", isLive = true,
                )
                newSubjects[subject.id] = subject
            }
            val year = Names.year(fileName)
            val kind = PaperKind.classify("", "", fileName)
            papers.add(
                PaperEntity(
                    id = Ids.paperId(subject.id, path),
                    subjectId = subject.id,
                    title = "${if (year > 0) "$year - " else ""}${kind.label} Examination (${fileName.removeSuffix(".pdf")})",
                    path = path,
                    fileName = fileName,
                    year = year,
                    kind = kind.name,
                    exam = kind.label,
                    sourceCode = code,
                    isLive = true,
                    addedAt = now,
                ),
            )
        }
        if (newSubjects.isNotEmpty()) catalog.insertSubjects(newSubjects.values.toList())
        val inserted = if (papers.isEmpty()) emptyList() else catalog.insertPapers(papers)
        return IndexStats(pdfFiles.size, inserted.count { it != -1L }, unclassified)
    }

    companion object {
        const val MAX_PAGES = 120
        const val MAX_DEPTH = 4
    }
}

/**
 * Clearly-labelled sample pages for Offline Demo Mode (port of getDemoHTML in webclient.go).
 * Uses fake handle numbers above 990000; these never reach the catalog.
 */
object DemoDSpace {
    private const val VIEW = "aspect_artifactbrowser_CommunityViewer_div_community-view"
    private const val P = Config.HANDLE_PREFIX

    fun html(path: String): String {
        val n = DSpaceScraper.handleNumber(path)
        return when {
            n == 16L -> """<html><body><div id="$VIEW">
                <div class="artifact-title"><span>[Demo] B.Tech</span><a href="${P}990001"></a></div>
                <div class="artifact-title"><span>[Demo] MCA</span><a href="${P}990002"></a></div>
                </div></body></html>"""
            n in 990001L..990009L -> """<html><body><div id="$VIEW"><ul>
                ${(1..8).joinToString("") { """<li><a href="${P}9901$it"><span>[Demo] Semester $it</span></a></li>""" }}
                </ul></div></body></html>"""
            n in 990100L..990199L -> """<html><body><div id="$VIEW"><ul>
                <li><a href="${P}990201"><span>[Demo] Mid-Term 2025</span></a></li>
                <li><a href="${P}990202"><span>[Demo] End Semester 2025 Regular</span></a></li>
                </ul></div></body></html>"""
            else -> """<html><body><h2 class="ds-div-head">[Demo] Exam session</h2><div class="file-list">
                <div class="file-wrapper"><div><a href="/xmlui/bitstream/handle/123456789/990201/Demo_Sample_Paper.pdf"></a></div>
                <div><div><span title="Name:"></span><span title="Demo_Sample_Paper.pdf"></span></div></div></div></div></body></html>"""
        }
    }

    fun searchHtml(query: String): String = """<html><body>
        <div class="artifact-description"><a href="${P}990201">[Demo] Sample result for “${query.replace("<", "")}”</a></div>
        </body></html>"""
}
