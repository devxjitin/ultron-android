package com.assistant.app.viewmodel

import com.assistant.app.data.KnowledgeToolResponse
import com.assistant.app.data.SearchFailureCategory
import com.assistant.app.network.GeminiEmbeddingClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class KnowledgeManagerTest {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Unconfined)
    private var key = "key"
    private var calls = 0
    private var behaviour: suspend (Int) -> List<String> = { listOf("a") }

    private fun manager() = KnowledgeManager(
        scope = scope,
        state = MutableStateFlow(AssistantUiState()),
        storeProvider = { error("store must not be opened by these tests") },
        resolveKey = { key },
        showToast = { _, _ -> },
        auxiliaryUsage = { { _, _ -> } },
        searchOverride = { _, _ -> behaviour(++calls) },
        retryDelayMs = 1
    )

    @After fun tearDown() = job.cancel()

    private fun search(query: String = "hello") = runBlocking { manager().runSearchTool(query, "id1", null) }

    @Test fun `empty question is rejected without searching`() {
        assertEquals(KnowledgeToolResponse.invalidQuery(), search("   "))
        assertEquals(0, calls)
    }

    @Test fun `missing key says search is not set up`() {
        key = ""
        assertEquals(KnowledgeToolResponse.notConfigured(), search())
        assertEquals(0, calls)
    }

    @Test fun `found chunks are returned`() {
        behaviour = { listOf("one", "two") }
        assertEquals(KnowledgeToolResponse.results(listOf("one", "two")), search())
        assertEquals(1, calls)
    }

    @Test fun `a transient failure is retried once`() {
        behaviour = { n -> if (n == 1) throw IOException("blip") else listOf("ok") }
        assertEquals(KnowledgeToolResponse.results(listOf("ok")), search())
        assertEquals(2, calls)
    }

    @Test fun `a transient failure that repeats is reported after one retry`() {
        behaviour = { throw IOException("down") }
        assertEquals(KnowledgeToolResponse.failed(SearchFailureCategory.NETWORK), search())
        assertEquals(2, calls)
    }

    @Test fun `a configuration failure is not retried`() {
        behaviour = { throw GeminiEmbeddingClient.HttpFailure(403, "denied") }
        assertEquals(KnowledgeToolResponse.failed(SearchFailureCategory.CONFIG), search())
        assertEquals(1, calls)
    }

    @Test fun `long questions are cut before searching`() {
        var seen = 0
        val m = KnowledgeManager(scope, MutableStateFlow(AssistantUiState()), { error("no store") }, { key }, { _, _ -> }, { { _, _ -> } },
            searchOverride = { q, _ -> seen = q.length; emptyList() }, retryDelayMs = 1)
        runBlocking { m.runSearchTool("x".repeat(2000), "id", null) }
        assertEquals(KnowledgeManager.MAX_SEARCH_QUERY_CHARS, seen)
    }

    @Test fun `cancellation is passed on and not turned into a result`() {
        behaviour = { throw CancellationException("stop") }
        assertThrows(CancellationException::class.java) { search() }
        assertEquals(1, calls)
    }

    @Test fun `dismissing the error clears the target and stage`() {
        val state = MutableStateFlow(
            AssistantUiState(knowledgeDocumentError = "bad", savingKnowledgeDocumentTarget = "doc1",
                knowledgePreparationStage = KnowledgePreparationStage.PREPARING)
        )
        KnowledgeManager(scope, state, { error("no store") }, { key }, { _, _ -> }, { { _, _ -> } }).dismissDocumentError()
        assertEquals(null, state.value.knowledgeDocumentError)
        assertEquals(null, state.value.savingKnowledgeDocumentTarget)
        assertFalse(state.value.isSavingKnowledgeDocument)
    }
}
