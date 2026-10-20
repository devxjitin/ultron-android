package com.assistant.app.data

import com.assistant.app.network.GeminiEmbeddingClient
import io.objectbox.BoxStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Exercises the production adapter with real 768-dimensional HNSW and disk transactions. */
class KnowledgeIndexNativeTest {
    @get:Rule val temporary = TemporaryFolder()
    private val document = RagDocument("one", "Title", "Source text", 10L)
    private fun vector(axis: Int) = FloatArray(768).also { it[axis] = 1f }
    private fun chunk(document: RagDocument, axis: Int, model: String = GeminiEmbeddingClient.MODEL) =
        RagChunk(document.id, 0, document.text, vector(axis), model, "Section")

    private fun BoxStore.verify(source: KnowledgeSnapshot) {
        verifyKnowledgeIndex(source,
            boxFor(KnowledgeDocumentEntity::class.java).all.map {
                RagDocument(it.externalId, it.title, it.text, it.updatedAtMillis)
            },
            boxFor(KnowledgeChunkEntity::class.java).all.map {
                RagChunk(it.documentExternalId, it.chunkIndex, it.text, requireNotNull(it.embedding),
                    it.embeddingModel, it.heading)
            })
    }

    @Test fun `native reindex changes nearest result model and vector after reopen`() {
        val other = RagDocument("two", "Other", "Other text", 20L)
        val directory = temporary.newFolder("reindex")
        val old = KnowledgeSnapshot(0L, listOf(document, other),
            listOf(chunk(document, 0, "old-model"), chunk(other, 1, "old-model")))
        val current = old.copy(revision = 1L,
            chunks = listOf(chunk(document, 1), chunk(other, 0)))
        MyObjectBox.builder().directory(directory).build().use { store ->
            val index = ObjectBoxKnowledgeIndex(store)
            index.replace(old)
            assertEquals(document.id, index.nearest(vector(0), 1).single().docId)
            index.replace(current)
            store.verify(current)
            assertEquals(other.id, index.nearest(vector(0), 1).single().docId)
        }
        MyObjectBox.builder().directory(directory).build().use { store ->
            store.verify(current)
            val result = ObjectBoxKnowledgeIndex(store).nearest(vector(0), 1).single()
            assertEquals(other.id, result.docId)
            assertEquals(GeminiEmbeddingClient.MODEL, result.embeddingModel)
            assertArrayEquals(vector(0), result.embedding, 0f)
        }
    }

    @Test fun `nearest drops chunks beyond the relevance distance`() {
        val directory = temporary.newFolder("threshold")
        val source = KnowledgeSnapshot(0L, listOf(document), listOf(chunk(document, 1)))
        MyObjectBox.builder().directory(directory).build().use { store ->
            val index = ObjectBoxKnowledgeIndex(store)
            index.replace(source)
            // Orthogonal vectors have cosine distance 1.0, well beyond the default threshold.
            assertTrue(index.nearest(vector(0), 5).isEmpty())
            assertEquals(1, index.nearest(vector(1), 5).size)
            assertEquals(1, index.nearest(vector(0), 5, maxDistance = 2.0).size)
        }
    }

    @Test fun `verification failure rolls back cleared records and vectors on disk`() {
        val directory = temporary.newFolder("rollback")
        val original = KnowledgeSnapshot(0L, listOf(document), listOf(chunk(document, 0)))
        val changed = document.copy(text = "New text", updatedAtMillis = 11L)
        val invalid = KnowledgeSnapshot(1L, listOf(changed),
            listOf(chunk(changed, 1), chunk(changed, 1)))
        MyObjectBox.builder().directory(directory).build().use { store ->
            val index = ObjectBoxKnowledgeIndex(store)
            index.replace(original)
            try {
                index.replace(invalid)
                fail("Expected duplicate-source verification failure")
            } catch (_: IllegalStateException) { }
            store.verify(original)
            assertEquals(document.text, index.nearest(vector(0), 1).single().text)
        }
        MyObjectBox.builder().directory(directory).build().use { it.verify(original) }
    }

    @Test fun `source commit before index interruption recovers on database restart`() = runBlocking {
        val directory = temporary.newFolder("restart")
        var source = KnowledgeSnapshot(0L, listOf(document), listOf(chunk(document, 0)))
        var indexedRevision: Long? = null
        var version = 0
        var failIndex = false
        fun coordinator(index: ObjectBoxKnowledgeIndex) = KnowledgeIndexCoordinator(
            readState = { KnowledgeIndexState(source, indexedRevision, version) },
            commitSource = { source = it(source) },
            replaceIndex = {
                if (failIndex) error("Injected interruption before index transaction")
                index.replace(it)
            },
            markIndexed = { indexedRevision = it.revision; version = KNOWLEDGE_INDEX_RECONCILIATION_VERSION },
            invalidateCache = {}
        )
        val changed = document.copy(title = "Updated title", text = "Updated text", updatedAtMillis = 11L)
        MyObjectBox.builder().directory(directory).build().use { store ->
            val coordinator = coordinator(ObjectBoxKnowledgeIndex(store))
            coordinator.reconcile()
            failIndex = true
            try {
                coordinator.mutate { it.copy(documents = listOf(changed), chunks = listOf(chunk(changed, 1))) }
                fail("Expected interrupted index write")
            } catch (_: IllegalStateException) { }
            assertEquals(1L, source.revision)
            assertEquals(0L, indexedRevision)
            assertEquals(document.text, ObjectBoxKnowledgeIndex(store).nearest(vector(0), 1).single().text)
        }
        failIndex = false
        MyObjectBox.builder().directory(directory).build().use { store ->
            val index = ObjectBoxKnowledgeIndex(store)
            coordinator(index).reconcile()
            store.verify(source)
            assertEquals(source.revision, indexedRevision)
            assertEquals(changed.text, index.nearest(vector(1), 1).single().text)
        }
    }

    @Test fun `delete removes searchable records and orphan documents permanently`() {
        val directory = temporary.newFolder("delete")
        MyObjectBox.builder().directory(directory).build().use { store ->
            val index = ObjectBoxKnowledgeIndex(store)
            index.replace(KnowledgeSnapshot(0L, listOf(document), listOf(chunk(document, 0))))
            index.replace(KnowledgeSnapshot(1L, emptyList(), emptyList()))
            assertTrue(index.nearest(vector(0), 5).isEmpty())
            assertEquals(0L, store.boxFor(KnowledgeDocumentEntity::class.java).count())
            assertEquals(0L, store.boxFor(KnowledgeChunkEntity::class.java).count())
        }
        MyObjectBox.builder().directory(directory).build().use { store ->
            assertTrue(ObjectBoxKnowledgeIndex(store).nearest(vector(0), 5).isEmpty())
        }
    }
}
