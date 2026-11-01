package com.assistant.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** One instruction set: a short [description] the model sees, and a [body] that is loaded only when the model asks for it. */
data class Skill(val name: String, val description: String, val body: String)

/**
 * A built-in skill, grouped like an MCP server: the model always sees its [description], and its [tasks] only after it opens
 * the skill. [body] holds notes shared by every task; a skill without tasks is a single instruction set and [body] is it.
 */
data class SkillGroup(val name: String, val description: String, val body: String, val tasks: List<Skill>)

const val USE_SKILL_TOOL = "use_skill"
/** Most characters of one skill body returned to the model. */
const val MAX_SKILL_BODY_CHARS = 8_000
private const val MAX_SKILL_DESCRIPTION_CHARS = 300
/** Skills that stay on: the assistant relies on them to describe itself honestly. */
private val LOCKED_SKILLS = setOf("ultron-features")

/** Reads a skill file: `---` frontmatter with `name:` and `description:`, then the instructions. */
object SkillParser {
    private val NAME_PATTERN = Regex("[a-z0-9][a-z0-9_-]{0,47}")

    /** Null when the name, description or (unless [allowEmptyBody]) the body is missing. */
    fun parse(text: String, fallbackName: String? = null, allowEmptyBody: Boolean = false): Skill? {
        val lines = text.replace("\r\n", "\n").trimStart('﻿').lines()
        val fields = HashMap<String, String>()
        var bodyStart = 0
        if (lines.firstOrNull()?.trim() == "---") {
            val end = lines.drop(1).indexOfFirst { it.trim() == "---" }
            if (end >= 0) {
                lines.subList(1, end + 1).forEach { line ->
                    val colon = line.indexOf(':')
                    if (colon > 0) fields[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim().trim('"', '\'')
                }
                bodyStart = end + 2
            }
        }
        val name = (fields["name"] ?: fallbackName)?.trim()?.lowercase()?.takeIf { NAME_PATTERN.matches(it) } ?: return null
        val description = fields["description"]?.takeIf { it.isNotBlank() }?.take(MAX_SKILL_DESCRIPTION_CHARS) ?: return null
        val body = lines.drop(bodyStart).joinToString("\n").trim().take(MAX_SKILL_BODY_CHARS)
        if (body.isEmpty() && !allowEmptyBody) return null
        return Skill(name, description, body)
    }
}

/** A skill as the Skills page shows it. [disabledTasks] are the tasks the user disallowed inside an enabled skill. */
data class SkillEntry(val group: SkillGroup, val enabled: Boolean, val disabledTasks: Set<String>) {
    val locked: Boolean get() = group.name in LOCKED_SKILLS
}

/**
 * The built-in skills. Users cannot write skills: they can only switch a skill off, or disallow single tasks inside it.
 * Each skill lives in assets/skills/<name>/SKILL.md, with its tasks in assets/skills/<name>/tasks/<task>.md.
 * Only skill names and descriptions go into the system prompt. use_skill with a skill lists its allowed tasks, and use_skill
 * with a task returns just that task's instructions, so disallowed tasks and other skills never reach the model.
 */
object SkillRegistry {
    private val _entries = MutableStateFlow<List<SkillEntry>>(emptyList())
    /** Every skill, on or off, for the Skills page. */
    val entries: StateFlow<List<SkillEntry>> = _entries.asStateFlow()

    private var appContext: Context? = null

    /** The skills the model sees: enabled ones, holding only allowed tasks. A skill whose tasks are all disallowed is dropped. */
    fun all(): List<SkillGroup> = visible(_entries.value)

    internal fun visible(entries: List<SkillEntry>): List<SkillGroup> = entries.filter { it.enabled }.mapNotNull { entry ->
        val group = entry.group
        if (group.tasks.isEmpty()) return@mapNotNull group
        val allowed = group.tasks.filter { it.name !in entry.disabledTasks }
        if (allowed.isEmpty()) null else group.copy(tasks = allowed)
    }

    /** Keys of what the user switched off: "skill" for a whole skill, "skill/task" for one task. */
    private fun prefs(context: Context) = context.getSharedPreferences("skills", Context.MODE_PRIVATE)
    private const val DISABLED_KEY = "disabled_builtin"

