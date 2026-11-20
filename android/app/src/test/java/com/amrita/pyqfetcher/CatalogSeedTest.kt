package com.amrita.pyqfetcher

import com.amrita.pyqfetcher.data.seed.SeedMapper
import com.amrita.pyqfetcher.domain.PaperKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Guards the bundled catalog_seed.json generated from cmd/internal/menu/cys_catalog.go. */
class CatalogSeedTest {
    private val seed = SeedMapper.parse(File("src/main/assets/catalog_seed.json").readText(Charsets.UTF_8))

    private val allPapers = seed.subjects.flatMap { s -> s.papers.map { s to it } }

    @Test fun keepsAllVerifiedPapers() {
        assertTrue("expected at least 157 verified papers, got ${allPapers.size}", allPapers.size >= 157)
        assertEquals(38, seed.subjects.size)
    }

    @Test fun everyPathIsARealBitstreamPdf() {
        val prefix = "/xmlui/bitstream/handle/123456789/"
        for ((s, p) in allPapers) {
            assertTrue("${s.code}: ${p.path}", p.path.startsWith(prefix))
            assertTrue("${s.code}: ${p.path}", p.path.substringBefore('?').lowercase().endsWith(".pdf"))
            assertTrue("${s.code}: handle in ${p.path}", p.handle.isNotBlank() && p.path.contains("/${p.handle}/"))
            assertTrue("${s.code}: no fabricated placeholder", !p.path.contains("EndSem_Regular") && !p.path.contains("MidTerm_P1"))
        }
    }

    @Test fun noDuplicatePathsWithinASubject() {
        for (s in seed.subjects) {
            val paths = s.papers.map { it.path }
            assertEquals("duplicate path in ${s.code}", paths.size, paths.toSet().size)
        }
    }

    @Test fun subjectCodesAreUniqueAndSemestersValid() {
        assertEquals(seed.subjects.size, seed.subjects.map { it.code }.toSet().size)
        assertTrue(seed.subjects.all { it.semester in 1..8 })
        assertTrue(seed.subjects.all { it.branch == "CYS" })
    }

    @Test fun yearsAreSane() {
        assertTrue(allPapers.all { (_, p) -> p.year in 2015..2030 })
    }

    @Test fun legacyBridgesFromTheCurriculumAreMapped() {
        val byCode = seed.subjects.associateBy { it.code }
        assertTrue("20CYS114" in byCode.getValue("24CYS101").legacyCodes)
        assertTrue("20CYS101" in byCode.getValue("24CYS114").legacyCodes)
        assertTrue("20CYS204" in byCode.getValue("24CYS205").legacyCodes)
        assertTrue("20CYS205" in byCode.getValue("24CYS206").legacyCodes)
        val dsa = byCode.getValue("24CYS215").legacyCodes
        assertTrue("20CY214" in dsa && "20CYS214" in dsa)
    }

    @Test fun bridgedSubjectsContainBothNewAndLegacyPapers() {
        val dbms = seed.subjects.first { it.code == "24CYS205" }
        assertTrue(dbms.papers.any { it.sourceCode == "24CYS205" })
        assertTrue(dbms.papers.any { it.sourceCode == "20CYS204" })
    }

    @Test fun allBranchesPresentAndOnlyCysIsFeatured() {
        assertEquals(setOf("CYS", "CSE", "ECE", "AIE", "EEE", "MECH", "MCA", "MTECH"), seed.branches.map { it.code }.toSet())
        assertEquals(listOf("CYS"), seed.branches.filter { it.featured }.map { it.code })
    }

    @Test fun mapperProducesUniqueStableIds() {
        val e = SeedMapper.toEntities(seed)
        assertEquals(e.papers.size, e.papers.map { it.id }.toSet().size)
        assertEquals(e.subjects.size, e.subjects.map { it.id }.toSet().size)
        val again = SeedMapper.toEntities(seed)
        assertEquals(e.papers.map { it.id }, again.papers.map { it.id })
        assertTrue(e.papers.all { p -> e.subjects.any { it.id == p.subjectId } })
    }

    @Test fun classifiesAssessmentsSeparatelyFromMidTerms() {
        val e = SeedMapper.toEntities(seed)
        val kinds = e.papers.map { PaperKind.from(it.kind) }.toSet()
        assertEquals(setOf(PaperKind.END_SEM, PaperKind.MID_TERM, PaperKind.ASSESSMENT), kinds)
        assertTrue(e.papers.filter { it.exam.startsWith("Assessment") }.all { it.kind == PaperKind.ASSESSMENT.name })
    }
}
