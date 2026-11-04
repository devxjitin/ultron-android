package com.assistant.app.network

import org.json.JSONObject

/** Read-only YouTube tools run on AWS; media bytes and download commands stay on the phone. */
object YouTubeSearchClient {
    private val client: McpClient? by lazy {
        if (ApiClient.isConfigured) McpClient(ApiClient.url("/v1/youtube/mcp")) { ApiClient.authHeaders() } else null
    }

    suspend fun callTool(name: String, arguments: JSONObject): String {
        val mcp = client ?: return "status=failed\nYouTube Search is not set up in this build."
        val result = mcp.callTool(name, arguments)
        return result.getOrNull() ?: "status=failed\n" +
            ((result.exceptionOrNull() as? McpException)?.message ?: "Could not reach YouTube Search.")
    }
}
