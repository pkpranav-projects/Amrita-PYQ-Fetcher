package com.amrita.pyqfetcher.domain

import com.amrita.pyqfetcher.data.db.PaperRow
import java.util.Locale

/**
 * Instant fuzzy search over papers: course codes (new + legacy), subject titles, years,
 * exam types, with common abbreviations (OS, DBMS …) and one-typo tolerance.
 */
object SearchEngine {
    private val synonyms = mapOf(
        "os" to "operating systems",
        "dbms" to "database management",
        "db" to "database",
        "dsa" to "data structures",
        "cn" to "computer networks",
        "ml" to "machine learning",
        "ai" to "artificial intelligence",
        "vapt" to "vulnerability assessment penetration",
        "coa" to "computer organisation architecture",
        "dm" to "discrete mathematics",
        "ui" to "user interface",
        "mid" to "mid-term",
        "midterm" to "mid-term",
        "mid-sem" to "mid-term",
        "endsem" to "end-sem",
        "end" to "end-sem",
        "final" to "end-sem",
        "crypto" to "cryptography",
    )

    fun haystack(r: PaperRow): String = buildString {
        append(r.subjectCode).append(' ')
        append(r.legacyCodes.replace(',', ' ')).append(' ')
        append(r.subjectName).append(' ')
        append(r.paper.sourceCode).append(' ')
        append(r.paper.title).append(' ')
        append(r.paper.fileName).append(' ')
        append(if (r.paper.year > 0) r.paper.year.toString() else "").append(' ')
        append(PaperKind.from(r.paper.kind).label).append(' ')
        append("sem ").append(r.semester).append(' ')
        append(r.branchCode)
    }.lowercase(Locale.ROOT)

    fun matches(hay: String, query: String): Boolean {
        val tokens = query.lowercase(Locale.ROOT).trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return false
        val words = hay.split(Regex("""[^a-z0-9]+""")).filter { it.isNotEmpty() }
        return tokens.all { tok ->
            val expanded = synonyms[tok]
            hay.contains(tok) ||
                (expanded != null && expanded.split(' ').all { hay.contains(it) }) ||
                (tok.length >= 4 && tok.all { it.isLetter() } && words.any { w -> w.all { it.isLetter() } && within1(w, tok) })
        }
    }

    fun search(rows: List<PaperRow>, query: String): List<PaperRow> {
        if (query.isBlank()) return emptyList()
        return rows.filter { matches(haystack(it), query) }
            .sortedWith(compareByDescending<PaperRow> { exactCodeHit(it, query) }.thenByDescending { it.paper.year })
    }

    private fun exactCodeHit(r: PaperRow, q: String): Boolean {
        val query = q.trim().uppercase(Locale.ROOT)
        return r.subjectCode == query || r.legacyCodes.split(',').any { it == query }
    }

    /** True when the two words differ by at most one edit (insert, delete or substitute). */
    internal fun within1(a: String, b: String): Boolean {
        if (a == b) return true
        if (kotlin.math.abs(a.length - b.length) > 1) return false
        if (a.length < 4 || b.length < 4) return false
        var i = 0
        var j = 0
        var edits = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) {
                i++; j++
            } else {
                if (++edits > 1) return false
                when {
                    a.length > b.length -> i++
                    a.length < b.length -> j++
                    else -> { i++; j++ }
                }
            }
        }
        return edits + (a.length - i) + (b.length - j) <= 1
    }
}
