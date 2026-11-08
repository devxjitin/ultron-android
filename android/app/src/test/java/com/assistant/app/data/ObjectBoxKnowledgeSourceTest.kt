package com.assistant.app.data

import com.assistant.app.network.GeminiEmbeddingClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** ObjectBox as the only copy of the knowledge: every change is one checked disk transaction. */
class ObjectBoxKnowledgeSourceTest {
    @get:Rule val temporary = TemporaryFolder()
    private val one = RagDocument("one", "One", "First text", 1L)
    private val two = RagDocument("two", "Two", "Second text", 2L)
    private fun vector(axis: Int) = FloatArray(768).also { it[axis] = 1f }
    private fun chunk(doc: RagDocument, axis: Int) =
        RagChunk(doc.id, 0, doc.text, vector(axis), GeminiEmbeddingClient.MODEL, "Heading")

    @Test fun `changes survive a restart and the revision counts up`() = runBlocking {
        val directory = temporary.newFolder("restart")
        val saved = mutableListOf<Long>()
        MyObjectBox.builder().directory(directory).build().use { store ->
            val source = ObjectBoxKnowledgeSource(ObjectBoxKnowledgeIndex(store), 5L) { saved += it }
            val coordinator = source.coordinator()
            coordinator.mutate { it.copy(documents = it.documents + one, chunks = it.chunks + chunk(one, 0)) }
            coordinator.mutate { it.copy(documents = it.documents + two, chunks = it.chunks + chunk(two, 1)) }
            assertEquals(listOf(6L, 7L), saved)
            val snapshot = coordinator.snapshot()
            assertEquals(7L, snapshot.revision)
            assertEquals(listOf("one", "two"), snapshot.documents.map { it.id })
        }
        MyObjectBox.builder().directory(directory).build().use { store ->
            val index = ObjectBoxKnowledgeIndex(store)
            val snapshot = ObjectBoxKnowledgeSource(index, 7L) { }.coordinator().snapshot()
            assertEquals(listOf("one", "two"), snapshot.documents.map { it.id })
            assertEquals(2, snapshot.chunks.size)
            assertEquals("two", index.nearest(vector(1), 1).single().docId)
        }
    }

    @Test fun `a rejected change leaves the stored data and revision untouched`() = runBlocking {
        val directory = temporary.newFolder("rollback")
        val saved = mutableListOf<Long>()
        MyObjectBox.builder().directory(directory).build().use { store ->
            val coordinator = ObjectBoxKnowledgeSource(ObjectBoxKnowledgeIndex(store), 0L) { saved += it }.coordinator()
            coordinator.mutate { it.copy(documents = listOf(one), chunks = listOf(chunk(one, 0))) }
            try {
                // Two chunks with the same position are a duplicate, which verification refuses.
                coordinator.mutate { it.copy(documents = listOf(one, two), chunks = listOf(chunk(two, 1), chunk(two, 1))) }
                fail("Expected the duplicate chunk to be refused")
            } catch (_: IllegalStateException) { }
            val snapshot = coordinator.snapshot()
            assertEquals(1L, snapshot.revision)
            assertEquals(listOf("one"), snapshot.documents.map { it.id })
            assertEquals(listOf(1L), saved)
        }
        MyObjectBox.builder().directory(directory).build().use { store ->
            val snapshot = ObjectBoxKnowledgeSource(ObjectBoxKnowledgeIndex(store), 1L) { }.coordinator().snapshot()
            assertEquals(listOf("one"), snapshot.documents.map { it.id })
            assertEquals(1, snapshot.chunks.size)
        }
    }

    @Test fun `failing to save the revision does not fail the change`() = runBlocking {
        val directory = temporary.newFolder("persist")
        MyObjectBox.builder().directory(directory).build().use { store ->
            val coordinator = ObjectBoxKnowledgeSource(ObjectBoxKnowledgeIndex(store), 0L) { error("disk full") }.coordinator()
            coordinator.mutate { it.copy(documents = listOf(one), chunks = listOf(chunk(one, 0))) }
            assertEquals(1L, coordinator.snapshot().revision)
            assertEquals(listOf("one"), coordinator.snapshot().documents.map { it.id })
        }
    }

    @Test fun `delete and clear remove every record`() = runBlocking {
        val directory = temporary.newFolder("delete")
        MyObjectBox.builder().directory(directory).build().use { store ->
            val index = ObjectBoxKnowledgeIndex(store)
            val coordinator = ObjectBoxKnowledgeSource(index, 0L) { }.coordinator()
            coordinator.mutate { it.copy(documents = listOf(one, two), chunks = listOf(chunk(one, 0), chunk(two, 1))) }
            coordinator.mutate { current ->
                current.copy(documents = current.documents.filterNot { it.id == "one" },
                    chunks = current.chunks.filterNot { it.docId == "one" })
            }
            assertEquals(listOf("two"), coordinator.snapshot().documents.map { it.id })
            assertTrue(index.nearest(vector(0), 5).isEmpty())
            coordinator.mutate { it.copy(documents = emptyList(), chunks = emptyList()) }
            assertTrue(coordinator.snapshot().documents.isEmpty())
            assertTrue(index.nearest(vector(1), 5).isEmpty())
        }
    }

    @Test fun `reconcile after a change does nothing more`() = runBlocking {
        val directory = temporary.newFolder("reconcile")
        MyObjectBox.builder().directory(directory).build().use { store ->
            val coordinator = ObjectBoxKnowledgeSource(ObjectBoxKnowledgeIndex(store), 0L) { }.coordinator()
            coordinator.mutate { it.copy(documents = listOf(one), chunks = listOf(chunk(one, 0))) }
            val before = coordinator.snapshot()
            assertSame(before.documents, coordinator.reconcile().documents)
        }
    }
}
