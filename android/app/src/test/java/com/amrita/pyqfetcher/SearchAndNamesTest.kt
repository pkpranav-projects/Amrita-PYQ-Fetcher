package com.amrita.pyqfetcher

import com.amrita.pyqfetcher.data.db.PaperEntity
import com.amrita.pyqfetcher.data.db.PaperRow
import com.amrita.pyqfetcher.domain.Names
import com.amrita.pyqfetcher.domain.PaperKind
import com.amrita.pyqfetcher.domain.SearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchAndNamesTest {
    private fun row(code: String, name: String, legacy: String, year: Int, kind: PaperKind, source: String = code, sem: Int = 3) =
        PaperRow(
            paper = PaperEntity(
                id = "$code-$year-${kind.name}", subjectId = "CYS:$code", title = "$year - ${kind.label} ($source)",
                path = "/x/$source.pdf", fileName = "$source.pdf", year = year, kind = kind.name, exam = kind.label, sourceCode = source,
            ),
            localPath = null, dlDemo = null, subjectCode = code, subjectName = name, legacyCodes = legacy, branchCode = "CYS", semester = sem,
        )

    private val rows = listOf(
        row("24CYS205", "Database Management System", "20CYS204", 2025, PaperKind.END_SEM),
        row("24CYS205", "Database Management System", "20CYS204", 2023, PaperKind.END_SEM, source = "20CYS204"),
        row("24CYS203", "Operating Systems", "20CYS203", 2024, PaperKind.MID_TERM),
        row("24CYS206", "Modern Cryptography", "20CYS205", 2025, PaperKind.MID_TERM),
        row("24CYS114", "Classical Cryptography", "20CYS101", 2022, PaperKind.ASSESSMENT, sem = 2),
    )

    @Test fun findsByNewCode() = assertEquals(2, SearchEngine.search(rows, "24CYS205").size)

    @Test fun findsByLegacyCode() {
        val r = SearchEngine.search(rows, "20CYS204")
        assertEquals(2, r.size)
        assertTrue(r.all { it.subjectCode == "24CYS205" })
    }

    @Test fun findsByAbbreviation() {
        assertEquals(setOf("24CYS203"), SearchEngine.search(rows, "os").map { it.subjectCode }.toSet())
        assertEquals(setOf("24CYS205"), SearchEngine.search(rows, "DBMS").map { it.subjectCode }.toSet())
    }

    @Test fun findsByYearAndExamType() {
        assertEquals(1, SearchEngine.search(rows, "2024").size)
        assertEquals(setOf("24CYS206", "24CYS203"), SearchEngine.search(rows, "mid-term").map { it.subjectCode }.toSet())
        assertEquals(1, SearchEngine.search(rows, "crypto 2025").size)
    }

    @Test fun toleratesOneTypo() {
        assertEquals(2, SearchEngine.search(rows, "cryptograpy").size)
        assertTrue(SearchEngine.within1("cryptography", "cryptograpy"))
        assertTrue(SearchEngine.within1("database", "databse"))
        assertFalse(SearchEngine.within1("database", "network"))
    }

    @Test fun blankAndUnknownQueriesReturnNothing() {
        assertTrue(SearchEngine.search(rows, "  ").isEmpty())
        assertTrue(SearchEngine.search(rows, "zzzzqqq").isEmpty())
    }

    @Test fun parsesCourseCodeYearSemesterFromFileNames() {
        val f = "20CYS201_III Sem End_Odd 2023.pdf"
        assertEquals("20CYS201", Names.courseCode(f))
        assertEquals(2023, Names.year(f))
        assertEquals(3, Names.semester(f))
        assertEquals(8, Names.semester("19CSE402_VIII Sem End_Even 2025.pdf"))
        assertEquals(4, Names.semester("24CYS211_IV Sem End_Even 2025.pdf"))
        assertNull(Names.courseCode("random notes.pdf"))
        assertEquals(0, Names.year("random notes.pdf"))
    }

    @Test fun infersBranchFromCode() {
        assertEquals("CYS", Names.branchOf("24CYS101"))
        assertEquals("CSE", Names.branchOf("19CSE201"))
        assertEquals("ECE", Names.branchOf("19ECE101"))
        assertEquals("MCA", Names.branchOf("20MCA101"))
        assertNull(Names.branchOf("24MAT109"))
    }

    @Test fun classifiesKindsFromLiveFileNames() {
        assertEquals(PaperKind.MID_TERM, PaperKind.classify("", "", "20CYS301_V Sem Mid Term_Odd 2025.pdf"))
        assertEquals(PaperKind.END_SEM, PaperKind.classify("", "", "20CYS301_V Sem End_Odd 2024.pdf"))
        assertEquals(PaperKind.ASSESSMENT, PaperKind.classify("", "", "20CYS114_II Ass May 2023.pdf"))
        assertEquals(PaperKind.ASSESSMENT, PaperKind.classify("Midterm", "Assessment 2", ""))
        assertEquals(PaperKind.MID_TERM, PaperKind.classify("Midterm", "Midterm", ""))
    }
}
