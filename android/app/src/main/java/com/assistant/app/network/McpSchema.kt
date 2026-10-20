package com.assistant.app.network

import org.json.JSONArray
import org.json.JSONObject

/**
 * Turns an MCP tool's JSON Schema into the smaller schema dialect Gemini accepts for function
 * parameters: upper-case types, no $ref/anyOf/additionalProperties, only fields Gemini understands.
 */
object McpSchema {
    /** Null when the tool takes no arguments (Gemini rejects an object with no properties). */
    fun toGeminiParameters(schemaJson: String): JSONObject? {
        val schema = runCatching { JSONObject(schemaJson) }.getOrNull() ?: return null
        val converted = convert(schema, 0) ?: return null
        return converted.takeIf { it.optString("type") == "OBJECT" && (it.optJSONObject("properties")?.length() ?: 0) > 0 }
    }

    private fun convert(node: JSONObject, depth: Int): JSONObject? {
        if (depth > MAX_DEPTH) return JSONObject().put("type", "STRING")
        // Unions: use the first real option.
        val union = node.optJSONArray("anyOf") ?: node.optJSONArray("oneOf")
        if (union != null) {
            for (i in 0 until union.length()) {
                val option = union.optJSONObject(i) ?: continue
                if (option.optString("type") != "null") return convert(option, depth + 1)
            }
        }
        val type = when (val raw = node.opt("type")) {
            is String -> raw
            is JSONArray -> (0 until raw.length()).map { raw.optString(it) }.firstOrNull { it != "null" } ?: "string"
            else -> if (node.has("properties")) "object" else "string"
        }
        val out = JSONObject()
        node.optString("description").takeIf { it.isNotBlank() }?.let { out.put("description", it.take(MAX_DESCRIPTION)) }
        when (type) {
            "object" -> {
                out.put("type", "OBJECT")
                val properties = JSONObject()
                val source = node.optJSONObject("properties") ?: JSONObject()
                source.keys().forEach { key ->
                    source.optJSONObject(key)?.let { child -> convert(child, depth + 1)?.let { properties.put(key, it) } }
                }
                out.put("properties", properties)
                val required = node.optJSONArray("required")
                if (required != null) {
                    val kept = JSONArray()
                    for (i in 0 until required.length()) required.optString(i).takeIf { properties.has(it) }?.let(kept::put)
                    if (kept.length() > 0) out.put("required", kept)
                }
            }
            "array" -> {
                out.put("type", "ARRAY")
                out.put("items", node.optJSONObject("items")?.let { convert(it, depth + 1) } ?: JSONObject().put("type", "STRING"))
            }
            "integer" -> out.put("type", "INTEGER")
            "number" -> out.put("type", "NUMBER")
            "boolean" -> out.put("type", "BOOLEAN")
            else -> {
                out.put("type", "STRING")
                node.optJSONArray("enum")?.takeIf { it.length() > 0 }?.let { values ->
                    out.put("enum", JSONArray((0 until values.length()).map { values.optString(it) }))
                }
            }
        }
        return out
    }

    private const val MAX_DEPTH = 5
    private const val MAX_DESCRIPTION = 400
}
