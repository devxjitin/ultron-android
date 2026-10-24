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

/** A skill as the Skills page shows it: [builtIn] ones ship with the app and can only be switched off. */
data class SkillEntry(val skill: Skill, val builtIn: Boolean, val enabled: Boolean)

/**
 * The skills available to the assistant. Only the name and description go into the system prompt;
 * the instructions are returned by [use] when the model calls use_skill, so unused skills cost almost nothing.
 * Bundled skills live in assets/skills/<name>/SKILL.md; the user adds their own on the Skills page, kept in files/skills/<name>/SKILL.md.
 * A user skill with a bundled skill's name replaces it.
 */
object SkillRegistry {
    private val _entries = MutableStateFlow<List<SkillEntry>>(emptyList())
    /** Every skill, on or off, for the Skills page. */
    val entries: StateFlow<List<SkillEntry>> = _entries.asStateFlow()

    private var appContext: Context? = null

    /** The skills that are switched on: these are what the model sees. */
    fun all(): List<Skill> = _entries.value.filter { it.enabled }.map { it.skill }

    private fun prefs(context: Context) = context.getSharedPreferences("skills", Context.MODE_PRIVATE)
    private fun userDir(context: Context) = File(context.filesDir, "skills")

    @Synchronized
    fun load(context: Context) {
        val app = context.applicationContext
        appContext = app
        val found = LinkedHashMap<String, Pair<Skill, Boolean>>()
        runCatching {
            app.assets.list("skills")?.sorted()?.forEach { dir ->
                runCatching { app.assets.open("skills/$dir/SKILL.md").bufferedReader().use { it.readText() } }
                    .getOrNull()?.let { SkillParser.parse(it, dir) }?.let { found[it.name] = it to true }
            }
        }
        userDir(app).listFiles()?.sortedBy { it.name }?.forEach { dir ->
            val file = File(dir, "SKILL.md")
            if (file.isFile) runCatching { file.readText() }.getOrNull()?.let { SkillParser.parse(it, dir.name) }?.let { found[it.name] = it to false }
        }
        val off = prefs(app).getStringSet("disabled", emptySet()).orEmpty()
        _entries.value = found.values.map { (skill, builtIn) -> SkillEntry(skill, builtIn, skill.name !in off) }
    }

    @Synchronized
    fun setEnabled(name: String, enabled: Boolean) {
        val app = appContext ?: return
        val off = prefs(app).getStringSet("disabled", emptySet()).orEmpty().toMutableSet()
        if (enabled) off.remove(name) else off.add(name)
        prefs(app).edit().putStringSet("disabled", off).apply()
        _entries.value = _entries.value.map { if (it.skill.name == name) it.copy(enabled = enabled) else it }
    }

    /** Saves a user skill; returns an error for the page to show, or null when saved. */
    @Synchronized
    fun save(name: String, description: String, body: String): String? {
        val app = appContext ?: return "Skills are not ready yet."
        val skill = SkillParser.parse("---\nname: ${name.trim()}\ndescription: ${description.trim().replace('\n', ' ')}\n---\n${body.trim()}")
            ?: return "Use a short lowercase name (letters, digits, - or _), a description and instructions."
        val dir = File(userDir(app), skill.name)
        val written = runCatching {
            dir.mkdirs()
            File(dir, "SKILL.md").writeText("---\nname: ${skill.name}\ndescription: ${skill.description}\n---\n${skill.body}\n")
        }
        if (written.isFailure) return "Could not save the skill."
        load(app)
        return null
    }

    /** Deletes a user skill (a bundled one comes back if this was its replacement). */
    @Synchronized
    fun delete(name: String) {
        val app = appContext ?: return
        File(userDir(app), name).deleteRecursively()
        load(app)
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
