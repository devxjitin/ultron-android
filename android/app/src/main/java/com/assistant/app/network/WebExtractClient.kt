package com.assistant.app.network

import org.json.JSONObject

/**
 * Reads a web page as clean text through the web-extract MCP server on AWS Lambda (trafilatura). It goes through the
 * Ultron API with the user's Firebase token.
 */
object WebExtractClient {
    private val apiClient: McpClient? by lazy {
        if (ApiClient.isConfigured) McpClient(ApiClient.url("/v1/web/mcp")) { ApiClient.authHeaders() } else null
    }

    suspend fun read(url: String, maxChars: Int): String {
        val mcp = apiClient ?: return "status=failed\nThe web page reader is not set up in this build."
        val args = JSONObject().put("url", url).put("max_chars", maxChars)
        val result = mcp.callTool("extract_page_content", args)
        result.getOrNull()?.let { return it }
        val reason = (result.exceptionOrNull() as? McpException)?.message?.removePrefix("status=error\n")
        return "status=failed\n${reason ?: "The page could not be read."}"
    }
}
