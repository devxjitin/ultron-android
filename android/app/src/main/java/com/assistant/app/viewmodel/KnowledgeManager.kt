package com.assistant.app.viewmodel

import android.util.Log
import com.assistant.app.decisions.ChunkReranker
import com.assistant.app.decisions.DecisionsClient
import com.assistant.app.data.AssistantOptions
import com.assistant.app.data.KnowledgeToolResponse
import com.assistant.app.data.NO_GEMINI_KEY_MESSAGE
import com.assistant.app.data.RagDocumentStore
import com.assistant.app.data.RagSearchEngine
import com.assistant.app.data.SearchFailureCategory
import com.assistant.app.data.UsageStore
import com.assistant.app.data.classifySearchFailure
import com.assistant.app.data.requiresEmbeddingMigration
import com.assistant.app.data.validateFormattedDataset
import com.assistant.app.network.GeminiEmbeddingClient
import com.assistant.app.network.GeminiTextClient
import com.assistant.app.util.LatencyTool
import com.assistant.app.util.UserFacingError
import com.assistant.app.util.UserMessageException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The Knowledge documents and the search over them: adding, importing, editing and deleting documents, keeping the
 * embeddings up to date, and answering the voice model's `search_knowledge_base` tool. Progress and errors go into the
 * same [state] the screen reads.
 *
 * [storeProvider] is only called when the documents are first needed. [searchOverride] replaces the real search in tests.
 */
