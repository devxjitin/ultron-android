package com.assistant.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DocxTextTest {
    private fun docx(documentXml: String, extraEntries: Map<String, ByteArray> = emptyMap()): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            extraEntries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("word/document.xml")); zip.write(documentXml.toByteArray()); zip.closeEntry()
        }
        return out.toByteArray()
    }

    @Test fun `paragraphs become lines and entities are decoded`() {
        val xml = "<w:document><w:p><w:r><w:t>Hello &amp; welcome</w:t></w:r></w:p><w:p><w:r><w:t>Second</w:t></w:r></w:p></w:document>"
        assertEquals("Hello & welcome\nSecond", DocxText.extract(docx(xml), 1000))
    }

    @Test fun `text is cut at the character limit`() {
        val xml = "<w:p><w:t>${"a".repeat(500)}</w:t></w:p>"
        assertEquals(100, DocxText.extract(docx(xml), 100).length)
    }

    @Test fun `a file without document xml gives empty text`() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip -> zip.putNextEntry(ZipEntry("other.xml")); zip.write(byteArrayOf(1)); zip.closeEntry() }
        assertEquals("", DocxText.extract(out.toByteArray(), 100))
    }

    @Test fun `not a zip gives empty text`() {
        assertEquals("", DocxText.extract(byteArrayOf(1, 2, 3, 4), 100))
    }

    @Test fun `a highly compressible huge document is read only up to the cap`() {
        // 40 MB of text compresses to a few KB; only the first 8 MB may ever be read.
        val huge = "<w:p><w:t>" + "x".repeat(40 * 1024 * 1024) + "</w:t></w:p>"
        val bytes = docx(huge)
        assertTrue("fixture should be small, was ${bytes.size}", bytes.size < 200_000)
        val text = DocxText.extract(bytes, 1000)
        assertEquals(1000, text.length)
    }
}
