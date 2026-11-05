package com.assistant.app.network

import org.json.JSONArray
import org.json.JSONObject

/**
 * Web search through Ultron's AWS service, which runs the DDGS metasearch library. The phone searches DuckDuckGo
 * itself ([DuckDuckGoClient]) only when this service cannot answer.
 */
object WebSearchClient {
    private val client: McpClient? by lazy {
        if (ApiClient.isConfigured) McpClient(ApiClient.url("/v1/search/mcp")) { ApiClient.authHeaders() } else null
    }

    suspend fun search(query: String, time: String?, max: Int = 6): Result<List<SearchHit>> {
        val mcp = client ?: return Result.failure(IllegalStateException("Web search is not set up in this build."))
        val args = JSONObject().put("query", query).put("limit", max)
        if (time != null) args.put("time", time)
        return mcp.callTool("web_search", args).mapCatching { parse(it) }
    }

    /** The tool answers "status=ok", a warning line, then a JSON list of hits. Anything else is a failure. */
    internal fun parse(text: String): List<SearchHit> {
        val lines = text.lines()
        if (lines.firstOrNull()?.trim() != "status=ok") error("Web search failed.")
        val json = lines.drop(1).firstOrNull { it.trimStart().startsWith("[") && !it.trimStart().startsWith("[Web") } ?: error("No results.")
        val array = JSONArray(json)
        val hits = (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            val url = item.optString("url")
            if (url.isBlank()) null else SearchHit(item.optString("title"), url, item.optString("snippet"))
        }
        if (hits.isEmpty()) error("No results.")
        return hits
    }
}
