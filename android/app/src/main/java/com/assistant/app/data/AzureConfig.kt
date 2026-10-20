package com.assistant.app.data

/** How the typed chat reaches Azure OpenAI; [OFF] keeps the chat on Gemini. */
enum class AzureMode { OFF, API_KEY, ENDPOINT }

/**
 * Azure OpenAI settings for the typed chat. [API_KEY] needs only the key (the resource comes from the build),
 * [ENDPOINT] also takes the resource's own endpoint URL. [model] is the deployment name.
 */
data class AzureConfig(
    val mode: AzureMode = AzureMode.OFF,
    val endpoint: String = "",
    val apiKey: String = "",
    val model: String = DEFAULT_AZURE_MODEL
) {
    val isActive: Boolean get() = mode != AzureMode.OFF && apiKey.isNotBlank() && model.isNotBlank()
}

const val DEFAULT_AZURE_MODEL = "gpt-5.4-mini"

/** Deployment name to label, cheapest first. */
val AZURE_CHAT_MODELS = listOf(
    "gpt-5.4-mini" to "GPT-5.4 mini (low cost)",
    "gpt-6-luna" to "GPT-6 Luna"
)

/** Turns whatever the user pasted (resource URL, .../openai, .../openai/v1/chat/completions) into the OpenAI-style base URL. */
fun azureBaseUrl(endpoint: String): String {
    var url = endpoint.trim().trimEnd('/')
    url = url.removeSuffix("/chat/completions").trimEnd('/')
    if (url.endsWith("/openai/v1")) return url
    url = url.removeSuffix("/openai").trimEnd('/')
    return "$url/openai/v1"
}
