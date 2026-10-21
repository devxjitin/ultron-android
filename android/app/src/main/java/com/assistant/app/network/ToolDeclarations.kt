package com.assistant.app.network

import com.assistant.app.data.DeviceTool
import com.assistant.app.data.GITHUB_TOOL_PREFIX
import com.assistant.app.data.MEMORY_FORGET_TOOL
import com.assistant.app.data.MEMORY_SAVE_TOOL
import com.assistant.app.data.McpToolSpec
import org.json.JSONArray
import org.json.JSONObject

/** Function declarations in the shape Gemini expects, shared by the Live voice session and the typed chat. */
object ToolDeclarations {

    /** A device tool. [nonBlocking] lets the Live model keep talking while the tool runs. */
    fun device(tool: DeviceTool, nonBlocking: Boolean = false): JSONObject {
        val declaration = JSONObject()
            .put("name", tool.functionName)
            .put("description", tool.description)
        if (nonBlocking) declaration.put("behavior", "NON_BLOCKING")
        if (tool.params.isEmpty()) return declaration
        val properties = JSONObject()
        tool.params.forEach { param ->
            val property = JSONObject().put("type", param.type.name).put("description", param.description)
            if (param.choices.isNotEmpty()) property.put("enum", JSONArray(param.choices))
            properties.put(param.name, property)
        }
        val required = JSONArray(tool.params.filter { it.required }.map { it.name })
        return declaration.put(
            "parameters",
            JSONObject().put("type", "OBJECT").put("properties", properties).put("required", required)
        )
    }

    /** Search over the user's saved knowledge documents (RAG), for the typed chat. */
    fun knowledgeSearch(): JSONObject = JSONObject()
        .put("name", "search_knowledge_base")
        .put(
            "description",
            "Searches the documents and notes the user saved in this app. Call this only " +
                "when the user asks about information they stored themselves, never for " +
                "everyday or general questions."
        )
        .put(
            "parameters",
            JSONObject().put("type", "OBJECT")
                .put("properties", JSONObject().put("query", JSONObject().put("type", "STRING").put("description", "The user's question, in their own words.")))
                .put("required", JSONArray().put("query"))
        )

    /** A tool from a connected MCP server, declared under a prefixed name so it cannot clash with device tools. */
    fun mcp(
        tool: McpToolSpec,
        nonBlocking: Boolean = false,
        prefix: String = GITHUB_TOOL_PREFIX,
        /** Chat only: also carries the tool's own JSON Schema under [ORIGINAL_SCHEMA_KEY] for providers that accept it as is. */
        withOriginalSchema: Boolean = false
    ): JSONObject {
        val analysis = McpSchema.analyze(tool.schema)
        val note = if (analysis.issues.isEmpty()) "" else " (Argument types are simplified for this model: ${analysis.issues.joinToString()}.)"
        val declaration = JSONObject()
            .put("name", prefix + tool.name)
            .put("description", tool.description.take(400) + note)
        if (nonBlocking) declaration.put("behavior", "NON_BLOCKING")
        analysis.parameters?.let { declaration.put("parameters", it) }
        if (withOriginalSchema) {
            runCatching { JSONObject(tool.schema).also { it.remove("\$schema") } }.getOrNull()
                ?.takeIf { it.optString("type") == "object" }?.let { declaration.put(ORIGINAL_SCHEMA_KEY, it) }
        }
        return declaration
    }

    /** Declarations carry the tool's own schema under this key for Azure; Gemini rejects unknown fields, so its client removes it. */
    const val ORIGINAL_SCHEMA_KEY = "_jsonSchema"

    fun withoutPrivateFields(declarations: JSONArray): JSONArray = JSONArray().also { copy ->
        for (i in 0 until declarations.length()) {
            val declaration = JSONObject(declarations.getJSONObject(i).toString())
            declaration.keys().asSequence().filter { it.startsWith("_") }.toList().forEach { declaration.remove(it) }
            copy.put(declaration)
        }
    }

    /** save_memory / forget_memory: the assistant's personal memory about the user. */
    fun memory(nonBlocking: Boolean = false): List<JSONObject> {
        fun string(description: String) = JSONObject().put("type", "STRING").put("description", description)
        fun tool(name: String, description: String, properties: JSONObject, required: String): JSONObject {
            val declaration = JSONObject().put("name", name).put("description", description)
                .put("parameters", JSONObject().put("type", "OBJECT").put("properties", properties)
                    .put("required", JSONArray().put(required)))
            if (nonBlocking) declaration.put("behavior", "NON_BLOCKING")
            return declaration
        }
        return listOf(
            tool(MEMORY_SAVE_TOOL,
                "Saves one lasting fact or preference about the user so it is remembered in future conversations. " +
                    "When the fact updates or contradicts something already remembered, pass that old note in replaces so it is replaced and not duplicated.",
                JSONObject()
                    .put("fact", string("One short note about the user, in third person, e.g. \"Likes strong tea without sugar\"."))
                    .put("replaces", string("Optional. The older remembered note (or its key words) that this fact replaces, e.g. \"Likes strong tea without sugar\".")),
                "fact"),
            tool(MEMORY_FORGET_TOOL,
                "Forgets saved facts about the user that match the given words.",
                JSONObject().put("about", string("A few key words of the fact to forget, e.g. \"tea\".")),
                "about")
        )
    }
}
