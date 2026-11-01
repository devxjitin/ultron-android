package com.assistant.app.data

import android.content.Context
import org.json.JSONObject
import java.io.File

const val USE_SKILL_TOOL = "use_skill"
const val SKILL_ACTION_TOOL = "skill_action"
const val GITHUB_SKILL_ID = "github"
const val FEATURES_SKILL_ID = "ultron-features"

private const val GITHUB_ACTION_PREFIX = "github:"
private const val FEATURES_DESCRIPTION =
    "What you can and cannot do on this phone. Use when asked what you can do, or before saying you cannot do something."

/** One thing a skill can do. The user allows or disallows each action on the Skills page. */
class SkillAction(
    val name: String,
    val label: String,
    val hint: String,
    val description: String,
    val params: List<ToolParam>,
    /** Only known for tools from an MCP server; null when the app cannot tell. */
    val readOnly: Boolean?,
    val deviceTool: DeviceTool?
) {
    /** What the app stores when the user disallows this action. */
    val key: String = deviceTool?.functionName ?: (GITHUB_ACTION_PREFIX + name)
}

/**
 * A built-in group of actions, like one MCP server and its tools. Users cannot create skills; they can only allow or
 * disallow the skills and actions that ship with the app. A skill with a [guide] and no actions is a read-only note.
 */
class BuiltInSkill(
    val id: String,
    val label: String,
    val description: String,
    val actions: List<SkillAction>,
    val guide: String? = null
)

/** The skills that ship with the app: the phone-side tool groups, plus GitHub when it is signed in. */
object BuiltInSkills {
    private val meta: Map<String, Pair<String, String>> = mapOf(
        "device_info" to ("Device info" to "Time, battery, storage, network, Wi-Fi, Bluetooth, steps and light level."),
        "device_controls" to ("Device controls" to "Flashlight, brightness, volume, ringer, vibration, do not disturb, media keys, screen timeout and rotation."),
        "alarms" to ("Alarms & reminders" to "Set, list, change and cancel alarms, timers and reminders."),
        "calendar" to ("Calendar" to "Check the schedule, find free time and add or cancel events."),
        "contacts" to ("Contacts" to "Find people and see their birthdays in the address book."),
        "notifications" to ("Notifications" to "Read, reply to and clear notifications, and see what is playing."),
        "location" to ("Location" to "Know where the user is and get directions."),
        "app_usage" to ("App usage" to "See which apps the user uses most."),
        "duckduckgo" to ("DuckDuckGo" to "Search the web for news, prices and facts."),
        "web_reader" to ("Web pages" to "Read the main text of a web page or link."),
        "weather" to ("Weather" to "Current weather and forecast for here or any city."),
        "youtube" to ("YouTube" to "Search videos, get details and transcripts, list a channel and download video or audio."),
        "calls" to ("Calls" to "Dial, answer and end calls, read the call log, call a contact and save new contacts."),
        "messages" to ("Messages" to "Read text messages and send one to a number or a contact."),
        "apps_settings" to ("Apps & settings" to "Open apps, the camera and settings, use the clipboard, show notifications."),
        "files" to ("Files" to "Find images, videos, audio and documents and open them."),
        "navigation" to ("Phone navigation" to "Back, home, recents, shade, quick settings, scrolling, lock screen and screenshots."),
        "screen" to ("Screen control" to "Read the screen, tap, type and wait for text to operate other apps.")
    )

    val phone: List<BuiltInSkill> = PhoneConnectors.all.map { connector ->
        val (label, description) = meta[connector.id] ?: (connector.id to connector.id)
        BuiltInSkill(connector.id, label, description, connector.tools.map { deviceAction(it) })
    }

    private fun deviceAction(tool: DeviceTool) = SkillAction(
        tool.functionName, tool.label, tool.hint, tool.description, tool.params, readOnly = null, deviceTool = tool
    )

    fun github(tools: List<McpToolSpec>): BuiltInSkill = BuiltInSkill(
        GITHUB_SKILL_ID, "GitHub", "Repositories, issues, pull requests and code on the user's GitHub account.",
        tools.map { SkillAction(it.name, it.name.replace('_', ' '), it.description.take(90), it.description.take(400), paramsFromSchema(it.schema), it.readOnly, null) }
    )

    /** Reads the top-level properties of a JSON Schema into plain parameters; nested shapes are passed as JSON text. */
    private fun paramsFromSchema(schema: String): List<ToolParam> = runCatching {
        val root = JSONObject(schema)
        val properties = root.optJSONObject("properties") ?: return@runCatching emptyList()
        val required = root.optJSONArray("required")?.let { array -> (0 until array.length()).map { array.getString(it) }.toSet() }.orEmpty()
        properties.keys().asSequence().map { name ->
            val property = properties.optJSONObject(name)
            val type = when (property?.optString("type")) {
                "integer" -> ToolParamType.INTEGER
                "number" -> ToolParamType.NUMBER
                else -> ToolParamType.STRING
            }
            ToolParam(name, type, property?.optString("description").orEmpty().take(160), name in required)
        }.toList()
    }.getOrDefault(emptyList())
}

/** What the model asked for, checked against what the user allows. */
sealed interface SkillResolution {
    data class Device(val tool: DeviceTool) : SkillResolution
    data class Github(val name: String) : SkillResolution
    data class Refused(val message: String) : SkillResolution
}

/**
 * The one place that decides what the model may see and call. The system prompt lists skills by name only; use_skill reveals
 * the allowed actions of one skill; skill_action runs one of them. Skills or actions the user disallowed never appear.
 */
