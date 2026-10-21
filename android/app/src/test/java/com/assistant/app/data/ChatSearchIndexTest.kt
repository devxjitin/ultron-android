package com.assistant.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSearchIndexTest {
    private fun turn(user: String, assistant: String, vararg files: String) =
        ConversationTurn(user, assistant, 0L, files.map { AttachmentRef(it, "text/plain", it, 1) })

    private val travel = ChatSession("a", 0L, listOf(turn("hi", "hello"), turn("plan a trip to Goa", "Sure, **Goa** in December is lovely")), customTitle = "Holiday")
    private val taxes = ChatSession("b", 0L, listOf(turn("how do I file taxes", "Use the portal", "return.pdf")))
    private val index = ChatSearchIndex(listOf(travel, taxes))

    @Test fun `a chat must contain every word`() {
        assertEquals(listOf("a"), index.search(listOf("goa", "december"), SearchScope.ALL).map { it.session.id })
        assertTrue(index.search(listOf("goa", "taxes"), SearchScope.ALL).isEmpty())
    }

    @Test fun `the hit points at the exchange that matched, with a clean snippet`() {
        val hit = index.search(listOf("december"), SearchScope.ALL).single()
        assertEquals(1, hit.turnIndex)
        assertTrue(hit.snippet!!.contains("Goa in December"))
        assertTrue(!hit.snippet!!.contains("**"))
    }

    @Test fun `the scope limits where words are looked for`() {
        assertEquals(listOf("a"), index.search(listOf("holiday"), SearchScope.TITLES).map { it.session.id })
        assertTrue(index.search(listOf("holiday"), SearchScope.MESSAGES).isEmpty())
        assertEquals(listOf("b"), index.search(listOf("return"), SearchScope.FILES).map { it.session.id })
        assertTrue(index.search(listOf("return"), SearchScope.TITLES).isEmpty())
    }

    @Test fun `a title match has nothing to jump to`() {
        assertNull(index.search(listOf("holiday"), SearchScope.TITLES).single().turnIndex)
    }

    @Test fun `more matches rank higher and results are capped`() {
        val many = (1..80).map { ChatSession("s$it", 0L, listOf(turn("word " + "word ".repeat(it), "x"))) }
        val hits = ChatSearchIndex(many).search(listOf("word"), SearchScope.ALL)
        assertEquals(ChatSearchIndex.MAX_RESULTS, hits.size)
        assertEquals("s80", hits.first().session.id)
    }

    @Test fun `no words means no results`() {
        assertTrue(index.search(emptyList(), SearchScope.ALL).isEmpty())
    }
}
