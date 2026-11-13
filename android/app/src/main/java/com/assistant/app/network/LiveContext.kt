package com.assistant.app.network

/** How full the live model's context window is, for the ring around the mic button. */
object LiveContext {
    /** The live model's context window, in tokens. */
    const val LIMIT_TOKENS = 65_536

    /**
     * The server is asked to drop the oldest turns once the session reaches this share of the window.
     * The model gets slower and starts to make things up when the context is more than about half full,
     * so the session is kept at 60% of the window and compacted when it gets there.
     */
    const val COMPRESS_AT_TOKENS = (LIMIT_TOKENS * 6) / 10

    enum class Level { NORMAL, WARNING, CRITICAL }

    /** 0..1 share of the window in use. */
    fun fraction(tokens: Int): Float = (tokens.toFloat() / LIMIT_TOKENS).coerceIn(0f, 1f)

    /** Levels are relative to the 60% compaction point, which the session never goes past. */
    fun level(fraction: Float): Level = when {
        fraction >= 0.54f -> Level.CRITICAL
        fraction >= 0.42f -> Level.WARNING
        else -> Level.NORMAL
    }

    /** "41k / 65k" */
    fun label(tokens: Int): String = "${tokens / 1000}k / ${LIMIT_TOKENS / 1000}k"
}