object SkillPolicy {
    /** Every skill, allowed or not, for the Skills page. */
    fun catalog(settings: AssistantSettings): List<BuiltInSkill> =
        BuiltInSkills.phone + BuiltInSkills.github(settings.githubTools)

    fun isAllowed(settings: AssistantSettings, action: SkillAction): Boolean =
        action.deviceTool?.let { it in settings.enabledDeviceTools }
            ?: (settings.githubLogin.isNotEmpty() && action.name in settings.enabledGithubTools)

    /** The skills the model can see: allowed actions only, and skills with none left are dropped. */
    fun active(settings: AssistantSettings): List<BuiltInSkill> {
        val skills = catalog(settings).mapNotNull { skill ->
            val allowed = skill.actions.filter { isAllowed(settings, it) }
            if (allowed.isEmpty()) null else BuiltInSkill(skill.id, skill.label, skill.description, allowed)
        }
        val guide = SkillGuide.text
        return if (guide.isBlank()) skills else skills + BuiltInSkill(FEATURES_SKILL_ID, "Ultron features", FEATURES_DESCRIPTION, emptyList(), guide)
    }

    /** The lines added to the system prompt: skill names and purposes only, never their actions. */
    fun promptSection(skills: List<BuiltInSkill>): String {
        if (skills.isEmpty()) return ""
        return buildString {
            append("Skills: your abilities are grouped into skills, and you only see each skill's id and purpose here. ")
            append("When a request needs one, first call $USE_SKILL_TOOL with its id. It returns the actions you may use. ")
            append("Then call $SKILL_ACTION_TOOL with the skill id, the action name and its arguments. ")
            append("Never guess an action that $USE_SKILL_TOOL did not list. If no skill fits, say that ability is switched off and can be turned on in Skills.")
            skills.forEach { append("\n- ").append(it.id).append(": ").append(it.description) }
        }
    }

    /** Result of use_skill: the allowed actions of one skill with their arguments, or the guide text of a note skill. */
    fun open(settings: AssistantSettings, skillId: String): String {
        val skills = active(settings)
        val skill = skills.firstOrNull { it.id == skillId.trim().lowercase() }
            ?: return "status=not_found\nNo such skill. Available: ${skills.joinToString { it.id }}."
        skill.guide?.let { return "status=ok\nFollow this guide for the current request.\n\n$it" }
        return buildString {
            append("status=ok\nSkill ${skill.id} (${skill.label}). Call $SKILL_ACTION_TOOL with skill=\"${skill.id}\", an action name and arguments as JSON text. ")
            append("These are the only actions available:")
            skill.actions.forEach { action ->
                append("\n- ").append(action.name).append(": ").append(action.description)
                if (action.params.isEmpty()) append("\n  arguments: none, use {}")
                action.params.forEach { p ->
                    append("\n  ").append(p.name).append(" (").append(p.type.name.lowercase()).append(if (p.required) ", required" else ", optional").append(")")
                    if (p.description.isNotBlank()) append(": ").append(p.description)
                    if (p.choices.isNotEmpty()) append(" One of: ").append(p.choices.joinToString())
                }
            }
        }
    }

    /** Checks one skill_action call. [unlocked] holds the skills the model already opened with use_skill. */
    fun resolve(settings: AssistantSettings, unlocked: Set<String>, skillId: String, actionName: String): SkillResolution {
        val id = skillId.trim().lowercase()
        val skill = active(settings).firstOrNull { it.id == id }
            ?: return SkillResolution.Refused("status=not_found\nThat skill is not available. It may be switched off in Skills.")
        if (skill.guide != null) return SkillResolution.Refused("status=not_found\nThis skill has no actions. Call $USE_SKILL_TOOL and follow its guide.")
        if (id !in unlocked) return SkillResolution.Refused("status=locked\nCall $USE_SKILL_TOOL with \"$id\" first to see its actions.")
        val action = skill.actions.firstOrNull { it.name == actionName.trim() }
            ?: return SkillResolution.Refused("status=not_found\nThat action is not available in this skill. Allowed: ${skill.actions.joinToString { it.name }}.")
        return action.deviceTool?.let { SkillResolution.Device(it) } ?: SkillResolution.Github(action.name)
    }

    /** skill_action carries its arguments as JSON text; blank means none. Returns null when the text is not a JSON object. */
    fun parseArguments(text: String): JSONObject? {
        if (text.isBlank()) return JSONObject()
        return runCatching { JSONObject(text) }.getOrNull()
    }
}

/** The "ultron-features" note, read once from the app's assets. */
object SkillGuide {
    @Volatile
    var text: String = ""
        private set

    fun load(context: Context) {
        val app = context.applicationContext
        text = runCatching {
            app.assets.open("skills/$FEATURES_SKILL_ID/SKILL.md").bufferedReader().use { it.readText() }
        }.getOrNull()?.let(::stripFrontmatter).orEmpty()
        // Earlier versions let users write their own skills; those are gone, so their files are removed.
        runCatching { File(app.filesDir, "skills").deleteRecursively() }
        runCatching { app.getSharedPreferences("skills", Context.MODE_PRIVATE).edit().clear().apply() }
    }

    internal fun stripFrontmatter(raw: String): String {
        val lines = raw.replace("\r\n", "\n").trimStart('﻿').lines()
        if (lines.firstOrNull()?.trim() != "---") return lines.joinToString("\n").trim()
        val end = lines.drop(1).indexOfFirst { it.trim() == "---" }
        return if (end < 0) lines.joinToString("\n").trim() else lines.drop(end + 2).joinToString("\n").trim()
    }
}
