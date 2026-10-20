package com.assistant.app.data

import com.assistant.app.network.GeminiEmbeddingClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class KnowledgeIndexConsistencyTest {
    private val document = RagDocument("one", "Source", "Source text", 1L)
    private val second = RagDocument("two", "Other", "Other text", 2L)
    private fun chunk(doc: RagDocument, model: String = GeminiEmbeddingClient.MODEL, value: Float = 1f) =
        RagChunk(doc.id, 0, doc.text, floatArrayOf(value), model, "Heading")
    private fun snapshot(docs: List<RagDocument>, chunks: List<RagChunk>, revision: Long = 0L) =
        KnowledgeSnapshot(revision, docs, chunks)

    /** Durable state survives coordinator recreation; native transactions are simulated here. */
    private class Persistence(var source: KnowledgeSnapshot) {
        var indexed: KnowledgeSnapshot = KnowledgeSnapshot(-1L, emptyList(), emptyList())
        var indexedRevision: Long? = null
        var version = 0
        var failSource = false
        var failIndex = false
        var failMarker = false
        var cancelAfterSource = false
        var corrupt: ((KnowledgeSnapshot) -> KnowledgeSnapshot)? = null
        var replacements = 0
        var cached: List<RagChunk>? = source.chunks

        fun coordinator() = KnowledgeIndexCoordinator(
            readState = { KnowledgeIndexState(source, indexedRevision, version) },
            commitSource = { transform ->
                if (failSource) error("Injected source failure")
                source = transform(source)
                if (cancelAfterSource) throw CancellationException("Cancelled after durable commit")
            },
            replaceIndex = { requested ->
                val previous = indexed
                try {
                    // Failure after clearing records must roll back the entire replacement.
                    indexed = requested.copy(documents = emptyList(), chunks = emptyList())
                    if (failIndex) error("Injected index failure")
                    indexed = corrupt?.invoke(requested) ?: requested
                    verifyKnowledgeIndex(requested, indexed.documents, indexed.chunks)
                    replacements++
                } catch (error: Exception) {
                    indexed = previous
                    throw error
                }
            },
            markIndexed = { requested ->
                if (failMarker) error("Injected marker failure")
                check(source.revision == requested.revision)
                indexedRevision = requested.revision
                version = KNOWLEDGE_INDEX_RECONCILIATION_VERSION
            },
            invalidateCache = { cached = null }
        )
    }

    private suspend fun expectFailure(action: suspend () -> Unit) {
        try {
            action()
            fail("Expected failure")
        } catch (_: IllegalStateException) { }
    }

    @Test fun `old migration marker cannot skip initial full reconciliation`() = runBlocking {
        val persistence = Persistence(snapshot(listOf(document), listOf(chunk(document))))
        persistence.indexedRevision = 0L
        persistence.indexed = snapshot(listOf(second), listOf(chunk(second)))
        val result = persistence.coordinator().reconcile()
        verifyKnowledgeIndex(result, persistence.indexed.documents, persistence.indexed.chunks)
        assertEquals(KNOWLEDGE_INDEX_RECONCILIATION_VERSION, persistence.version)
        assertEquals(1, persistence.replacements)
        persistence.coordinator().reconcile()
        assertEquals(1, persistence.replacements)
    }

    @Test fun `add update and delete index failures block results and recover after restart`() = runBlocking {
        val initial = snapshot(listOf(document), listOf(chunk(document)))
        val updated = document.copy(title = "Edited title", text = "Edited text", updatedAtMillis = 3L)
        val transforms: List<(KnowledgeSnapshot) -> KnowledgeSnapshot> = listOf(
            { it.copy(documents = it.documents + second, chunks = it.chunks + chunk(second)) },
            { it.copy(documents = listOf(updated), chunks = listOf(chunk(updated, value = 2f))) },
            { it.copy(documents = emptyList(), chunks = emptyList()) }
        )
        transforms.forEach { transform ->
            val persistence = Persistence(initial)
            val coordinator = persistence.coordinator()
            coordinator.reconcile()
            val previousIndex = persistence.indexed
            persistence.failIndex = true
            expectFailure { coordinator.mutate(transform) }
            assertEquals(1L, persistence.source.revision)
            assertEquals(0L, persistence.indexedRevision)
            assertSame(previousIndex, persistence.indexed)
            assertNull(persistence.cached)
            expectFailure { persistence.coordinator().reconcile() }
            persistence.failIndex = false
            val recovered = persistence.coordinator().reconcile()
            verifyKnowledgeIndex(recovered, persistence.indexed.documents, persistence.indexed.chunks)
            assertEquals(1L, persistence.indexedRevision)
        }
    }

    @Test fun `source write failure preserves source index and revision`() = runBlocking {
        val initial = snapshot(listOf(document), listOf(chunk(document)))
        val persistence = Persistence(initial)
        val coordinator = persistence.coordinator()
        coordinator.reconcile()
        val previousIndex = persistence.indexed
        persistence.failSource = true
        expectFailure { coordinator.mutate { it.copy(documents = emptyList(), chunks = emptyList()) } }
        assertSame(initial, persistence.source)
        assertSame(previousIndex, persistence.indexed)
        assertEquals(0L, persistence.indexedRevision)
    }

    @Test fun `marker failure repeats complete replacement without duplicates`() = runBlocking {
        val persistence = Persistence(snapshot(listOf(document), listOf(chunk(document))))
        persistence.failMarker = true
        expectFailure { persistence.coordinator().reconcile() }
        assertNull(persistence.indexedRevision)
        assertEquals(0, persistence.version)
        persistence.failMarker = false
        persistence.coordinator().reconcile()
        assertEquals(2, persistence.replacements)
        assertEquals(1, persistence.indexed.documents.size)
        assertEquals(1, persistence.indexed.chunks.size)
    }

    @Test fun `cancellation after source commit invalidates cache and leaves recoverable revision`() = runBlocking {
        val persistence = Persistence(snapshot(listOf(document), listOf(chunk(document))))
        val coordinator = persistence.coordinator()
        coordinator.reconcile()
        persistence.cached = persistence.source.chunks
        persistence.cancelAfterSource = true
        try {
            coordinator.mutate { it.copy(documents = emptyList(), chunks = emptyList()) }
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
        assertNull(persistence.cached)
        assertEquals(1L, persistence.source.revision)
        assertEquals(0L, persistence.indexedRevision)
        persistence.cancelAfterSource = false
        persistence.coordinator().reconcile()
        assertTrue(persistence.indexed.chunks.isEmpty())
    }

    @Test fun `reindex updates queried model vectors content and preserves source timestamps`() = runBlocking {
        val old = chunk(document, "gemini-embedding-001", 9f)
        val persistence = Persistence(snapshot(listOf(document), listOf(old)))
        val coordinator = persistence.coordinator()
        coordinator.reconcile()
        val prepared = listOf(document to listOf(chunk(document, value = 2f)))
        coordinator.mutate { applyReindexedChunks(it, prepared) }
        assertFalse(requiresEmbeddingMigration(persistence.indexed.chunks))
        assertArrayEquals(floatArrayOf(2f), persistence.indexed.chunks.single().embedding, 0f)
        assertEquals(document, persistence.indexed.documents.single())
        assertEquals(persistence.source.revision, persistence.indexedRevision)
    }

    @Test fun `prepared reindex cannot overwrite concurrent edits deletes or new documents`() = runBlocking {
        val changed = document.copy(text = "Concurrent edit", updatedAtMillis = 3L)
        val added = RagDocument("three", "Added", "New content", 4L)
        val current = snapshot(listOf(changed, added), listOf(chunk(changed), chunk(added)), 7L)
        val persistence = Persistence(current)
        val coordinator = persistence.coordinator()
        coordinator.reconcile()
        coordinator.mutate { applyReindexedChunks(it,
            listOf(document to listOf(chunk(document, value = 9f)), second to listOf(chunk(second)))) }
        assertSame(current, persistence.source)
        assertEquals(7L, persistence.source.revision)
        verifyKnowledgeIndex(current, persistence.indexed.documents, persistence.indexed.chunks)
    }

    @Test fun `reindex detects edit and revert with changed timestamp`() {
        val reverted = document.copy(updatedAtMillis = 3L)
        val current = snapshot(listOf(reverted), listOf(chunk(reverted, value = 2f)))
        assertSame(current, applyReindexedChunks(current,
            listOf(document to listOf(chunk(document, value = 9f)))))
    }

    @Test fun `reindex replaces unchanged documents while retaining concurrently changed documents`() {
        val changed = second.copy(text = "New version", updatedAtMillis = 3L)
        val retainedChunk = chunk(changed)
        val current = snapshot(listOf(document, changed), listOf(chunk(document), retainedChunk))
        val replacement = chunk(document, value = 2f)
        val result = applyReindexedChunks(current,
            listOf(document to listOf(replacement), second to listOf(chunk(second, value = 9f))))
        assertEquals(current.documents, result.documents)
        assertEquals(listOf(retainedChunk, replacement), result.chunks)
    }

    @Test fun `same chunk keys with stale model vector heading or content fail verification`() = runBlocking {
        val expected = chunk(document)
        val corruptions = listOf(
            expected.copy(embeddingModel = "old-model"),
            expected.copy(embedding = floatArrayOf(9f)),
            expected.copy(heading = "Wrong heading"),
            expected.copy(text = "Wrong text")
        )
        corruptions.forEach { corrupted ->
            val persistence = Persistence(snapshot(listOf(document), listOf(expected)))
            persistence.corrupt = { it.copy(chunks = listOf(corrupted)) }
            expectFailure { persistence.coordinator().reconcile() }
            assertTrue(persistence.indexed.chunks.isEmpty())
            assertNull(persistence.indexedRevision)
            assertEquals(0, persistence.version)
        }
    }

    @Test fun `missing duplicate orphan and stale document records cannot mark index current`() = runBlocking {
        val initial = snapshot(listOf(document), listOf(chunk(document)))
        val corruptions: List<(KnowledgeSnapshot) -> KnowledgeSnapshot> = listOf(
            { it.copy(chunks = emptyList()) },
            { it.copy(chunks = it.chunks + it.chunks) },
            { it.copy(chunks = it.chunks + chunk(second)) },
            { it.copy(documents = it.documents + second) },
            { it.copy(documents = listOf(document.copy(title = "Stale title"))) },
            { it.copy(documents = it.documents + it.documents) }
        )
        corruptions.forEach { corrupt ->
            val persistence = Persistence(initial)
            persistence.corrupt = corrupt
            expectFailure { persistence.coordinator().reconcile() }
            assertNull(persistence.indexedRevision)
        }
    }

    @Test fun `empty authoritative source removes stale documents and orphan index chunks`() = runBlocking {
        val persistence = Persistence(snapshot(emptyList(), emptyList()))
        persistence.indexed = snapshot(listOf(second), listOf(chunk(second)))
        persistence.coordinator().reconcile()
        assertTrue(persistence.indexed.documents.isEmpty())
        assertTrue(persistence.indexed.chunks.isEmpty())
    }

    @Test fun `authoritative orphan or duplicate records abort replacement`() = runBlocking {
        val invalid = listOf(
            snapshot(listOf(document), listOf(chunk(second))),
            snapshot(listOf(document), listOf(chunk(document), chunk(document))),
            snapshot(listOf(document, document), listOf(chunk(document)))
        )
        invalid.forEach { source ->
            val persistence = Persistence(source)
            expectFailure { persistence.coordinator().reconcile() }
            assertNull(persistence.indexedRevision)
        }
    }

    @Test fun `legacy source can be read for reembedding without requiring old vectors in native index`() = runBlocking {
        val persistence = Persistence(snapshot(listOf(document), listOf(chunk(document, "old-model"))))
        persistence.failIndex = true
        val source = persistence.coordinator().snapshot()
        assertTrue(requiresEmbeddingMigration(source.chunks))
        assertEquals(0, persistence.replacements)
    }
}
