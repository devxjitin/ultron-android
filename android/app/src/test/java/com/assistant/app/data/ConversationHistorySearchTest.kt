package com.assistant.app.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneOffset

class ConversationHistorySearchTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun vector(axis: Int) = FloatArray(768).also { it[axis] = 1f }
    private fun at(day: Int, hour: Int = 12) =
        LocalDateTime.of(2026, 11, day, hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun withStore(block: (ConversationHistoryStore) -> Unit) {
        MyObjectBox.builder().directory(temporary.newFolder()).build().use { block(ConversationHistoryStore(it)) }
    }

    private fun ConversationHistoryStore.add(session: String, user: String, assistant: String, ts: Long, axis: Int? = null): Long {
        val id = append(session, user, assistant, ts)!!
        if (axis != null) setEmbedding(id, vector(axis))
        return id
    }

    private fun search(
        store: ConversationHistoryStore, query: String, range: DateRange = DateRange.ALL, queryAxis: Int? = null
    ): String = runBlocking {
        ConversationHistorySearch(store, { if (queryAxis == null) throw IOException("offline") else vector(queryAxis) }, ZoneOffset.UTC)
            .search(query, range)
    }

    @Test fun `keyword search finds an exact name without any embedding`() = withStore { store ->
        store.add("a", "recommend a restaurant", "Try Spice Garden near the station", at(3))
        store.add("a", "what is the weather", "It is sunny", at(3, 13))
        val result = search(store, "restaurant naam Spice Garden")
        assertTrue(result, "Spice Garden" in result)
        assertEquals("[2026-11-03 12:00] You: recommend a restaurant", result.lines().first())
    }

    @Test fun `vector search finds a paraphrase that shares no words`() = withStore { store ->
        store.add("a", "dentist appointment tomorrow at ten", "Noted", at(3), axis = 5)
        store.add("b", "play some music", "Playing", at(3, 13), axis = 6)
        val result = search(store, "when do I see the doctor for my teeth", queryAxis = 5)
        assertTrue(result, "dentist" in result)
        assertFalse(result, "music" in result)
    }

    @Test fun `an exchange that matches by keyword and meaning ranks first`() = withStore { store ->
        store.add("a", "my car number is 4821", "Saved", at(1), axis = 7)
        store.add("b", "car wash prices", "Fifty rupees", at(2), axis = 8)
        val result = search(store, "car number", queryAxis = 7)
        assertTrue(result, result.indexOf("4821") < result.indexOf("car wash"))
    }

    @Test fun `a date range leaves out other days`() = withStore { store ->
        store.add("a", "plan for monday", "Gym", at(2), axis = 1)
        store.add("b", "plan for friday", "Movie", at(6), axis = 1)
        val friday = DateRange.parse("2026-11-06", "2026-11-06", ZoneOffset.UTC)
        val result = search(store, "plan", friday, queryAxis = 1)
        assertTrue(result, "friday" in result)
        assertFalse(result, "monday" in result)
    }

    @Test fun `neighbouring exchanges of the same session are included`() = withStore { store ->
        store.add("a", "tell me about the trip", "Which city?", at(3, 10))
        store.add("a", "Jaipur in December", "Great, five days?", at(3, 11))
        store.add("a", "yes five days", "Booked the plan", at(3, 12))
        store.add("a", "unrelated later", "ok", at(3, 15))
        store.add("b", "Jaipur hotel", "Other session", at(4))
        val result = search(store, "Jaipur December")
        assertTrue(result, "tell me about the trip" in result)
        assertTrue(result, "yes five days" in result)
        assertFalse(result, "unrelated later" in result)
    }

    @Test fun `newer exchange wins a tie`() = withStore { store ->
        store.add("a", "favourite colour is blue", "Noted", at(1))
        store.add("b", "favourite colour is green", "Noted", at(5))
        val result = search(store, "favourite colour")
        assertTrue(result, result.indexOf("green") < result.indexOf("blue"))
    }

    @Test fun `a word in most exchanges is ignored`() = withStore { store ->
        repeat(25) { store.add("s$it", "kya haal hai number $it", "Theek", at(1) + it) }
        store.add("rare", "kya time hua", "Ten", at(2))
        val result = search(store, "kya")
        assertEquals("", result)
    }

    @Test fun `nothing found gives an empty result`() = withStore { store ->
        assertEquals("", search(store, "anything"))
        store.add("a", "hello", "hi", at(1))
        assertEquals("", search(store, "zebra"))
    }

    @Test fun `Devanagari text is found`() = withStore { store ->
        store.add("a", "मेरी दवाई रात को आठ बजे है", "ठीक है", at(3))
        store.add("a", "mausam kaisa hai", "dhoop", at(3, 13))
        assertTrue(search(store, "दवाई कब लेनी है").contains("दवाई"))
    }

    @Test fun `result is capped`() = withStore { store ->
        val filler = "word ".repeat(300)
        repeat(60) { store.add("other$it", "something else $it", "ok", at(1) + it) }
        repeat(12) { store.add("s$it", "topic alpha $filler", "reply $filler", at(2) + it) }
        val result = search(store, "alpha")
        assertTrue(result.length.toString(), result.length <= ConversationHistorySearch.MAX_RESULT_CHARS + 1_700)
        assertTrue(result.isNotEmpty())
    }

    @Test fun `date range parsing is inclusive and strict`() {
        val range = DateRange.parse("2026-11-03", "2026-11-04", ZoneOffset.UTC)
        assertTrue(at(3, 0) in range)
        assertTrue(LocalDateTime.of(2026, 11, 4, 23, 59).toInstant(ZoneOffset.UTC).toEpochMilli() in range)
        assertFalse(at(5, 0) in range)
        assertFalse(at(2, 23) in range)
        assertTrue(DateRange.parse(null, "  ", ZoneOffset.UTC).isAll)
        assertThrows(IllegalArgumentException::class.java) { DateRange.parse("03-11-2026", null, ZoneOffset.UTC) }
        assertThrows(IllegalArgumentException::class.java) { DateRange.parse("2026-11-05", "2026-11-01", ZoneOffset.UTC) }
        assertNull(DateRange.parse("2026-11-03", null, ZoneOffset.UTC).toMillis)
    }
}
