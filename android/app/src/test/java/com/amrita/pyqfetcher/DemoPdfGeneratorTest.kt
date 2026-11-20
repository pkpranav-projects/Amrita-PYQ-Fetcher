package com.amrita.pyqfetcher

import com.amrita.pyqfetcher.data.net.DemoPdfGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoPdfGeneratorTest {
    private val text = String(DemoPdfGenerator.generate("24CYS201 - Mid-Term (test)"), Charsets.ISO_8859_1)

    @Test fun hasPdfMagicAndEofMarker() {
        assertTrue(text.startsWith("%PDF-1.4\n"))
        assertTrue(text.trimEnd().endsWith("%%EOF"))
    }

    @Test fun crossReferenceOffsetsPointAtObjects() {
        val xrefPos = Regex("""startxref\n(\d+)""").find(text)!!.groupValues[1].toInt()
        assertTrue(text.substring(xrefPos).startsWith("xref\n0 6\n"))
        val entries = Regex("""(\d{10}) 00000 n """).findAll(text).map { it.groupValues[1].toInt() }.toList()
        assertEquals(5, entries.size)
        entries.forEachIndexed { i, off ->
            assertTrue("object ${i + 1}", text.substring(off).startsWith("${i + 1} 0 obj"))
        }
    }

    @Test fun streamLengthMatches() {
        val len = Regex("""/Length (\d+)""").find(text)!!.groupValues[1].toInt()
        val start = text.indexOf("stream\n") + "stream\n".length
        val end = text.indexOf("endstream")
        assertEquals(len, end - start)
    }

    @Test fun escapesParenthesesInTitle() {
        val t = String(DemoPdfGenerator.generate("a (b) \\ c é"), Charsets.ISO_8859_1)
        assertTrue(t.contains("a \\(b\\) \\\\ c ?"))
    }
}
