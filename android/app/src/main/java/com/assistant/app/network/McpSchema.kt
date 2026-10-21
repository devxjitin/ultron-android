package com.assistant.app.network

import org.json.JSONArray
import org.json.JSONObject

/**
 * Turns an MCP tool's JSON Schema into the smaller schema dialect Gemini accepts for function
 * parameters: upper-case types, no $ref/anyOf/additionalProperties, only fields Gemini understands.
 */
object McpSchema {
    /** What the conversion produced, and the places where the result is looser than the original schema. */
    class Analysis(val parameters: JSONObject?, val issues: List<String>)

    /** Null when the tool takes no arguments (Gemini rejects an object with no properties). */
    fun toGeminiParameters(schemaJson: String): JSONObject? = analyze(schemaJson).parameters

    fun analyze(schemaJson: String): Analysis {
        val schema = runCatching { JSONObject(schemaJson) }.getOrNull() ?: return Analysis(null, listOf("The schema is not valid JSON."))
        val issues = mutableListOf<String>()
        val converted = convert(schema, 0, schema, issues) ?: return Analysis(null, issues.distinct())
        val parameters = converted.takeIf { it.optString("type") == "OBJECT" && (it.optJSONObject("properties")?.length() ?: 0) > 0 }
        return Analysis(parameters, issues.distinct())
    }

    /** Follows a local reference such as "#/$defs/Thing" or "#/definitions/Thing" inside [root]; null if it cannot be found. */
    private fun resolveRef(ref: String, root: JSONObject): JSONObject? {
        if (!ref.startsWith("#/")) return null
        var current: JSONObject = root
        for (part in ref.removePrefix("#/").split('/')) {
            current = current.optJSONObject(part.replace("~1", "/").replace("~0", "~")) ?: return null
        }
        return current
    }

    private fun unsupported(note: String) = JSONObject().put("type", "STRING").put("description", note)

    private fun convert(node: JSONObject, depth: Int, root: JSONObject, issues: MutableList<String>): JSONObject? {
        if (depth > MAX_DEPTH) {
            issues += "deeply nested arguments"
            return unsupported("Too deeply nested to describe; pass a JSON-encoded value.")
        }
        node.optString("\$ref").takeIf { it.isNotEmpty() }?.let { ref ->
            val target = resolveRef(ref, root) ?: run {
                issues += "an unresolved reference"
                return unsupported("Unresolved schema reference; pass a JSON-encoded value.")
            }
            // Keep the description written next to the reference.
            val merged = JSONObject(target.toString())
            node.optString("description").takeIf { it.isNotBlank() }?.let { merged.put("description", it) }
            return convert(merged, depth + 1, root, issues)
        }
        // A single-part allOf is just that part.
        node.optJSONArray("allOf")?.takeIf { it.length() == 1 }?.optJSONObject(0)?.let { return convert(it, depth + 1, root, issues) }
        // Unions: Gemini has none, so the first real option is used and the others are named in the description.
        val union = node.optJSONArray("anyOf") ?: node.optJSONArray("oneOf")
        if (union != null) {
            val options = (0 until union.length()).mapNotNull { union.optJSONObject(it) }.filter { it.optString("type") != "null" }
            val first = options.firstOrNull()
            if (first != null) {
                val converted = convert(first, depth + 1, root, issues) ?: return null
                val others = options.drop(1).map { it.optString("type").ifEmpty { "other" } }.distinct()
                    .filter { it != first.optString("type") }
                if (others.isNotEmpty()) {
                    issues += "arguments that accept more than one type"
                    val note = "Also accepts: ${others.joinToString()}."
                    converted.put("description", listOfNotNull(converted.optString("description").takeIf { it.isNotBlank() }, note).joinToString(" "))
                }
                return converted
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
                    source.optJSONObject(key)?.let { child -> convert(child, depth + 1, root, issues)?.let { properties.put(key, it) } }
                }
                out.put("properties", properties)
                if (source.length() == 0 && depth > 0) issues += "free-form object arguments"
                val required = node.optJSONArray("required")
                if (required != null) {
                    val kept = JSONArray()
                    for (i in 0 until required.length()) required.optString(i).takeIf { properties.has(it) }?.let(kept::put)
                    if (kept.length() > 0) out.put("required", kept)
                }
            }
            "array" -> {
                out.put("type", "ARRAY")
                out.put("items", node.optJSONObject("items")?.let { convert(it, depth + 1, root, issues) } ?: JSONObject().put("type", "STRING"))
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

    private const val MAX_DEPTH = 8
    private const val MAX_DESCRIPTION = 400
}
