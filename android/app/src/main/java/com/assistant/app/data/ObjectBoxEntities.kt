package com.assistant.app.data

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
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
