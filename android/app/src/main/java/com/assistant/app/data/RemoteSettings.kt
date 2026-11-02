package com.assistant.app.data

import org.json.JSONArray

/**
 * Product settings that can change without a new APK. The values come from Firebase Remote Config as strings
 * (see contracts/remote-config.schema.json); anything missing, malformed or out of range falls back to the bundled
 * default, so a bad console edit can never break the app.
 */
data class RemoteSettings(
    val minSupportedVersion: Int = 0,
    val liveModels: List<AssistantOptions.LiveModel> = AssistantOptions.bundledLiveModels,
    val liveModelDefault: String = AssistantOptions.bundledLiveModels.first().id,
    val sessionMaxMinutes: Int = 15,
    val idleTimeoutSeconds: Int = 120,
    val turnTimeoutSeconds: Int = 15,
    val maxToolResultChars: Int = 6_000,
    val updateCheckMinutes: Int = 5,
    val toolsDisabled: Set<String> = emptySet(),
    val promptVersion: String = "",
    val skillsVersion: String = ""
) {
    fun isToolDisabled(functionName: String): Boolean = functionName in toolsDisabled

    companion object {
        val DEFAULTS = RemoteSettings()

        /** Builds settings from raw Remote Config strings. [raw] returns null for a key that has no value. */
        fun from(raw: (String) -> String?): RemoteSettings {
            fun int(key: String, default: Int, range: IntRange): Int =
                raw(key)?.trim()?.toIntOrNull()?.takeIf { it in range } ?: default

            val models = parseModels(raw("live_models_allowed")) ?: DEFAULTS.liveModels
            val chosen = raw("live_model_default")?.trim()
                ?.takeIf { id -> models.any { it.id == id } }
                ?: models.firstOrNull { it.id == DEFAULTS.liveModelDefault }?.id
                ?: models.first().id

            return RemoteSettings(
                minSupportedVersion = int("min_supported_version", DEFAULTS.minSupportedVersion, 0..Int.MAX_VALUE),
                liveModels = models,
                liveModelDefault = chosen,
                sessionMaxMinutes = int("session_max_minutes", DEFAULTS.sessionMaxMinutes, 1..60),
                idleTimeoutSeconds = int("idle_timeout_seconds", DEFAULTS.idleTimeoutSeconds, 10..1800),
                turnTimeoutSeconds = int("turn_timeout_seconds", DEFAULTS.turnTimeoutSeconds, 5..120),
                maxToolResultChars = int("max_tool_result_chars", DEFAULTS.maxToolResultChars, 500..50_000),
                updateCheckMinutes = int("update_check_minutes", DEFAULTS.updateCheckMinutes, 1..1440),
                toolsDisabled = parseStrings(raw("tools_disabled")) ?: DEFAULTS.toolsDisabled,
                promptVersion = raw("prompt_version")?.trim().orEmpty(),
                skillsVersion = raw("skills_version")?.trim().orEmpty()
            )
        }

        private fun parseStrings(text: String?): Set<String>? {
            if (text.isNullOrBlank()) return null
            return runCatching {
                val array = JSONArray(text)
                (0 until array.length()).map { array.getString(it).trim() }.filter { it.isNotEmpty() }.toSet()
            }.getOrNull()
        }

        private fun parseModels(text: String?): List<AssistantOptions.LiveModel>? {
            if (text.isNullOrBlank()) return null
            return runCatching {
                val array = JSONArray(text)
                (0 until array.length()).map {
                    val item = array.getJSONObject(it)
                    val id = item.getString("id").trim()
                    val label = item.getString("label").trim()
                    require(id.isNotEmpty() && label.isNotEmpty())
                    AssistantOptions.LiveModel(id, label)
                }.also { require(it.isNotEmpty()) }
            }.getOrNull()
        }
    }
}
