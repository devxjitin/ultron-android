package com.assistant.app.network

import com.assistant.app.BuildConfig
import org.json.JSONObject

/**
 * Reads a web page as clean text through the web-extract MCP server on AWS Lambda (trafilatura). It goes through the
 * Ultron API with the user's Firebase token; the older secret-URL server is only the fallback while phones move over.
 */
object WebExtractClient {
    private val apiClient: McpClient? by lazy {
        if (ApiClient.isConfigured) McpClient(ApiClient.url("/v1/web/mcp")) { ApiClient.authHeaders() } else null
    }
    private val legacyClient: McpClient? by lazy {
        BuildConfig.WEB_EXTRACT_URL.takeIf { it.isNotBlank() }?.let { McpClient(it) { emptyMap() } }
    }

    suspend fun read(url: String, maxChars: Int): String {
        val clients = listOfNotNull(apiClient, legacyClient)
        if (clients.isEmpty()) return "status=failed\nThe web page reader is not set up in this build."
        val args = JSONObject().put("url", url).put("max_chars", maxChars)
        var failure: Throwable? = null
        for (mcp in clients) {
            val result = mcp.callTool("extract_page_content", args)
            result.getOrNull()?.let { return it }
            failure = result.exceptionOrNull()
        }
        val reason = (failure as? McpException)?.message?.removePrefix("status=error\n")
        return "status=failed\n${reason ?: "The page could not be read."}"
    }
}
