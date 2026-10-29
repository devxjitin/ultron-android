package com.assistant.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** One skill: a short [description] that is always visible to the model, and a [body] that is loaded only when the model asks for it. */
data class Skill(val name: String, val description: String, val body: String)

const val USE_SKILL_TOOL = "use_skill"
/** Most characters of one skill body returned to the model. */
const val MAX_SKILL_BODY_CHARS = 8_000
private const val MAX_SKILL_DESCRIPTION_CHARS = 300

/** Reads a SKILL.md: `---` frontmatter with `name:` and `description:`, then the instructions. */
object SkillParser {
    private val NAME_PATTERN = Regex("[a-z0-9][a-z0-9_-]{0,47}")

    fun parse(text: String, fallbackName: String? = null): Skill? {
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
        if (body.isEmpty()) return null
        return Skill(name, description, body)
    }
}

/** A skill as the Skills page shows it. Skills ship inside the app, so they are always on and cannot be edited. */
data class SkillEntry(val skill: Skill)

/**
 * The skills available to the assistant. Only the name and description go into the system prompt;
 * the instructions are returned by [use] when the model calls use_skill, so unused skills cost almost nothing.
 * Skills are locked: they are read only from assets/skills/<name>/SKILL.md, shipped with the app. The user can view
 * them but cannot add, change, switch off or delete any, and the model has no tool that writes them.
 */
object SkillRegistry {
    private val _entries = MutableStateFlow<List<SkillEntry>>(emptyList())
    /** Every skill, for the Skills page. */
    val entries: StateFlow<List<SkillEntry>> = _entries.asStateFlow()

    fun all(): List<Skill> = _entries.value.map { it.skill }

    @Synchronized
    fun load(context: Context) {
        val app = context.applicationContext
        val found = LinkedHashMap<String, Skill>()
        runCatching {
            app.assets.list("skills")?.sorted()?.forEach { dir ->
                runCatching { app.assets.open("skills/$dir/SKILL.md").bufferedReader().use { it.readText() } }
                    .getOrNull()?.let { SkillParser.parse(it, dir) }?.let { found[it.name] = it }
            }
        }
        // Earlier versions let the user save their own skills; those files and the old switches are no longer used.
        runCatching { File(app.filesDir, "skills").deleteRecursively() }
        runCatching { app.getSharedPreferences("skills", Context.MODE_PRIVATE).edit().clear().apply() }
        _entries.value = found.values.map { SkillEntry(it) }
    }

    /** The lines added to the system prompt; empty when there are no skills. */
    fun promptSection(skills: List<Skill> = all()): String {
        if (skills.isEmpty()) return ""
        return buildString {
            append("Skills: you have extra instruction sets. Do not use one unless the request clearly matches it. ")
            append("When it does, first call $USE_SKILL_TOOL with its name, then follow the instructions it returns.")
            skills.forEach { append("\n- ").append(it.name).append(": ").append(it.description) }
        }
    }

    /** Result of use_skill: the instructions, or a list of valid names when the name is wrong. */
    fun use(name: String, skills: List<Skill> = all()): String {
        val skill = skills.firstOrNull { it.name == name.trim().lowercase() }
            ?: return "status=not_found\nNo skill with that name. Available: ${skills.joinToString { it.name }}."
        return "status=ok\nFollow these instructions for the current request.\n\n${skill.body}"
    }
}
