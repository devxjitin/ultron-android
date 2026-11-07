package com.assistant.app.data

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileSearchTest {
    private val zone = ZoneId.of("UTC")
    private val now = 1_700_000_000_000L
    private val day = 86_400_000L

    private fun file(name: String, folder: String = "", age: Long = 0, key: Long = name.hashCode().toLong()) =
        FileCandidate(name, folder, now - age, key)

    private fun names(query: String, files: List<FileCandidate>) =
        FileSearch.rank(files, FileSearch.parse(query, now, zone)).map { it.file.name }

    @Test fun `a part of the name is enough and word order does not matter`() {
        val files = listOf(file("Resume_Rahul_2024.pdf"), file("IMG_0001.jpg"))
        assertEquals(listOf("Resume_Rahul_2024.pdf"), names("rahul resume", files))
        assertEquals(listOf("Resume_Rahul_2024.pdf"), names("res", files))
    }

    @Test fun `filler words and the kind word are ignored`() {
        val files = listOf(file("Invoice March.pdf"))
        assertEquals(listOf("Invoice March.pdf"), names("show me my invoice file please", files))
    }

    @Test fun `one typo still finds the file`() {
        val files = listOf(file("Invoice_March.pdf"))
        assertEquals(listOf("Invoice_March.pdf"), names("invoise", files))
    }

    @Test fun `the folder name counts`() {
        val files = listOf(file("a1.jpg", "DCIM/Goa Trip/"), file("a2.jpg", "DCIM/Camera/"))
        assertEquals(listOf("a1.jpg"), names("goa", files))
    }

    @Test fun `yesterday keeps only yesterdays files, newest first`() {
        val request = FileSearch.parse("photos from yesterday", now, zone)
        assertTrue(request.words.isEmpty())
        assertNotNull(request.from)
        val start = request.from!!
        val inside = file("in.jpg", age = now - (start + 1000))
        val outside = file("out.jpg", age = 5 * day)
        assertEquals(listOf("in.jpg"), FileSearch.rank(listOf(outside, inside), request).map { it.file.name })
    }

    @Test fun `no words lists the newest first`() {
        val files = listOf(file("old.jpg", age = 3 * day), file("new.jpg", age = 1))
        assertEquals(listOf("new.jpg", "old.jpg"), names("", files))
    }

    @Test fun `a word that fits nothing gives no match but closest names are offered`() {
        val files = listOf(file("Passport_scan.pdf"), file("holiday.jpg"))
        val request = FileSearch.parse("zzzzzz", now, zone)
        assertTrue(FileSearch.rank(files, request).isEmpty())
        assertTrue(FileSearch.closest(files, FileSearch.parse("passprt", now, zone), 3).any { it.name == "Passport_scan.pdf" })
    }

    @Test fun `a day word inside another word is not a date`() {
        assertNull(FileSearch.parse("kalyan photos", now, zone).from)
    }
}