internal class KnowledgeManager(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<AssistantUiState>,
    storeProvider: () -> RagDocumentStore,
    /** The Knowledge key when one is saved, otherwise the main Gemini key; blank until the user adds one. */
    private val resolveKey: () -> String,
    private val showToast: (String, AppNotificationType) -> Unit,
    /** Counts the tokens a helper request used, under the given purpose in Usage. */
    private val auxiliaryUsage: (String) -> (Int, Int) -> Unit,
    private val searchOverride: (suspend (String, LatencyTool?) -> List<String>)? = null,
    private val retryDelayMs: Long = SEARCH_RETRY_DELAY_MS
) {
    companion object {
        /** One quick retry for transient knowledge-search failures; keeps worst case well under the 15 s model watchdog. */
        const val SEARCH_RETRY_LIMIT = 1
        const val SEARCH_RETRY_DELAY_MS = 500L
        const val MAX_SEARCH_QUERY_CHARS = 500
    }

    private val store: RagDocumentStore by lazy(storeProvider)

    /** Serializes add/update/delete of knowledge documents so two concurrently triggered
     *  operations (e.g. an in-progress edit's Save and a separately launched .md upload) never
     *  overlap and clobber each other's isSavingKnowledgeDocument/progress/error UI state. */
    private val docMutex = Mutex()
    private var documentsJob: Job? = null
    private val migrationRunning = AtomicBoolean(false)

    /** Knowledge import and search use the optional Knowledge key when one is saved, otherwise the main Gemini key. */
    fun embeddingKey(): String = resolveKey()

    private var cachedClient: Pair<String, GeminiEmbeddingClient>? = null
    fun embeddingClient(): GeminiEmbeddingClient {
        val key = resolveKey()
        if (key.isBlank()) throw UserMessageException(NO_GEMINI_KEY_MESSAGE)
        return cachedClient?.takeIf { it.first == key }?.second
            ?: GeminiEmbeddingClient(key).also { cachedClient = key to it }
    }

    private var cachedReranker: Pair<String, ChunkReranker>? = null

    /** The Decisions reranker for the current key, or null while the setting is off or after the key said to slow down. */
    private fun reranker(): ChunkReranker? {
        if (!state.value.settings.decisionsRerankEnabled) return null
        val key = resolveKey().takeIf { it.isNotBlank() } ?: return null
        val cached = cachedReranker?.takeIf { it.first == key }?.second
            ?: ChunkReranker(DecisionsClient(key)).also { cachedReranker = key to it }
        return cached
    }

    private suspend fun searchChunks(query: String, trace: LatencyTool?): List<String> {
        searchOverride?.let { return it(query, trace) }
        val reranker = reranker()
        return RagSearchEngine(
            documentStore = store,
            embeddingClient = embeddingClient(),
            retrievalChunkLimit = state.value.settings.retrievalChunkLimit,
            rerank = reranker?.let { r -> { q, chunks -> r.rerank(q, chunks) } }
        ).search(query, trace)
    }

    /** Re-embeds chunks stored under an older embedding model, in the background and never inside a
     *  live tool call. Until it finishes, searches answer "not ready". Safe to call repeatedly. */
    fun startEmbeddingMigrationIfNeeded() {
        if (!migrationRunning.compareAndSet(false, true)) return
        scope.launch(Dispatchers.IO) {
            try {
                if (embeddingKey().isBlank()) return@launch
                if (!requiresEmbeddingMigration(store.allChunks())) return@launch
                Log.i("AssistantSearch", "Embedding migration started")
                store.reindexAll(embeddingClient())
                Log.i("AssistantSearch", "Embedding migration finished")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("AssistantError", "Background embedding migration failed", e)
            } finally {
                migrationRunning.set(false)
            }
        }
    }

    /** The Knowledge key was removed, so the old client must not be used again. */
    fun forgetEmbeddingClient() {
        cachedClient = null
    }

    /** The Knowledge key was changed: forget the old client and re-embed anything stored under another model. */
    fun onEmbeddingKeyChanged() {
        cachedClient = null
        startEmbeddingMigrationIfNeeded()
    }

    /** A failed flow ends its collection, so this is started again after a knowledge reset. */
    fun collectDocuments() {
        documentsJob?.cancel()
        documentsJob = scope.launch {
            store.documents
                .catch { e ->
                    Log.e("AssistantError", "Failed to read knowledge documents", e)
                    // Decode failures are IllegalStateException; storage I/O errors are not resettable data problems.
                    if (e is IllegalStateException) {
                        state.update { it.copy(knowledgeDocuments = emptyList(), knowledgeStoreCorrupt = true) }
                    }
                }
                .collect { docs ->
                    state.update { it.copy(knowledgeDocuments = docs, knowledgeStoreCorrupt = false) }
                }
        }
    }

    /** Recovery from unreadable knowledge data: deletes all documents and the index. */
    fun resetKnowledge() {
        scope.launch {
            try {
                docMutex.withLock { store.resetKnowledge() }
                collectDocuments()
                showToast("Knowledge reset", AppNotificationType.SUCCESS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("AssistantError", "Failed to reset knowledge", e)
                showToast("Couldn't reset knowledge. Please try again.", AppNotificationType.ERROR)
            }
        }
    }

    fun addDocument(title: String, text: String) {
        if (title.isBlank() || text.isBlank()) return
        scope.launch {
            docMutex.withLock {
                state.update {
                    it.copy(
                        isSavingKnowledgeDocument = true,
                        knowledgePreparationStage = KnowledgePreparationStage.PREPARING,
                        savingKnowledgeDocumentTarget = ADD_DOCUMENT_TARGET_ID,
                        knowledgeDocumentError = null,
                        uploadedChunks = 0,
                        totalChunks = 0
                    )
                }
                try {
                    store.addDocument(title, text, embeddingClient()) { done, total ->
                        state.update { it.copy(uploadedChunks = done, totalChunks = total) }
                    }
                    state.update {
                        it.copy(
                            isSavingKnowledgeDocument = false,
                            knowledgePreparationStage = null,
                            savingKnowledgeDocumentTarget = null,
                            uploadedChunks = 0,
                            totalChunks = 0
                        )
                    }
                    showToast("Document added", AppNotificationType.SUCCESS)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val error = UserFacingError.forThrowable("Knowledge document save", e)
                    showToast(error, AppNotificationType.ERROR)
                    state.update {
                        it.copy(
                            isSavingKnowledgeDocument = false,
                            knowledgePreparationStage = null,
                            uploadedChunks = 0,
                            totalChunks = 0,
                            knowledgeDocumentError = error
                        )
                    }
                }
            }
        }
    }

    /** Imports a visual PDF through Gemini before embedding its structured paragraph output. */
    fun importPdf(title: String, pdfBytes: ByteArray) {
        if (title.isBlank() || pdfBytes.isEmpty()) return
        scope.launch {
            docMutex.withLock {
                state.update {
                    it.copy(
                        isSavingKnowledgeDocument = true,
                        knowledgePreparationStage = KnowledgePreparationStage.ANALYZING,
                        savingKnowledgeDocumentTarget = ADD_DOCUMENT_TARGET_ID,
                        knowledgeDocumentError = null,
                        uploadedChunks = 0,
                        totalChunks = 0
                    )
                }
                try {
                    val embeddingKey = embeddingKey()
                    if (embeddingKey.isBlank()) throw UserMessageException(NO_GEMINI_KEY_MESSAGE)
                    val formattedText = GeminiTextClient(
                        apiKey = embeddingKey,
                        onUsage = auxiliaryUsage(UsageStore.PURPOSE_PDF),
                        documentFormatPrompt = state.value.settings.documentFormatPrompt,
                        documentFormatModel = AssistantOptions.DATASET_MODEL
                    ).formatKnowledgePdf(pdfBytes, state.value.settings.retrievalChunkLimit)
                    if (formattedText.isBlank()) throw UserMessageException("We couldn't read this document. Please try another file.")
                    val dataset = validateFormattedDataset(formattedText)
                    state.update { it.copy(knowledgePreparationStage = KnowledgePreparationStage.PREPARING) }
                    store.addDocument(title, dataset, embeddingClient()) { done, total ->
                        state.update { it.copy(uploadedChunks = done, totalChunks = total) }
                    }
                    state.update {
                        it.copy(
                            isSavingKnowledgeDocument = false,
                            knowledgePreparationStage = null,
                            savingKnowledgeDocumentTarget = null,
                            uploadedChunks = 0,
                            totalChunks = 0
                        )
                    }
                    showToast("Document imported", AppNotificationType.SUCCESS)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val error = UserFacingError.forThrowable("Knowledge document import", e)
                    showToast(error, AppNotificationType.ERROR)
                    state.update {
                        it.copy(
                            isSavingKnowledgeDocument = false,
                            knowledgePreparationStage = null,
                            uploadedChunks = 0,
                            totalChunks = 0,
                            knowledgeDocumentError = error
                        )
                    }
                }
            }
        }
    }

    fun updateDocument(id: String, title: String, text: String) {
        if (title.isBlank() || text.isBlank()) return
        scope.launch {
            docMutex.withLock {
                state.update {
                    it.copy(
                        isSavingKnowledgeDocument = true,
                        knowledgePreparationStage = KnowledgePreparationStage.PREPARING,
                        savingKnowledgeDocumentTarget = id,
                        knowledgeDocumentError = null,
                        uploadedChunks = 0,
                        totalChunks = 0
                    )
                }
                try {
                    val result = store.updateDocument(id, title, text, embeddingClient()) { done, total ->
                        state.update { it.copy(uploadedChunks = done, totalChunks = total) }
                    }
                    state.update {
                        it.copy(
                            isSavingKnowledgeDocument = false,
                            knowledgePreparationStage = null,
                            savingKnowledgeDocumentTarget = null,
                            uploadedChunks = 0,
                            totalChunks = 0
                        )
                    }
                    if (result != null) {
                        showToast("Document updated", AppNotificationType.SUCCESS)
                    } else {
                        showToast("Document was deleted before the update finished", AppNotificationType.SUCCESS)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val error = UserFacingError.forThrowable("Knowledge document update", e)
                    showToast(error, AppNotificationType.ERROR)
                    state.update {
                        it.copy(
                            isSavingKnowledgeDocument = false,
                            knowledgePreparationStage = null,
                            uploadedChunks = 0,
                            totalChunks = 0,
                            knowledgeDocumentError = error
                        )
                    }
                }
            }
        }
    }

    fun deleteDocument(id: String) {
        scope.launch {
            try {
                docMutex.withLock { store.deleteDocument(id) }
                showToast("Document deleted", AppNotificationType.SUCCESS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("AssistantError", "Failed to delete knowledge document", e)
                showToast("Couldn't delete that document. Please try again.", AppNotificationType.ERROR)
            }
        }
    }

    /** The target stays set after a failed save on purpose, so the error is shown under the right card; only this clears it. */
    fun dismissDocumentError() {
        state.update {
            it.copy(
                knowledgeDocumentError = null,
                knowledgePreparationStage = null,
                savingKnowledgeDocumentTarget = null
            )
        }
    }

    /**
     * Runs the `search_knowledge_base` tool for the voice model and returns the text it gets back. A transient failure
     * is tried once more; a missing key or an empty question is answered at once. Cancellation is passed on.
     */
    suspend fun runSearchTool(rawQuery: String, callId: String, trace: LatencyTool?): String = try {
        val query = rawQuery.trim().take(MAX_SEARCH_QUERY_CHARS)
        if (query.isEmpty()) {
            trace?.event("tool_empty_query")
            KnowledgeToolResponse.invalidQuery()
        } else if (embeddingKey().isBlank()) {
            // Not a failed lookup and not proof of absence: search just isn't configured.
            trace?.event("tool_not_configured")
            KnowledgeToolResponse.notConfigured()
        } else {
            var attempt = 0
            var outcome: String? = null
            while (outcome == null) {
                try {
                    val chunks = searchChunks(query, trace)
                    outcome = KnowledgeToolResponse.results(chunks)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val category = classifySearchFailure(e)
                    if (category == SearchFailureCategory.REINDEX_REQUIRED) {
                        startEmbeddingMigrationIfNeeded()
                    }
                    // Ids and categories only -- never the query, documents or keys.
                    Log.w("AssistantSearch", "search failed id=$callId category=$category " +
                        "error=${e.javaClass.simpleName} attempt=${attempt + 1}")
                    if (category.transient && attempt < SEARCH_RETRY_LIMIT) {
                        attempt++
                        trace?.event("tool_retry", mapOf("category" to category.name))
                        delay(retryDelayMs)
                    } else {
                        trace?.failure()
                        trace?.event("tool_failed", mapOf("category" to category.name))
                        outcome = KnowledgeToolResponse.failed(category)
                    }
                }
            }
            checkNotNull(outcome)
        }
    } catch (e: CancellationException) {
        trace?.event("tool_canceled")
        throw e
    }
}
