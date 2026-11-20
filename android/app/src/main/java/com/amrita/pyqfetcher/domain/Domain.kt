package com.amrita.pyqfetcher.domain

import java.security.MessageDigest
import java.util.Locale

enum class PaperKind(val label: String) {
    END_SEM("End-Sem"),
    MID_TERM("Mid-Term"),
    ASSESSMENT("Assessment");

    companion object {
        fun from(value: String): PaperKind = entries.firstOrNull { it.name == value } ?: END_SEM

        /** Classifies a seed entry (type + exam) or a live-scraped file name. */
        fun classify(type: String, exam: String, fileName: String = ""): PaperKind {
            val hay = "$exam $fileName".lowercase(Locale.ROOT)
            if (hay.contains("assess") || Regex("""_(i|ii|iii)\s*ass""", RegexOption.IGNORE_CASE).containsMatchIn(fileName)) {
                return ASSESSMENT
            }
            return when {
                type.equals("Midterm", true) -> MID_TERM
                type.equals("EndSem", true) -> END_SEM
                hay.contains("mid") -> MID_TERM
                else -> END_SEM
            }
        }
    }
}

enum class StudyStatus(val label: String) {
    NOT_STARTED("Not started"),
    IN_PROGRESS("In progress"),
    SOLVED("Solved");

    companion object {
        fun from(value: String): StudyStatus = entries.firstOrNull { it.name == value } ?: NOT_STARTED
    }
}

enum class ExamFilter(val label: String) {
    ALL("All"),
    END_SEM("End-Sem"),
    MID_TERM("Mid-Term"),
    ASSESSMENTS("Assessments");

    fun matches(kind: PaperKind): Boolean = when (this) {
        ALL -> true
        END_SEM -> kind == PaperKind.END_SEM
        MID_TERM -> kind == PaperKind.MID_TERM
        ASSESSMENTS -> kind == PaperKind.ASSESSMENT
    }
}

data class YearRange(val label: String, val start: Int, val end: Int) {
    operator fun contains(year: Int): Boolean = year in start..end
}

/** Year-range packs ported from easymode.go. */
object Packs {
    val FIVE_YEAR = YearRange("Past 5 Years Complete Pack (2021–2026)", 2021, 2026)
    val TWO_YEAR = listOf(
        YearRange("2025–2026", 2025, 2026),
        YearRange("2023–2024", 2023, 2024),
        YearRange("2021–2022", 2021, 2022),
        YearRange("2019–2020", 2019, 2020),
    )
}

/** Minimal view of a paper used by pack/filter logic (keeps it unit-testable without Room). */
data class PackPaper(val id: String, val year: Int, val kind: PaperKind, val saved: Boolean)

data class Pack(val range: YearRange, val papers: List<PackPaper>) {
    val total: Int get() = papers.size
    val saved: Int get() = papers.count { it.saved }
    val missing: Int get() = total - saved
    val fullySaved: Boolean get() = total > 0 && missing == 0
}

object PackBuilder {
    fun filterByRange(papers: List<PackPaper>, start: Int, end: Int): List<PackPaper> =
        papers.filter { it.year in start..end }

    /**
     * Builds the visible packs: the 5-year pack plus only those 2-year ranges that actually
     * contain verified papers (after applying the exam-type filter).
     */
    fun build(papers: List<PackPaper>, filter: ExamFilter): List<Pack> {
        val scoped = papers.filter { filter.matches(it.kind) }
        val result = ArrayList<Pack>()
        val five = Pack(Packs.FIVE_YEAR, filterByRange(scoped, Packs.FIVE_YEAR.start, Packs.FIVE_YEAR.end))
        if (five.total > 0) result.add(five)
        for (r in Packs.TWO_YEAR) {
            val p = Pack(r, filterByRange(scoped, r.start, r.end))
            if (p.total > 0) result.add(p)
        }
        return result
    }
}

object Ids {
    /** Short stable id that is safe in navigation routes and file names. */
    fun paperId(subjectId: String, path: String): String {
        val md = MessageDigest.getInstance("SHA-1").digest("$subjectId|${path.substringBefore('?')}".toByteArray())
        return "p" + md.joinToString("") { "%02x".format(it) }.take(16)
    }

    fun subjectId(branch: String, code: String) = "$branch:$code"
}

object Names {
    private val roman = mapOf("I" to 1, "II" to 2, "III" to 3, "IV" to 4, "V" to 5, "VI" to 6, "VII" to 7, "VIII" to 8)
    private val codeRegex = Regex("""(?<![A-Z0-9])(\d{2}[A-Z]{2,4}\d{2,3}[A-Z]?)""")
    private val yearRegex = Regex("""(20\d{2})""")
    private val semRegex = Regex("""(?<![A-Za-z])(VIII|VII|VI|IV|V|III|II|I)\s*Sem""", RegexOption.IGNORE_CASE)

    fun courseCode(fileName: String): String? = codeRegex.find(fileName.uppercase(Locale.ROOT))?.groupValues?.get(1)

    fun year(fileName: String): Int = yearRegex.findAll(fileName).lastOrNull()?.groupValues?.get(1)?.toIntOrNull() ?: 0

    fun semester(fileName: String): Int =
        semRegex.find(fileName)?.groupValues?.get(1)?.uppercase(Locale.ROOT)?.let { roman[it] } ?: 0

    private val ordinalSem = Regex("""\b(first|second|third|fourth|fifth|sixth|seventh|eighth|1st|2nd|3rd|4th|5th|6th|7th|8th)\s*semester""", RegexOption.IGNORE_CASE)
    private val ordinals = listOf("first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth")

    /** Semester from an exam-session title such as "Third Semester- Mid-Term 2025", or 0. */
    fun semesterFromTitle(title: String): Int {
        val w = ordinalSem.find(title)?.groupValues?.get(1)?.lowercase(Locale.ROOT) ?: return 0
        return ordinals.indexOf(w).takeIf { it >= 0 }?.plus(1) ?: w.first().digitToIntOrNull() ?: 0
    }

    /** Best-effort branch inferred from a course code such as 23CSE201 → CSE. */
    fun branchOf(code: String): String? {
        val letters = code.dropWhile { it.isDigit() }.takeWhile { it.isLetter() }
        return when {
            letters == "CYS" || letters == "CY" -> "CYS"
            letters == "CSE" || letters == "CS" -> "CSE"
            letters == "ECE" || letters == "EC" -> "ECE"
            letters == "AIE" || letters == "AI" -> "AIE"
            letters == "EEE" || letters == "EE" -> "EEE"
            letters == "MEE" || letters == "MEC" || letters == "ME" -> "MECH"
            letters == "MCA" -> "MCA"
            else -> null
        }
    }

    fun sanitizeFile(name: String): String {
        val cleaned = name.replace(Regex("""[\\/:*?"<>|\s]+"""), "_").trim('_')
        return if (cleaned.lowercase(Locale.ROOT).endsWith(".pdf")) cleaned else "$cleaned.pdf"
    }
}
