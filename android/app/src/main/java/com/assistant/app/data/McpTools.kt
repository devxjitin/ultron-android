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

/**
 * The app only reads from GitHub, so only tools the server marks read-only are used, whatever the server was asked
 * to hide. A tool that says nothing about itself is treated as one that can change data. There are no per-tool switches.
 */
fun effectiveMcpTools(tools: List<McpToolSpec>): Set<String> = tools.filter { it.readOnly }.map { it.name }.toSet()
