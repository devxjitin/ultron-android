package com.assistant.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/** The Memory page's Past conversations list: grouping by day, deleting and clearing, and the size estimate. */
class PastConversationManagementTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun at(day: Int, hour: Int = 12, minute: Int = 0) =
        LocalDateTime.of(2026, 11, day, hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun withStore(block: (ConversationHistoryStore) -> Unit) {
        MyObjectBox.builder().directory(temporary.newFolder()).build().use { block(ConversationHistoryStore(it)) }
    }

    private fun turn(id: Long, ts: Long) = StoredTurn(id, "s", "q$id", "a$id", ts, embedded = false)

    @Test fun `exchanges are grouped by local day, days newest first and turns in order`() {
        val newestFirst = listOf(turn(4, at(5, 18)), turn(3, at(5, 9)), turn(2, at(3, 23, 59)), turn(1, at(3, 0, 1)))
        val days = groupByDay(newestFirst, ZoneOffset.UTC)
        assertEquals(listOf(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 3)), days.map { it.date })
        assertEquals(listOf(3L, 4L), days[0].turns.map { it.id })
        assertEquals(listOf(1L, 2L), days[1].turns.map { it.id })
    }

    @Test fun `a day group covers exactly that day`() {
        val day = groupByDay(listOf(turn(1, at(3, 12))), ZoneOffset.UTC).single()
        assertEquals(at(3, 0), day.fromMillis)
        assertEquals(at(4, 0) - 1, day.toMillis)
    }

    @Test fun `an empty list gives no days`() {
        assertTrue(groupByDay(emptyList(), ZoneOffset.UTC).isEmpty())
    }

    @Test fun `a time zone moves the day boundary`() {
        val lateEvening = turn(1, at(3, 22))
        val plusFive = ZoneOffset.ofHours(5)
        assertEquals(LocalDate.of(2026, 11, 3), groupByDay(listOf(lateEvening), ZoneOffset.UTC).single().date)
        assertEquals(LocalDate.of(2026, 11, 4), groupByDay(listOf(lateEvening), plusFive).single().date)
    }

    @Test fun `delete removes one exchange`() = withStore { store ->
        val first = store.append("s", "one", "a", at(1))!!
        val second = store.append("s", "two", "b", at(2))!!
        assertTrue(store.delete(first))
        assertFalse(store.delete(first))
        assertEquals(listOf(second), store.recent(10).map { it.id })
    }

    @Test fun `deleting a day removes only that day`() = withStore { store ->
        store.append("a", "mon 1", "x", at(3, 8))
        store.append("a", "mon 2", "x", at(3, 22))
        store.append("b", "tue", "x", at(4, 0, 0))
        store.append("b", "sun", "x", at(2, 23, 59))
        val removed = store.deleteRange(at(3, 0), at(4, 0) - 1)
        assertEquals(2, removed)
        assertEquals(listOf("sun", "tue"), store.recent(10).map { it.userText })
    }

    @Test fun `clear all empties the memory and it still works after`() = withStore { store ->
        repeat(3) { store.append("s", "q$it", "a", at(1) + it) }
        store.clearAll()
        assertEquals(0L, store.count())
        assertTrue(store.recent(10).isEmpty())
        assertTrue(store.pendingEmbedding(10).isEmpty())
        store.append("s", "again", "ok", at(2))
        assertEquals(1L, store.count())
    }

    @Test fun `a deleted exchange is no longer found by search`() = withStore { store ->
        val id = store.append("s", "my locker code is 4821", "Saved", at(1))!!
        store.delete(id)
        assertEquals(0L, store.termCount("locker", null, null))
    }

    @Test fun `size estimate grows with exchanges and more with embeddings`() = withStore { store ->
        assertEquals(0L, store.approxBytes())
        val a = store.append("s", "one", "a", at(1))!!
        store.append("s", "two", "b", at(2))
        val withoutEmbeddings = store.approxBytes()
        assertTrue(withoutEmbeddings > 0)
        store.setEmbedding(a, FloatArray(768).also { it[0] = 1f })
        assertTrue(store.approxBytes() > withoutEmbeddings)
    }
}
