package com.assistant.app.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

/** Runs against a real on-disk ObjectBox store, like the knowledge index tests. */
class ConversationHistoryStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun vector(axis: Int) = FloatArray(768).also { it[axis] = 1f }

    private fun <T> withStore(block: (ConversationHistoryStore) -> T): T =
        MyObjectBox.builder().directory(temporary.newFolder()).build().use { block(ConversationHistoryStore(it)) }

    @Test fun `noise with no real user text is not saved`() = withStore { store ->
        assertNull(store.append("s", "", "hello", 1L))
        assertNull(store.append("s", " a ", "hello", 2L))
        assertEquals(0L, store.count())
        assertNotNull(store.append("s", "hi", "hello", 3L))
        assertEquals(1L, store.count())
    }

    @Test fun `recent returns the newest turns oldest first`() = withStore { store ->
        (1..5).forEach { store.append("s", "question $it", "answer $it", it * 1_000L) }
        assertEquals(listOf("question 3", "question 4", "question 5"), store.recent(3).map { it.userText })
        assertTrue(store.recent(0).isEmpty())
        assertEquals(5, store.recent(50).size)
    }

    @Test fun `turns are kept however old they are`() = withStore { store ->
        store.append("old", "long ago", "yes", 1L)
        store.append("new", "today", "yes", System.currentTimeMillis())
        assertEquals(listOf("long ago", "today"), store.recent(10).map { it.userText })
    }

    @Test fun `a turn is pending until it is embedded`() = withStore { store ->
        val first = store.append("s", "one", "a", 1L)!!
        val second = store.append("s", "two", "b", 2L)!!
        assertEquals(listOf(first, second), store.pendingEmbedding(10).map { it.id })

        assertTrue(store.setEmbedding(first, vector(0)))
        assertEquals(listOf(second), store.pendingEmbedding(10).map { it.id })
        assertTrue(store.get(first)!!.embedded)
        assertFalse(store.get(second)!!.embedded)
    }

    @Test fun `embedding a deleted turn is a no-op`() = withStore { store ->
        assertFalse(store.setEmbedding(12345L, vector(1)))
    }

    @Test fun `indexer embeds every pending turn`() = withStore { store ->
        store.append("s", "weather today", "sunny", 1L)
        store.append("s", "set alarm", "done", 2L)
        val seen = mutableListOf<String>()
        val done = runBlocking {
            ConversationHistoryIndexer(store) { text -> seen += text; vector(2) }.indexPending()
        }
        assertEquals(2, done)
        assertEquals(listOf("You: weather today\nUltron: sunny", "You: set alarm\nUltron: done"), seen)
        assertTrue(store.pendingEmbedding(10).isEmpty())
    }

    @Test fun `indexer stops on a failure and leaves the rest pending`() = withStore { store ->
        store.append("s", "one", "a", 1L)
        store.append("s", "two", "b", 2L)
        store.append("s", "three", "c", 3L)
        var calls = 0
        val done = runBlocking {
            ConversationHistoryIndexer(store) {
                if (++calls == 2) throw IOException("offline")
                vector(3)
            }.indexPending()
        }
        assertEquals(1, done)
        assertEquals(listOf("two", "three"), store.pendingEmbedding(10).map { it.userText })
    }

    @Test fun `embedding text names both sides and is capped`() {
        assertEquals("You: hi", ConversationHistoryStore.embeddingText(" hi ", " "))
        val long = ConversationHistoryStore.embeddingText("x".repeat(5_000), "y")
        assertEquals(ConversationHistoryStore.MAX_TURN_CHARS, long.length)
    }
}
