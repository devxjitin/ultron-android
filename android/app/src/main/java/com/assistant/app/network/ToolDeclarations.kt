package com.assistant.app.network

import com.assistant.app.data.DeviceTool
import com.assistant.app.data.MEMORY_FORGET_TOOL
import com.assistant.app.data.Personalities
import com.assistant.app.data.SET_PERSONALITY_TOOL
import com.assistant.app.data.PromptRepeat
import com.assistant.app.data.SCHEDULE_PROMPT_TOOL
import com.assistant.app.data.MEMORY_SAVE_TOOL
import com.assistant.app.data.SkillGroup
import com.assistant.app.data.USE_SKILL_TOOL
import org.json.JSONArray
import org.json.JSONObject

/** Function declarations in the shape Gemini expects, used by the Live voice session. */
object ToolDeclarations {

    /** Slow tools are non-blocking: the model keeps talking while they run instead of going silent. */
    fun isNonBlocking(name: String): Boolean = name in DeviceTool.NON_BLOCKING_NAMES

    /** A device tool. */
    fun device(tool: DeviceTool): JSONObject {
        val declaration = JSONObject()
            .put("name", tool.functionName)
            .put("description", tool.description)
        if (isNonBlocking(tool.functionName)) declaration.put("behavior", "NON_BLOCKING")
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
        .put("behavior", "NON_BLOCKING")
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

    /** schedule_prompt: saves a task the assistant runs on its own later, shown on the Schedule page. */
    fun schedulePrompt(): JSONObject {
        fun string(description: String) = JSONObject().put("type", "STRING").put("description", description)
        return JSONObject()
            .put("name", SCHEDULE_PROMPT_TOOL)
            .put("description", "Schedules a task for you to do on your own at a later time, for example \"send Sonia hello on WhatsApp at 9 pm\" " +
                "or \"every morning at 7 tell me the weather\". Use it when the user wants something done later that is more than a plain reminder; " +
                "for a plain reminder use the reminder tool.")
            .put("parameters", JSONObject().put("type", "OBJECT")
                .put("properties", JSONObject()
                    .put("prompt", string("The task in the user's words, as a full instruction, e.g. \"Send Sonia 'Hello' on WhatsApp\"."))
                    .put("at_time", string("Clock time, HH:MM, 24-hour."))
                    .put("date", string("Optional. YYYY-MM-DD. Leave out for the next time that clock time comes."))
                    .put("repeat", JSONObject().put("type", "STRING").put("description", "How often. once unless the user asks for a repeat.")
                        .put("enum", JSONArray(PromptRepeat.entries.map { it.id }))))
                .put("required", JSONArray().put("prompt").put("at_time")))
    }

    /** set_personality: switches how the assistant talks when the user asks for it by voice. */
    fun personality(): JSONObject = JSONObject()
        .put("name", SET_PERSONALITY_TOOL)
        .put("description", "Changes your personality when the user asks you to, for example \"be friendly\", " +
            "\"be professional\", \"be funny\" or \"go back to destructive\". Call it only on a clear request.")
        .put("parameters", JSONObject().put("type", "OBJECT")
            .put("properties", JSONObject().put("personality", JSONObject().put("type", "STRING")
                .put("description", "The personality to switch to.")
                .put("enum", JSONArray(Personalities.all.map { it.id }))))
            .put("required", JSONArray().put("personality")))

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
