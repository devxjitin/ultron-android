package com.assistant.app.data

/** Shown when a feature needs Gemini and the user has not saved a key. The app has no built-in Gemini access. */
const val NO_GEMINI_KEY_MESSAGE = "Add your Gemini API key in Settings first."

/**
 * The key for knowledge features (PDF formatting, embeddings, search): the optional Knowledge key when the user saved
 * one, otherwise the main Gemini key. Blank when neither exists.
 */
fun knowledgeKeyFor(knowledgeKey: String?, geminiKey: String?): String =
    knowledgeKey?.takeIf { it.isNotBlank() } ?: geminiKey.orEmpty()
