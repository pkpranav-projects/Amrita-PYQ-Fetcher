package com.amrita.pyqfetcher.data.net

import java.util.Locale

/**
 * Builds a syntactically valid PDF 1.4 document (port of createDemoPDF in webclient.go),
 * but with a correctly computed cross-reference table so strict viewers open it cleanly.
 */
object DemoPdfGenerator {

    fun generate(paperTitle: String): ByteArray {
        val safeTitle = escape(paperTitle).take(90)
        val content = buildString {
            append("BT\n/F1 18 Tf\n50 720 Td\n(Amrita PYQ Fetcher) Tj\n")
            append("/F1 12 Tf\n0 -30 Td\n(Offline Demo Mode - Simulated Question Paper) Tj\n")
            append("0 -25 Td\n($safeTitle) Tj\n")
            append("0 -25 Td\n(Connect to Amrita campus Wi-Fi or VPN to download the real PDFs.) Tj\nET\n")
        }

        val sb = StringBuilder("%PDF-1.4\n")
        val offsets = ArrayList<Int>()
        fun obj(body: String) {
            offsets.add(sb.length)
            sb.append(offsets.size).append(" 0 obj\n").append(body).append("\nendobj\n")
        }
        obj("<< /Type /Catalog /Pages 2 0 R >>")
        obj("<< /Type /Pages /Kids [3 0 R] /Count 1 >>")
        obj("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>")
        obj("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")
        obj("<< /Length ${content.length} >>\nstream\n${content}endstream")

        val xrefPos = sb.length
        sb.append("xref\n0 ").append(offsets.size + 1).append('\n')
        sb.append("0000000000 65535 f \n")
        for (o in offsets) sb.append(String.format(Locale.US, "%010d 00000 n \n", o))
        sb.append("trailer\n<< /Size ").append(offsets.size + 1).append(" /Root 1 0 R >>\n")
        sb.append("startxref\n").append(xrefPos).append("\n%%EOF\n")
        return sb.toString().toByteArray(Charsets.ISO_8859_1)
    }

    private fun escape(s: String): String = buildString {
        for (c in s) {
            when {
                c == '\\' || c == '(' || c == ')' -> append('\\').append(c)
                c.code in 32..126 -> append(c)
                else -> append('?')
            }
        }
    }
}
