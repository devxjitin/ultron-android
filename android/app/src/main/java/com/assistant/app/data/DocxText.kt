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
    private val blankRuns = Regex("\n{3,}")

    /** The document's text with paragraphs on separate lines, cut at [maxChars]; empty if it cannot be read. */
    fun extract(bytes: ByteArray, maxChars: Int): String = try {
        var xml = ""
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "word/document.xml") {
                    xml = zip.readBytes().toString(Charsets.UTF_8)
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
}
