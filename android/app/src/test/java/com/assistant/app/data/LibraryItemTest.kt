package com.assistant.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryItemTest {
    private fun ref(path: String, name: String = "notes.pdf", size: Int = 100) = AttachmentRef(name, "application/pdf", path, size)

    private val sessions = listOf(
        ChatSession("a", 0, listOf(
            ConversationTurn("one", "r", timestampMillis = 1, attachments = listOf(ref("a/1"))),
            ConversationTurn("two", "r", timestampMillis = 3, attachments = listOf(ref("a/2"), ref("a/3", name = "other.pdf")))
        )),
        ChatSession("b", 0, listOf(ConversationTurn("three", "r", timestampMillis = 2, attachments = listOf(ref("b/1")))))
    )

    @Test fun `a file sent more than once is listed once as its newest copy`() {
        val items = libraryItems(sessions)
        assertEquals(listOf("a/2", "a/3"), items.map { it.file.path }.sorted())
    }

    @Test fun `every saved copy of a file is found for deleting`() {
        val item = libraryItems(sessions).first { it.file.path == "a/2" }
        assertEquals(setOf("a" to "a/1", "a" to "a/2", "b" to "b/1"), libraryCopies(sessions, item).toSet())
    }
}
