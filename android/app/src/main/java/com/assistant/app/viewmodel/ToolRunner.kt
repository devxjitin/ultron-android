package com.assistant.app.viewmodel

import android.util.Log
import com.assistant.app.data.AppSettingsStore
import com.assistant.app.data.ConversationHistoryTools
import com.assistant.app.data.DeviceTool
import com.assistant.app.data.DeviceToolRunner
import com.assistant.app.data.MEMORY_FORGET_TOOL
import com.assistant.app.data.MEMORY_SAVE_TOOL
import com.assistant.app.data.MemoryStore
import com.assistant.app.data.Personalities
import com.assistant.app.data.SET_PERSONALITY_TOOL
import com.assistant.app.data.SkillRegistry
import com.assistant.app.data.UntrustedContent
import com.assistant.app.decisions.ActionVerdict
import com.assistant.app.data.USE_SKILL_TOOL
import com.assistant.app.network.GeminiLiveClient
import com.assistant.app.util.LatencyTool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Runs the tools the voice model calls, by name, and returns the text handed back to it. */
internal class ToolRunner(
    private val state: StateFlow<AssistantUiState>,
    private val memoryStore: MemoryStore,
    private val settingsStore: AppSettingsStore,
    private val deviceToolRunner: DeviceToolRunner,
    private val knowledge: KnowledgeManager,
    private val historyTools: () -> ConversationHistoryTools,
    /** A running or warm session was built with the old personality; discard it. */
    private val onPersonalityChanged: () -> Unit,
    /** Called with the fact after a note is newly saved, so older notes it duplicates or replaces can be tidied in the background. */
    private val onMemorySaved: (String) -> Unit = {},
    /** Checks a tap or typing step before it runs; null when the setting is off. Must return Proceed if it cannot decide. */
    private val checkAction: suspend (DeviceTool, JSONObject) -> ActionVerdict = { _, _ -> ActionVerdict.Proceed },
    /** Looks at the screen after a step that worked; returns a line to add to the result, or null. */
    private val checkScreenAfter: suspend (DeviceTool, JSONObject) -> String? = { _, _ -> null },
    /** Names the skill that fits the user's request when the model asked for one that does not exist; null when unsure. */
    private val suggestSkill: suspend () -> String? = { null }
) {
    companion object {
        /** Screen steps that carry text, so they can be checked against what the user asked for. */
        val CHECKED_TOOLS = setOf(DeviceTool.CLICK_TEXT, DeviceTool.TYPE_TEXT)

        /** Screen steps after which the screen can be checked to see whether the step worked. */
        val STEP_TOOLS = setOf(
            DeviceTool.CLICK_TEXT, DeviceTool.CLICK_ITEM, DeviceTool.TAP_POSITION, DeviceTool.TYPE_TEXT,
            DeviceTool.PRESS_ENTER, DeviceTool.SWIPE, DeviceTool.LONG_PRESS
        )

        /** What the home screen shows while a tool runs, e.g. "Set alarm". */
        fun displayName(functionName: String): String = when {
            functionName == "search_knowledge_base" -> "Searching knowledge"
            functionName == MEMORY_SAVE_TOOL -> "Saving to memory"
            functionName == MEMORY_FORGET_TOOL -> "Forgetting a memory"
            functionName == USE_SKILL_TOOL -> "Loading a skill"
            functionName == SET_PERSONALITY_TOOL -> "Changing personality"
            else -> DeviceTool.fromFunctionName(functionName)?.label ?: functionName.replace('_', ' ')
        }
    }

    suspend fun run(call: GeminiLiveClient.FunctionCall, trace: LatencyTool?): String {
        val deviceTool = DeviceTool.fromFunctionName(call.name)
        return if (deviceTool != null) {
            runDeviceTool(deviceTool, call.args)
        } else if (call.name == MEMORY_SAVE_TOOL || call.name == MEMORY_FORGET_TOOL) {
            runToolSafely(call.name) { runMemoryTool(call.name, call.args) }
        } else if (call.name == SET_PERSONALITY_TOOL) {
            runToolSafely(call.name) { runPersonalityTool(call.args) }
        } else if (call.name == USE_SKILL_TOOL) {
            runSkillTool(call.args.optString("skill"), call.args.optString("task"))
        } else if (call.name != "search_knowledge_base") {
            "status=unknown_tool\nThere is no tool named ${call.name}. Do not call it again; tell the user you cannot do that with the tools you have."
        } else {
            knowledge.runSearchTool(call.args.optString("query"), call.id, trace)
        }
    }

    private suspend fun runSkillTool(skill: String, task: String): String {
        val result = SkillRegistry.use(skill, task)
        if (!result.startsWith("status=not_found\nNo skill with that name")) return result
        val suggestion = suggestSkill() ?: return result
        return "$result\nThe closest skill to the user's request is $suggestion. Call $USE_SKILL_TOOL with that name if it fits."
    }

    /** save_memory / forget_memory, called by the voice model. Memory is kept on this device only. */
    private suspend fun runMemoryTool(name: String, args: JSONObject): String {
        if (!state.value.settings.memoryEnabled) {
            return "status=disabled\nMemory is turned off by the user. Do not save or forget anything."
        }
        if (name != MEMORY_SAVE_TOOL) {
            val forgotten = memoryStore.forget(args.optString("about"))
            return when {
                forgotten.removed.isNotEmpty() -> "status=ok\nForgot: ${forgotten.removed.joinToString("; ") { it.text }}."
                forgotten.ambiguous.isNotEmpty() -> "status=ambiguous\nNothing was forgotten because several notes match: " +
                    forgotten.ambiguous.take(5).joinToString("; ") { it.text } +
                    ". Ask the user which one, then call forget_memory again with words from that note only."
                else -> "status=ok\nNothing saved matched that."
            }
        }
        val result = memoryStore.save(args.optString("fact"), args.optString("replaces").takeIf { it.isNotBlank() })
        if (result.added) onMemorySaved(args.optString("fact"))
        return buildString {
            append("status=ok\n")
            append(if (result.added) "Saved to memory." else "That is already remembered or empty.")
            if (result.replaced.isNotEmpty()) append(" Replaced: ").append(result.replaced.joinToString("; ")).append(".")
            if (result.ambiguous.isNotEmpty()) {
                append("\nNothing was deleted because the replaces hint matched several notes: ").append(result.ambiguous.joinToString("; ")).append(". ")
                append("Call save_memory again with replaces set to the exact old note, or ask the user which one is outdated.")
            }
            if (result.related.isNotEmpty()) {
                append("\nRelated older notes still saved: ").append(result.related.joinToString("; ")).append(". ")
                append("If any of them is now outdated or contradicted, call save_memory again with the same fact and replaces set to that note.")
            }
        }
    }

    /** Turns an unexpected exception in a tool into a failed result, so the model still gets an answer and the app never crashes. */
    private suspend fun runToolSafely(name: String, block: suspend () -> String): String = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e("AssistantError", "Tool $name failed", e)
        "status=failed\nThat action could not be completed."
    }

    /** set_personality, called by the voice model. The running session switches style at once through the reply. */
    private suspend fun runPersonalityTool(args: JSONObject): String {
        val id = args.optString("personality")
        if (!Personalities.isKnown(id)) {
            return "status=invalid\nChoose one of: ${Personalities.all.joinToString { it.id }}."
        }
        val personality = Personalities.byId(id)
        settingsStore.setPersonality(personality.id)
        onPersonalityChanged()
        return "status=ok\nPersonality is now ${personality.label}. From your next reply on, speak this way: " +
            "${personality.styleNote}. This replaces your earlier character description; every other rule stays. " +
            "Confirm the change in one short line in the new style."
    }

    /** Runs a device tool and returns the text handed back to the voice model. */
    private suspend fun runDeviceTool(tool: DeviceTool, args: JSONObject): String {
        if (tool !in state.value.settings.enabledDeviceTools && tool !in DeviceTool.ASSISTANT_OWN) {
            return "status=disabled\nThe user has turned this tool off. Tell them it is switched off in Settings."
        }
        if (tool == DeviceTool.SEARCH_CONVERSATION_HISTORY || tool == DeviceTool.RECENT_CONVERSATION) {
            return UntrustedContent.track(tool, runToolSafely(tool.functionName) { runHistoryTool(tool, args) })
        }
        val verdict = if (tool in CHECKED_TOOLS) checkAction(tool, args) else ActionVerdict.Proceed
        if (verdict is ActionVerdict.Block) return "status=blocked\n${verdict.reason}"
        var result = UntrustedContent.track(tool, deviceToolRunner.run(tool, args))
        if (verdict is ActionVerdict.Warn) result = "$result\nwarning=${verdict.note}"
        if (tool in STEP_TOOLS && result.startsWith("status=ok")) checkScreenAfter(tool, args)?.let { result = "$result\n$it" }
        return result
    }

    private suspend fun runHistoryTool(tool: DeviceTool, args: JSONObject): String = withContext(Dispatchers.IO) {
        historyTools().run(tool, args)
    }
}