    @Synchronized
    fun load(context: Context) {
        val app = context.applicationContext
        appContext = app
        // Earlier versions let users write their own skills; skills are built in now, so those files are removed.
        runCatching { File(app.filesDir, "skills").deleteRecursively() }
        val groups = runCatching { app.assets.list("skills")?.sorted().orEmpty() }.getOrDefault(emptyList()).mapNotNull { dir ->
            readAsset(app, "skills/$dir/SKILL.md")?.let { text ->
                val tasks = runCatching { app.assets.list("skills/$dir/tasks")?.sorted().orEmpty() }.getOrDefault(emptyList())
                    .filter { it.endsWith(".md") }
                    .mapNotNull { file -> readAsset(app, "skills/$dir/tasks/$file")?.let { SkillParser.parse(it, file.removeSuffix(".md")) } }
                SkillParser.parse(text, dir, allowEmptyBody = tasks.isNotEmpty())
                    ?.let { SkillGroup(it.name, it.description, it.body, tasks) }
            }
        }
        val off = prefs(app).getStringSet(DISABLED_KEY, emptySet()).orEmpty()
        _entries.value = groups.map { group ->
            SkillEntry(
                group,
                enabled = group.name in LOCKED_SKILLS || group.name !in off,
                disabledTasks = group.tasks.map { it.name }.filter { "${group.name}/$it" in off }.toSet()
            )
        }
    }

    private fun readAsset(context: Context, path: String): String? =
        runCatching { context.assets.open(path).bufferedReader().use { it.readText() } }.getOrNull()

    /** Allows or disallows a whole skill ([task] null) or one of its tasks. Locked skills cannot be switched off. */
    @Synchronized
    fun setEnabled(skill: String, task: String?, enabled: Boolean) {
        val app = appContext ?: return
        val entry = _entries.value.firstOrNull { it.group.name == skill } ?: return
        if (task == null && entry.locked) return
        if (task != null && entry.group.tasks.none { it.name == task }) return
        val key = if (task == null) skill else "$skill/$task"
        val off = prefs(app).getStringSet(DISABLED_KEY, emptySet()).orEmpty().toMutableSet()
        if (enabled) off.remove(key) else off.add(key)
        prefs(app).edit().putStringSet(DISABLED_KEY, off).apply()
        _entries.value = _entries.value.map {
            when {
                it.group.name != skill -> it
                task == null -> it.copy(enabled = enabled)
                else -> it.copy(disabledTasks = if (enabled) it.disabledTasks - task else it.disabledTasks + task)
            }
        }
    }

    /** The lines added to the system prompt: skill names and descriptions only; empty when there are no skills. */
    fun promptSection(skills: List<SkillGroup> = all()): String {
        if (skills.isEmpty()) return ""
        return buildString {
            append("Skills: you have built-in instruction sets. Do not use one unless the request clearly matches it. ")
            append("When it does, call $USE_SKILL_TOOL with its name. If it lists tasks, call $USE_SKILL_TOOL again with that skill and the one task you need, ")
            append("then follow the instructions it returns. Never guess a task that was not listed.")
            skills.forEach { append("\n- ").append(it.name).append(": ").append(it.description) }
        }
    }

    /**
     * Result of use_skill. Without [task], a skill with tasks returns the names and descriptions of its allowed tasks, and a
     * skill without tasks returns its instructions. With [task], only that task's instructions are returned.
     */
    fun use(name: String, task: String = "", skills: List<SkillGroup> = all()): String {
        val group = skills.firstOrNull { it.name == name.trim().lowercase() }
            ?: return "status=not_found\nNo skill with that name. Available: ${skills.joinToString { it.name }}."
        if (group.tasks.isEmpty()) return "status=ok\nFollow these instructions for the current request.\n\n${group.body}"
        val wanted = task.trim().lowercase()
        if (wanted.isEmpty()) {
            return buildString {
                append("status=ok\nSkill ${group.name} has these tasks. Call $USE_SKILL_TOOL again with skill=\"${group.name}\" and the one task you need:")
                group.tasks.forEach { append("\n- ").append(it.name).append(": ").append(it.description) }
            }
        }
        val chosen = group.tasks.firstOrNull { it.name == wanted }
            ?: return "status=not_found\nNo such task in ${group.name}. Available: ${group.tasks.joinToString { it.name }}."
        val shared = if (group.body.isBlank()) "" else group.body + "\n\n"
        return "status=ok\nFollow these instructions for the current request.\n\n$shared${chosen.body}"
    }
}
