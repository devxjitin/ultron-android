package com.assistant.app.data

import org.json.JSONArray
import org.json.JSONObject

/** One tool a connected MCP server offers, as cached on the device. */
data class McpToolSpec(
    val name: String,
    val description: String,
    /** The tool's JSON Schema for its arguments, as JSON text. */
    val schema: String,
    val readOnly: Boolean
)

/** Prefix that tells the app a tool call belongs to the GitHub connector, not a device tool. */
const val GITHUB_TOOL_PREFIX = "github_"

const val GITHUB_MCP_URL = "https://api.githubcopilot.com/mcp/"

/** The everyday read-only GitHub tools that start switched on; the rest are opt-in. */
private val PREFERRED_GITHUB_TOOLS = setOf(
    "get_me", "search_repositories", "search_code", "search_issues", "search_pull_requests",
    "get_file_contents", "list_issues", "list_pull_requests", "list_commits", "get_issue", "get_pull_request"
)

fun encodeMcpTools(tools: List<McpToolSpec>): String = JSONArray().also { array ->
    tools.forEach {
        array.put(JSONObject().put("name", it.name).put("description", it.description)
            .put("schema", it.schema).put("readOnly", it.readOnly))
    }
}.toString()

fun decodeMcpTools(json: String?): List<McpToolSpec> = runCatching {
    val array = JSONArray(json ?: return emptyList())
    (0 until array.length()).map {
        val item = array.getJSONObject(it)
        McpToolSpec(item.getString("name"), item.optString("description"), item.optString("schema", "{}"), item.optBoolean("readOnly"))
    }
}.getOrDefault(emptyList())

/** Applies the user's saved on/off choices ("name=1" / "name=0") over each tool's default. */
fun effectiveMcpTools(tools: List<McpToolSpec>, overrides: Set<String>?): Set<String> {
    val chosen = overrides.orEmpty().mapNotNull {
        val parts = it.split("=", limit = 2)
        if (parts.size == 2) parts[0] to (parts[1] == "1") else null
    }.toMap()
    val preferredPresent = tools.any { it.name in PREFERRED_GITHUB_TOOLS && it.readOnly }
    return tools.filter { tool ->
        chosen[tool.name] ?: (tool.readOnly && (!preferredPresent || tool.name in PREFERRED_GITHUB_TOOLS))
    }.map { it.name }.toSet()
}
