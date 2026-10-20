package com.assistant.app.data

import org.junit.Assert.*
import org.junit.Test

class HybridRetrievalTest {
    private val noVector = FloatArray(0)
    private fun chunk(doc: String, index: Int, text: String, heading: String? = null) =
        RagChunk(doc, index, text, noVector, heading = heading)

    private val corpus = listOf(
        chunk("fees", 0, "Tuition fee is 50000 per year", "Delhi > Fees"),
        chunk("fees", 1, "Hostel fee is 30000 per year", "Delhi > Fees"),
        chunk("rules", 0, "Visitors must register at the front desk", "Rules"),
        chunk("rooms", 0, "Seminar hall is Room 204 on the second floor", "Rooms"),
        chunk("misc", 0, "Parking is available near the gate", "Parking"),
        chunk("misc", 1, "Cafeteria opens at nine", "Cafeteria"),
        chunk("misc", 2, "Library closes at eight", "Library"),
        chunk("misc", 3, "Sports ground is open on weekends", "Sports"),
        chunk("misc", 4, "Medical room is on the ground floor", "Medical")
    )

    @Test fun `bm25 finds an exact identifier that vectors could miss`() {
        val keys = Bm25Index(corpus).search("where is room 204", limit = 3)
        assertEquals("rooms" to 0, keys.first())
    }

    @Test fun `bm25 ignores unknown and empty queries`() {
        val index = Bm25Index(corpus)
        assertTrue(index.search("zebra quantum", limit = 5).isEmpty())
        assertTrue(index.search("", limit = 5).isEmpty())
    }

    @Test fun `tokenizer keeps hindi and numbers`() {
        assertEquals(listOf("परीक्षा", "204"), bm25Tokens("परीक्षा, 204!"))
    }

    @Test fun `fusion ranks items found by both lists first`() {
        val a = listOf("x" to 0, "y" to 0, "z" to 0)
        val b = listOf("y" to 0, "w" to 0)
        val fused = fuseRankings(a, b, limit = 3)
        assertEquals("y" to 0, fused.first())
        assertEquals(3, fused.size)
    }

    @Test fun `context adds neighbours in reading order within the budget`() {
        val result = assembleContext(listOf(corpus[1]), corpus, mapOf("fees" to "Fee schedule"))
        assertEquals(2, result.size)
        assertTrue(result[0].startsWith("[Source: Fee schedule › Delhi > Fees]\nTuition"))
        assertTrue(result[1].contains("Hostel"))
    }

    @Test fun `neighbours are skipped when the extra budget is exhausted`() {
        val result = assembleContext(listOf(corpus[1]), corpus, emptyMap(), extraCharBudget = 5)
        assertEquals(1, result.size)
        assertTrue(result.single().startsWith("[Source: Unknown document"))
    }

    @Test fun `list style queries are detected`() {
        assertTrue(isListQuery("show me all exams"))
        assertTrue(isListQuery("सभी परीक्षा बताओ"))
        assertFalse(isListQuery("what is the hostel fee"))
    }
}
