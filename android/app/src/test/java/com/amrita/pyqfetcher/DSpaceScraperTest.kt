package com.amrita.pyqfetcher

import com.amrita.pyqfetcher.data.net.DSpaceScraper
import com.amrita.pyqfetcher.data.net.Resource
import com.amrita.pyqfetcher.data.net.ScrapeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Mirrors cmd/internal/requestclient/requestclient_test.go. */
class DSpaceScraperTest {

    @Test
    fun parseCourses_readsSpanAndHref() {
        val html = """<html><body>
            <div id="aspect_artifactbrowser_CommunityViewer_div_community-view">
                <div class="artifact-title"><span>Course1</span><a href="/course1"></a></div>
            </div></body></html>"""
        val r = DSpaceScraper.parseCourses(html)
        assertEquals(listOf(Resource("Course1", "/course1")), r)
    }

    @Test
    fun parseCourses_missingContainerIsEmpty() {
        assertTrue(DSpaceScraper.parseCourses("<html><body><p>nothing</p></body></html>").isEmpty())
    }

    @Test
    fun parseAssessments_combinesFirstTwoLists() {
        val html = """<html><body>
            <div id="aspect_artifactbrowser_CommunityViewer_div_community-view">
              <ul><li><a href="/assessment1"><span>Assessment1</span></a></li>
                  <li><a href="/assessment2"><span>Assessment2</span></a></li></ul>
              <ul><li><a href="/assessment3"><span>Assessment3</span></a></li></ul>
            </div></body></html>"""
        val r = DSpaceScraper.parseAssessments(html)
        assertEquals(
            listOf(Resource("Assessment1", "/assessment1"), Resource("Assessment2", "/assessment2"), Resource("Assessment3", "/assessment3")),
            r,
        )
    }

    @Test
    fun parseAssessments_singleList() {
        val html = """<div id="aspect_artifactbrowser_CommunityViewer_div_community-view"><ul>
            <li><a href="/a"><span>A</span></a></li></ul></div>"""
        assertEquals(listOf(Resource("A", "/a")), DSpaceScraper.parseAssessments(html))
    }

    @Test
    fun parseAssessments_noContainerThrows() {
        try {
            DSpaceScraper.parseAssessments("<html><body></body></html>")
            fail("expected ScrapeException")
        } catch (_: ScrapeException) {
        }
    }

    @Test
    fun parseSemesters_usesFirstListOnly() {
        val html = """<div id="aspect_artifactbrowser_CommunityViewer_div_community-view">
            <ul><li><a href="/sem1"><span>Semester1</span></a></li><li><a href="/sem2"><span>Semester2</span></a></li></ul>
            <ul><li><a href="/other"><span>Other</span></a></li></ul></div>"""
        assertEquals(
            listOf(Resource("Semester1", "/sem1"), Resource("Semester2", "/sem2")),
            DSpaceScraper.parseSemesters(html),
        )
    }

    @Test
    fun parseSemesters_emptyListThrows() {
        val html = """<div id="aspect_artifactbrowser_CommunityViewer_div_community-view"><ul></ul></div>"""
        try {
            DSpaceScraper.parseSemesters(html)
            fail("expected ScrapeException")
        } catch (_: ScrapeException) {
        }
    }

    @Test
    fun parseSubCommunities_readsDriNamespaceDiv() {
        val html = """<html><body><div xmlns="http://di.tamu.edu/DRI/1.0/"><ul>
            <li><a href="/hyper">Supply</a></li></ul></div></body></html>"""
        assertEquals(listOf(Resource("Supply", "/hyper")), DSpaceScraper.parseSubCommunities(html))
    }

    @Test
    fun parseSubCommunities_deduplicatesByHref() {
        val html = """<div xmlns="http://di.tamu.edu/DRI/1.0/"><ul>
            <li><a href="/x">One</a></li><li><a href="/x">One again</a></li></ul></div>"""
        assertEquals(1, DSpaceScraper.parseSubCommunities(html).size)
    }

    @Test
    fun parseFiles_readsSecondSpanTitleAndHref() {
        val html = """<html><body><div class="file-list"><div class="file-wrapper">
            <div><a href="/file1"></a></div>
            <div><div><span title="ignored"></span><span title="File1"></span></div></div>
            </div></div></body></html>"""
        assertEquals(listOf(Resource("File1", "/file1")), DSpaceScraper.parseFiles(html))
    }

    @Test
    fun parseFiles_realMirageMarkupAndFallbackName() {
        val html = """<div class="file-list">
          <div class="file-wrapper clearfix">
            <div class="thumbnail-wrapper"><a class="image-link" href="/xmlui/bitstream/handle/123456789/9640/24CYS102_I%20Sem%20End_Odd%202025.pdf?sequence=1"><img/></a></div>
            <div class="file-metadata"><div><span class="bold">Name:</span><span title="24CYS102_I Sem End_Odd 2025.pdf">24CYS102_I Sem End_Odd 2025.pdf</span></div></div>
          </div>
          <div class="file-wrapper"><div><a href="/xmlui/bitstream/handle/123456789/1/Plain%20Name.pdf"></a></div></div>
        </div>"""
        val r = DSpaceScraper.parseFiles(html)
        assertEquals(2, r.size)
        assertEquals("24CYS102_I Sem End_Odd 2025.pdf", r[0].name)
        assertEquals("Plain Name.pdf", r[1].name)
    }

    @Test
    fun parseSearchResults_readsArtifactDescriptionLinks() {
        val html = """<div class="artifact-description"><a href="/xmlui/handle/123456789/42">Operating Systems 2025</a></div>
            <div class="artifact-description"><a href="/xmlui/handle/123456789/42">Operating Systems 2025</a></div>
            <div class="artifact-description"><a href="/xmlui/handle/123456789/43">Cryptography</a></div>"""
        val r = DSpaceScraper.parseSearchResults(html)
        assertEquals(listOf("/xmlui/handle/123456789/42", "/xmlui/handle/123456789/43"), r.map { it.path })
    }

    @Test
    fun parsePage_collectsChildrenAndFiles() {
        val html = """<div id="aspect_artifactbrowser_CommunityViewer_div_community-view"><ul>
            <li><a href="/xmlui/handle/123456789/150"><span>B.Tech</span></a></li></ul></div>
            <div class="file-list"><div class="file-wrapper"><div><a href="/xmlui/bitstream/handle/123456789/1/a.pdf"></a></div>
            <div><div><span title="x"></span><span title="a.pdf"></span></div></div></div></div>"""
        val page = DSpaceScraper.parsePage(html)
        assertEquals(1, page.children.size)
        assertEquals(1, page.files.size)
    }

    @Test
    fun fileNameFromPath_decodesAndStripsQuery() {
        assertEquals(
            "20CYS201_III Sem End_Odd 2023.pdf",
            DSpaceScraper.fileNameFromPath("/xmlui/bitstream/handle/123456789/7558/20CYS201_III%20Sem%20End_Odd%202023.pdf?sequence=1"),
        )
    }
}
