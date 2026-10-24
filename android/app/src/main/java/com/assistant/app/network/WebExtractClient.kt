package com.assistant.app.network

import com.assistant.app.BuildConfig
import org.json.JSONObject

/** Reads a web page as clean text through the web-extract MCP server on AWS Lambda (trafilatura). */
object WebExtractClient {
    private val client: McpClient? by lazy {
        BuildConfig.WEB_EXTRACT_URL.takeIf { it.isNotBlank() }?.let { McpClient(it) { emptyMap() } }
    }

    suspend fun read(url: String, maxChars: Int): String {
        val mcp = client ?: return "status=failed\nThe web page reader is not set up in this build."
        val args = JSONObject().put("url", url).put("max_chars", maxChars)
        return mcp.callTool("extract_page_content", args).getOrElse { e ->
            val reason = (e as? McpException)?.message?.removePrefix("status=error\n")
            "status=failed\n${reason ?: "The page could not be read."}"
        }
    }
}
