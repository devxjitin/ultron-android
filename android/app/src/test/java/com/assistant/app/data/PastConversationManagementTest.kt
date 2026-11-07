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

/** The Memory page's Past conversations list: deleting and clearing, and the size estimate. */
class PastConversationManagementTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun at(day: Int, hour: Int = 12, minute: Int = 0) =
        LocalDateTime.of(2026, 11, day, hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun withStore(block: (ConversationHistoryStore) -> Unit) {
        MyObjectBox.builder().directory(temporary.newFolder()).build().use { block(ConversationHistoryStore(it)) }
    }

    @Test fun `delete removes one exchange`() = withStore { store ->
        val first = store.append("s", "one", "a", at(1))!!
        val second = store.append("s", "two", "b", at(2))!!
        assertTrue(store.delete(first))
        assertFalse(store.delete(first))
        assertEquals(listOf(second), store.recent(10).map { it.id })
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
