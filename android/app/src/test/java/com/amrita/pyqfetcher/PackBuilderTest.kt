package com.amrita.pyqfetcher

import com.amrita.pyqfetcher.domain.ExamFilter
import com.amrita.pyqfetcher.domain.PackBuilder
import com.amrita.pyqfetcher.domain.PackPaper
import com.amrita.pyqfetcher.domain.PaperKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackBuilderTest {
    private fun p(id: String, year: Int, kind: PaperKind, saved: Boolean = false) = PackPaper(id, year, kind, saved)

    private val papers = listOf(
        p("a", 2026, PaperKind.MID_TERM),
        p("b", 2025, PaperKind.END_SEM, saved = true),
        p("c", 2024, PaperKind.MID_TERM),
        p("d", 2023, PaperKind.END_SEM),
        p("e", 2022, PaperKind.ASSESSMENT),
        p("f", 2021, PaperKind.END_SEM),
        p("g", 2020, PaperKind.END_SEM),
        p("h", 2018, PaperKind.END_SEM),
    )

    @Test fun filterByRangeIsInclusive() {
        val r = PackBuilder.filterByRange(papers, 2023, 2025)
        assertEquals(listOf("b", "c", "d"), r.map { it.id })
    }

    @Test fun fiveYearPackCoversTwentyOneToTwentySix() {
        val five = PackBuilder.build(papers, ExamFilter.ALL).first()
        assertEquals("a b c d e f".split(" ").toSet(), five.papers.map { it.id }.toSet())
        assertEquals(6, five.total)
        assertEquals(1, five.saved)
        assertEquals(5, five.missing)
    }

    @Test fun onlyRangesWithPapersAreShown() {
        val labels = PackBuilder.build(papers.filter { it.year >= 2023 }, ExamFilter.ALL).map { it.range.label }
        assertTrue(labels.contains("2025–2026"))
        assertTrue(labels.contains("2023–2024"))
        assertFalse(labels.contains("2021–2022"))
        assertFalse(labels.contains("2019–2020"))
    }

    @Test fun twoYearRangesPartitionCorrectly() {
        val packs = PackBuilder.build(papers, ExamFilter.ALL).associateBy { it.range.label }
        assertEquals(setOf("a", "b"), packs["2025–2026"]!!.papers.map { it.id }.toSet())
        assertEquals(setOf("c", "d"), packs["2023–2024"]!!.papers.map { it.id }.toSet())
        assertEquals(setOf("e", "f"), packs["2021–2022"]!!.papers.map { it.id }.toSet())
        assertEquals(setOf("g"), packs["2019–2020"]!!.papers.map { it.id }.toSet())
    }

    @Test fun examFilterNarrowsEveryPack() {
        val mid = PackBuilder.build(papers, ExamFilter.MID_TERM)
        assertEquals(setOf("a", "c"), mid.first().papers.map { it.id }.toSet())
        val ass = PackBuilder.build(papers, ExamFilter.ASSESSMENTS)
        assertEquals(listOf("e"), ass.first().papers.map { it.id })
        assertTrue(ass.none { it.range.label == "2025–2026" })
    }

    @Test fun fullySavedDetection() {
        val saved = listOf(p("x", 2025, PaperKind.END_SEM, true), p("y", 2026, PaperKind.END_SEM, true))
        val pack = PackBuilder.build(saved, ExamFilter.ALL).first()
        assertTrue(pack.fullySaved)
        assertEquals(0, pack.missing)
    }

    @Test fun emptyInputGivesNoPacks() {
        assertTrue(PackBuilder.build(emptyList(), ExamFilter.ALL).isEmpty())
    }
}
