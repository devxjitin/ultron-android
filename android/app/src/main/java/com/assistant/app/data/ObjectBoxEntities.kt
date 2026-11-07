package com.assistant.app.data

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.VectorDistanceType

/** Persistent source document. The external id preserves DataStore-era document references. */
@Entity
data class KnowledgeDocumentEntity(
    @Id var id: Long = 0,
    var externalId: String = "",
    var title: String = "",
    var text: String = "",
    var updatedAtMillis: Long = 0
)

/** One searchable chunk with a 768-dimensional Gemini embedding indexed by HNSW. */
@Entity
data class KnowledgeChunkEntity(
    @Id var id: Long = 0,
    var documentExternalId: String = "",
    var chunkIndex: Int = 0,
    var text: String = "",
    var heading: String? = null,
    var embeddingModel: String = "",
    @HnswIndex(dimensions = 768, distanceType = VectorDistanceType.COSINE)
    var embedding: FloatArray? = null
)

/**
 * One finished exchange from a past conversation, kept on the phone for long-term memory. [embeddingModel] is blank
 * until the exchange has been embedded; until then it can only be found by keyword.
 */
@Entity
data class ConversationTurnEntity(
    @Id var id: Long = 0,
    @Index var sessionId: String = "",
    var userText: String = "",
    var assistantText: String = "",
    @Index var timestampMillis: Long = 0,
    var embeddingModel: String = "",
    @HnswIndex(dimensions = 768, distanceType = VectorDistanceType.COSINE)
    var embedding: FloatArray? = null
)

/** A picture the user added for the assistant to show. The JPEG itself is a file; this row holds what search needs. */
@Entity
data class ImageEntity(
    @Id var id: Long = 0,
    var title: String = "",
    var description: String = "",
    @Index var sha256: String = "",
    var fileName: String = "",
    var sizeBytes: Long = 0,
    @Index var createdAtMillis: Long = 0
)
