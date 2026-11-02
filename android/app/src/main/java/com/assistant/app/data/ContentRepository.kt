package com.assistant.app.data

import android.content.Context
import android.util.Log
import com.assistant.app.BuildConfig
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject

/** One published prompt version, already checked against the contract (contracts/prompt.schema.json). */
data class PromptContent(val persona: String, val greeting: String?) {
    companion object {
        private const val MAX_PERSONA_CHARS = 20_000
        private const val MAX_GREETING_CHARS = 2_000

        /** Null when the document is not a usable published prompt for this build; the caller keeps what it has. */
        fun parse(data: Map<String, Any?>, appVersion: Int): PromptContent? {
            if (data["status"] != "published") return null
            val persona = (data["persona"] as? String)?.trim()
                ?.takeIf { it.isNotEmpty() && it.length <= MAX_PERSONA_CHARS } ?: return null
            val minApp = (data["minAppVersion"] as? Number)?.toInt() ?: 0
            if (minApp > appVersion) return null
            val greeting = (data["greeting"] as? String)?.trim()
                ?.takeIf { it.isNotEmpty() && it.length <= MAX_GREETING_CHARS }
            return PromptContent(persona, greeting)
        }

        fun fromJson(text: String): PromptContent? = runCatching {
            val json = JSONObject(text)
            PromptContent(json.getString("persona"), json.optString("greeting").ifBlank { null })
        }.getOrNull()

        fun toJson(content: PromptContent): String = JSONObject()
            .put("persona", content.persona)
            .put("greeting", content.greeting ?: "")
            .toString()
    }
}

/**
 * Prompt text that the console can change without a new APK. The bundled defaults stay in place until a valid
 * published version arrives, the last good one is cached for offline starts, and a bad or missing document changes
 * nothing. A prompt the user wrote in Developer Settings always wins over this (see [AppSettingsStore]).
 */
object ContentRepository {
    private const val TAG = "ContentRepository"
    private const val PREFS = "remote_content"
    private const val KEY_PROMPT = "prompt"

    @Volatile private var prompt: PromptContent? = null

    /** The agent persona to use when the user has not written their own. */
    val persona: String get() = prompt?.persona ?: DEFAULT_SYSTEM_PROMPT.trim()

    val greeting: String get() = prompt?.greeting ?: DEFAULT_GREETING

    /** Loads the cached prompt so the first screen already uses the latest published text, even offline. */
    fun load(context: Context) {
        appContext = context.applicationContext
        val cached = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PROMPT, null)
        prompt = cached?.let(PromptContent::fromJson)
    }

    private lateinit var appContext: Context

    /** Reads `config/active`, then the prompt it points to, and keeps it if it is valid. Needs a signed-in user. */
    fun refresh() {
        runCatching { fetch(appContext) }.onFailure { Log.w(TAG, "Content refresh unavailable", it) }
    }

    private fun fetch(context: Context) {
        val db = FirebaseFirestore.getInstance()
        db.collection("config").document("active").get()
            .addOnSuccessListener { active ->
                val version = active.getString("promptVersion")?.takeIf { it.isNotBlank() } ?: return@addOnSuccessListener
                db.collection("prompts").document(version).get()
                    .addOnSuccessListener { doc ->
                        val parsed = doc.data?.let { PromptContent.parse(it, BuildConfig.VERSION_CODE) }
                            ?: return@addOnSuccessListener
                        prompt = parsed
                        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                            .putString(KEY_PROMPT, PromptContent.toJson(parsed)).apply()
                    }
                    .addOnFailureListener { Log.w(TAG, "Prompt fetch failed, keeping current text", it) }
            }
            .addOnFailureListener { Log.w(TAG, "Active pointer fetch failed, keeping current text", it) }
    }
}
