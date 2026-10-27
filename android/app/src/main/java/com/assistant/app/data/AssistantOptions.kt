package com.assistant.app.data

/** Static catalogs of Live API options the settings screen lets the user pick from. */
object AssistantOptions {

    /** Fixed Gemini text model that prepares PDFs into knowledge datasets. */
    const val DATASET_MODEL = "gemini-3.5-flash-lite"

    /**
     * [extendedThinking] models need `thinkingConfig` in setup, only accept NON_BLOCKING function
     * calls, and report real turn state in `interactionStatus` (a `turnComplete` can arrive while
     * the model is still working, e.g. after a spoken filler before a tool call).
     */
    data class LiveModel(val id: String, val label: String, val extendedThinking: Boolean = false)

    /** Live API models the user can choose between in Settings. */
    val liveModels: List<LiveModel> = listOf(
        LiveModel("gemini-3.8-live", "Gemini 3.8 Live"),
        LiveModel("gemini-3.8-live-extended-thinking", "Gemini 3.8 Live (extended thinking)", extendedThinking = true),
        LiveModel("gemini-3.1-flash-live-preview", "Gemini 3.1 Flash Live (preview)")
    )

    /** Thinking effort sent to extended-thinking models; low keeps voice replies quick. */
    const val EXTENDED_THINKING_LEVEL = "LOW"

    val defaultLiveModel: String = liveModels.first().id

    fun isApprovedLiveModel(id: String): Boolean = liveModels.any { it.id == id }

    data class Voice(val name: String, val gender: String, val style: String)

    /**
     * All 30 prebuilt Gemini Live voices, per Google's official voice list
     * (https://ai.google.dev/api/voices, https://ai.google.dev/gemini-api/docs/live-api/capabilities).
     * Gender follows Google's Chirp 3 HD voice list (same voice names), 14 female / 16 male.
     */
    val voices: List<Voice> = listOf(
        Voice("Zephyr", "Female", "Bright"),
        Voice("Kore", "Female", "Firm"),
        Voice("Orus", "Male", "Firm"),
        Voice("Autonoe", "Female", "Bright"),
        Voice("Umbriel", "Male", "Easy-going"),
        Voice("Erinome", "Female", "Clear"),
        Voice("Laomedeia", "Female", "Upbeat"),
        Voice("Schedar", "Male", "Even"),
        Voice("Achird", "Male", "Friendly"),
        Voice("Sadachbia", "Male", "Lively"),
        Voice("Puck", "Male", "Upbeat"),
        Voice("Fenrir", "Male", "Excitable"),
        Voice("Aoede", "Female", "Breezy"),
        Voice("Enceladus", "Male", "Breathy"),
        Voice("Algieba", "Male", "Smooth"),
        Voice("Algenib", "Male", "Gravelly"),
        Voice("Achernar", "Female", "Soft"),
        Voice("Gacrux", "Female", "Mature"),
        Voice("Zubenelgenubi", "Male", "Casual"),
        Voice("Sadaltager", "Male", "Knowledgeable"),
        Voice("Charon", "Male", "Informative"),
        Voice("Leda", "Female", "Youthful"),
        Voice("Callirrhoe", "Female", "Easy-going"),
        Voice("Iapetus", "Male", "Clear"),
        Voice("Despina", "Female", "Smooth"),
        Voice("Rasalgethi", "Male", "Informative"),
        Voice("Alnilam", "Male", "Firm"),
        Voice("Pulcherrima", "Female", "Forward"),
        Voice("Vindemiatrix", "Female", "Gentle"),
        Voice("Sulafat", "Female", "Warm")
    )

    /** Youthful female voice, so a fresh install sounds cute and light. */
    val defaultVoice: String = "Algieba"

    fun isApprovedVoice(voiceName: String): Boolean = voices.any { it.name == voiceName }

    fun voiceGender(voiceName: String): String? = voices.firstOrNull { it.name == voiceName }?.gender

    data class Language(val label: String, val code: String)

    /** Spoken-language choices (Indian languages only). Codes follow the Live API supported-language
     *  list (https://ai.google.dev/gemini-api/docs/live-api/capabilities); the app UI itself always
     *  stays in English regardless of this choice. */
    val languages: List<Language> = listOf(
        Language("English (India)", "en-IN"),
        Language("Hindi", "hi-IN"),
        Language("Bengali", "bn"),
        Language("Tamil", "ta"),
        Language("Telugu", "te"),
        Language("Marathi", "mr"),
        Language("Gujarati", "gu"),
        Language("Kannada", "kn"),
        Language("Malayalam", "ml"),
        Language("Punjabi", "pa"),
        Language("Urdu", "ur"),
        Language("Odia", "or"),
        Language("Assamese", "as")
    )

    val defaultLanguageCode: String = languages.first().code

    fun languageLabel(code: String): String =
        languages.firstOrNull { it.code == code }?.label ?: languages.first().label

    fun isApprovedLanguage(code: String): Boolean = languages.any { it.code == code }
}
