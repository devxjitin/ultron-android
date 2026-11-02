package com.assistant.app.network

import com.assistant.app.data.DeviceTool
import com.assistant.app.data.GITHUB_TOOL_PREFIX
import com.assistant.app.data.MEMORY_FORGET_TOOL
import com.assistant.app.data.MEMORY_SAVE_TOOL
import com.assistant.app.data.McpToolSpec
import com.assistant.app.data.SkillGroup
import com.assistant.app.data.USE_SKILL_TOOL
import org.json.JSONArray
import org.json.JSONObject

/** Function declarations in the shape Gemini expects, used by the Live voice session. */
object ToolDeclarations {

    /** A device tool. */
    fun device(tool: DeviceTool): JSONObject {
        val declaration = JSONObject()
            .put("name", tool.functionName)
            .put("description", tool.description)
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

    /** Search over the user's saved knowledge documents (RAG). */
    fun knowledgeSearch(): JSONObject = JSONObject()
        .put("name", "search_knowledge_base")
        .put(
            "description",
            "Searches the documents the user saved in this app. Call this only " +
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
        prefix: String = GITHUB_TOOL_PREFIX
    ): JSONObject {
        val analysis = McpSchema.analyze(tool.schema)
        val note = if (analysis.issues.isEmpty()) "" else " (Argument types are simplified for this model: ${analysis.issues.joinToString()}.)"
        val declaration = JSONObject()
            .put("name", prefix + tool.name)
            .put("description", tool.description.take(400) + note)
        analysis.parameters?.let { declaration.put("parameters", it) }
        return declaration
    }

    /**
     * use_skill: opens one skill. Without a task it lists the skill's allowed tasks; with a task it loads only that task's
     * instructions, so unused skills and tasks never fill the prompt.
     */
    fun skill(skills: List<SkillGroup>): JSONObject {
        val declaration = JSONObject().put("name", USE_SKILL_TOOL)
            .put("description", "Opens one skill listed in the system prompt. Call it with only the skill to see its tasks, then again with the skill and one task to load that task's instructions, and follow them. Use it only when the request clearly matches the skill.")
            .put("parameters", JSONObject().put("type", "OBJECT")
                .put("properties", JSONObject()
                    .put("skill", JSONObject().put("type", "STRING").put("description", "Skill name.")
                        .put("enum", JSONArray(skills.map { it.name })))
                    .put("task", JSONObject().put("type", "STRING").put("description", "Optional. One task name exactly as the skill listed it.")))
                .put("required", JSONArray().put("skill")))
        return declaration
    }

    /** save_memory / forget_memory: the assistant's personal memory about the user. */
    fun memory(): List<JSONObject> {
        fun string(description: String) = JSONObject().put("type", "STRING").put("description", description)
        fun tool(name: String, description: String, properties: JSONObject, required: String): JSONObject {
            val declaration = JSONObject().put("name", name).put("description", description)
                .put("parameters", JSONObject().put("type", "OBJECT").put("properties", properties)
                    .put("required", JSONArray().put(required)))
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
