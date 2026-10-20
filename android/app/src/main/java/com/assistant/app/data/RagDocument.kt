package com.assistant.app.data

import com.assistant.app.network.GeminiEmbeddingClient

/** One user-managed knowledge document (added/edited/deleted from Settings). */
data class RagDocument(
    val id: String,
    val title: String,
    val text: String,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

/** One chunk of a [RagDocument], with its embedding vector, as actually searched at query time.
 *  [embeddingModel] is retained only to detect legacy stored vectors and re-index them into the
 *  app's one fixed embedding space.
 *  [heading] is the nearest Markdown heading ancestry path (e.g. "Delhi > Fees") above this
 *  chunk's paragraph, if any -- lets a search result be attributed to its section instead of
 *  showing as bare, unlabelled text (see RagSearchEngine.search). Null for chunks saved before
 *  this field existed, or a paragraph with no heading above it. */
data class RagChunk(
    val docId: String,
    val chunkIndex: Int,
    val text: String,
    val embedding: FloatArray,
    val embeddingModel: String = GeminiEmbeddingClient.MODEL,
    val heading: String? = null
)
