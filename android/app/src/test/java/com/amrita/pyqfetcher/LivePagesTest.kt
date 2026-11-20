package com.amrita.pyqfetcher

import com.amrita.pyqfetcher.data.net.DSpaceScraper
import com.amrita.pyqfetcher.domain.Names
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Runs the scraper against real pages saved from dspace.amritanet.edu:8080 (Nov 2026),
 * so markup changes on the live server are caught by a failing test.
 */
class LivePagesTest {
    private fun page(name: String) = File("src/test/resources/live/$name").readText(Charsets.UTF_8)
    private val handle = Regex("""^/xmlui/handle/123456789/\d+$""")

    @Test fun examRootListsProgrammes() {
        val p = DSpaceScraper.parsePage(page("root16.html"))
        val names = p.children.map { it.name }
        assertTrue(names.toString(), "B.Tech" in names && "MCA" in names && "M.Tech" in names)
        assertTrue("bad links: ${p.children.filterNot { handle.matches(it.path) }}", p.children.all { handle.matches(it.path) })
        assertTrue(p.files.isEmpty())
    }

    @Test fun btechListsSemestersAndRecentSessions() {
        val p = DSpaceScraper.parsePage(page("btech150.html"))
        val names = p.children.map { it.name }
        for (n in 1..8) assertTrue(names.toString(), names.any { it.startsWith("$n") && it.contains("Semester") })
        assertTrue(names.contains("First Semester - 2025 Regular"))
        assertTrue("bad links: ${p.children.filterNot { handle.matches(it.path) }}", p.children.all { handle.matches(it.path) })
        assertTrue("no link back up to the exam root", p.children.none { it.path.endsWith("/16") || it.path.endsWith("/150") })
    }

    @Test fun semesterListsYearsAndSessions() {
        val p = DSpaceScraper.parsePage(page("sem151.html"))
        val names = p.children.map { it.name }
        assertTrue(names.toString(), "2025" in names && "First Semester - 2025 Regular" in names)
        val parents = p.children.filter { it.name == "B.Tech" || it.name.contains("Examination") }
        assertTrue("parents: $parents", parents.isEmpty())
        assertTrue("bad links: ${p.children.filterNot { handle.matches(it.path) }}", p.children.all { handle.matches(it.path) })
    }

    @Test fun sessionPageListsEveryPdf() {
        val p = DSpaceScraper.parsePage(page("item9640.html"))
        assertEquals(24, p.files.size)
        assertTrue(p.files.all { it.name.lowercase().endsWith(".pdf") })
        assertTrue(p.files.all { it.path.startsWith("/xmlui/bitstream/handle/123456789/9640/") })
        assertEquals("23CHY109 _I Sem End_Odd 2025.pdf", p.files.first().name)
        assertTrue(p.files.any { it.name.startsWith("24CYS102") })
    }

    @Test fun realFileNamesParse() {
        val names = DSpaceScraper.parsePage(page("item9640.html")).files.map { it.name }
        for (n in names) {
            assertEquals("year of $n", 2025, Names.year(n))
            assertEquals("semester of $n", 1, Names.semester(n))
            assertTrue("code of $n", Names.courseCode(n) != null)
        }
    }

    @Test fun discoverSearchReturnsExamSessions() {
        val r = DSpaceScraper.parseSearchResults(page("discover_24CYS205.html"))
        assertTrue("got ${r.size}", r.size >= 5)
        assertTrue(r.all { handle.matches(it.path) })
        assertTrue(r.map { it.name }.toString(), r.any { it.name.contains("Semester") })
    }
}
