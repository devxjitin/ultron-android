package com.assistant.app.data

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/** Reads the plain text out of a Word (.docx) file, which is a zip holding word/document.xml. */
object DocxText {
    const val MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

    private val paragraphEnd = Regex("</w:p>")
    private val lineBreak = Regex("<w:br\\s*/?>|<w:cr\\s*/?>")
    private val tab = Regex("<w:tab\\s*/?>")
    private val anyTag = Regex("<[^>]+>")

    /** A small zip can inflate enormously, so only this much of word/document.xml is ever read. */
    private const val MAX_XML_BYTES = 8 * 1024 * 1024
    private const val MAX_ENTRIES = 500
    private val blankRuns = Regex("\n{3,}")

    /** The document's text with paragraphs on separate lines, cut at [maxChars]; empty if it cannot be read. */
    fun extract(bytes: ByteArray, maxChars: Int): String = try {
        var xml = ""
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entries = 0
            while (entries++ < MAX_ENTRIES) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "word/document.xml") {
                    xml = readCapped(zip).toString(Charsets.UTF_8)
                    break
                }
            }
        }
        xml.replace(paragraphEnd, "\n").replace(lineBreak, "\n").replace(tab, "\t")
            .replace(anyTag, "")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
            .replace(blankRuns, "\n\n").trim().take(maxChars)
    } catch (e: Exception) {
        ""
    }

    /** Reads at most [MAX_XML_BYTES] from [input]; the rest of an oversized entry is ignored. */
    private fun readCapped(input: java.io.InputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (out.size() < MAX_XML_BYTES) {
            val n = input.read(buffer, 0, minOf(buffer.size, MAX_XML_BYTES - out.size()))
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
}
