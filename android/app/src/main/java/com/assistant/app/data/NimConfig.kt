package com.assistant.app.data

/** NVIDIA NIM hosted endpoints (OpenAI-compatible); free to try with a key from build.nvidia.com. */
const val NIM_BASE_URL = "https://integrate.api.nvidia.com/v1"

const val DEFAULT_NIM_MODEL = "meta/llama-3.3-70b-instruct"

/** A few well-known hosted models to start from; "Load models" in Settings lists everything the catalog offers. */
val NIM_SUGGESTED_MODELS = listOf(
    "meta/llama-3.3-70b-instruct",
    "meta/llama-3.1-405b-instruct",
    "nvidia/llama-3.1-nemotron-70b-instruct",
    "deepseek-ai/deepseek-r1",
    "mistralai/mistral-large-2-instruct",
    "qwen/qwen2.5-coder-32b-instruct"
)

/** The typed chat on NVIDIA NIM when [enabled] and a key and model are set; the user chooses [model] themselves. */
data class NimConfig(
    val enabled: Boolean = false,
    val apiKey: String = "",
    val model: String = DEFAULT_NIM_MODEL
) {
    val isActive: Boolean get() = enabled && apiKey.isNotBlank() && model.isNotBlank()
}

private val NOT_CHAT_MODEL = Regex("embed|rerank|reward|guard|safety|parse|ocr|asr|tts|riva|clip", RegexOption.IGNORE_CASE)

/** Model ids from the catalog that can chat: embedding, ranking, safety and speech models are left out. */
fun nimChatModels(ids: List<String>): List<String> = ids.distinct().filter { it.isNotBlank() && !NOT_CHAT_MODEL.containsMatchIn(it) }.sorted()
