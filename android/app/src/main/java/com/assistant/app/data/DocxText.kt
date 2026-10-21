package com.assistant.app.data

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

/** Reads the plain text out of a Word (.docx) file, which is a zip holding word/document.xml. */
object DocxText {
    const val MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

    /** A small zip can inflate enormously, so only this much of word/document.xml is ever read. */
    private const val MAX_XML_BYTES = 8 * 1024 * 1024
    private const val MAX_ENTRIES = 500
    private const val MAX_TAG_CHARS = 512

    /** Shown instead of text when the file is not a readable Word document, so nobody mistakes it for an empty one. */
    const val UNREADABLE = "[This document could not be read: it is damaged or is not a Word file.]"
    const val TOO_LARGE_NOTE = "\n[The rest of this document was not read because it is too large.]"

    private val blankRuns = Regex("\n{3,}")
    private val numericEntity = Regex("&#(x?)([0-9a-fA-F]{1,6});")

    /**
     * The document's text with paragraphs on separate lines, cut at [maxChars]. The XML is scanned as a stream, so the
     * whole document is never held in memory; a file that is not readable gives [UNREADABLE], and one that is too large
     * ends with [TOO_LARGE_NOTE].
     */
    fun extract(bytes: ByteArray, maxChars: Int): String = try {
        var result: String? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entries = 0
            while (entries++ < MAX_ENTRIES) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "word/document.xml") {
                    result = scan(zip, maxChars)
                    break
                }
            }
        }
        result ?: UNREADABLE
    } catch (e: Exception) {
        UNREADABLE
    }

    private class CappedStream(private val source: InputStream, private val limit: Int) : InputStream() {
        var hitLimit = false
            private set
        private var count = 0

        override fun read(): Int {
            if (count >= limit) { hitLimit = true; return -1 }
            val b = source.read()
            if (b >= 0) count++
            return b
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (count >= limit) { hitLimit = true; return -1 }
            val n = source.read(buffer, offset, minOf(length, limit - count))
            if (n > 0) count += n
            return n
        }
    }

    private fun scan(source: InputStream, maxChars: Int): String {
        val capped = CappedStream(source, MAX_XML_BYTES)
        val reader = InputStreamReader(capped, Charsets.UTF_8)
        val out = StringBuilder()
        val run = StringBuilder()
        val tag = StringBuilder()
        var inTag = false
        val buffer = CharArray(8192)

        fun flushRun() {
            if (run.isNotEmpty()) { out.append(decode(run.toString())); run.setLength(0) }
        }

        read@ while (out.length < maxChars) {
            val n = reader.read(buffer)
            if (n < 0) break
            for (i in 0 until n) {
                val c = buffer[i]
                if (inTag) {
                    if (c == '>') {
                        inTag = false
                        flushRun()
                        val name = tag.toString().trim().trimEnd('/').trim().substringBefore(' ').substringBefore('\n')
                        when {
                            name == "/w:p" -> out.append('\n')
                            name == "w:br" || name == "w:cr" -> out.append('\n')
                            name == "w:tab" -> out.append('\t')
                        }
                        tag.setLength(0)
                        if (out.length >= maxChars) break@read
                    } else if (tag.length < MAX_TAG_CHARS) {
                        tag.append(c)
                    }
                } else if (c == '<') {
                    inTag = true
                } else {
                    run.append(c)
                    if (run.length > 4096) flushRun()
                }
            }
        }
        flushRun()
        val text = out.toString().replace(blankRuns, "\n\n").trim().take(maxChars)
        return if (capped.hitLimit && text.length < maxChars) text + TOO_LARGE_NOTE else text
    }

    private fun decode(text: String): String {
        if ('&' !in text) return text
        return text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
            .replace(numericEntity) { match ->
                val code = match.groupValues[2].toIntOrNull(if (match.groupValues[1].isEmpty()) 10 else 16)
                if (code != null && Character.isValidCodePoint(code)) String(Character.toChars(code)) else match.value
            }
            .replace("&amp;", "&")
    }
}
