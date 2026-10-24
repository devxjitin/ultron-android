package com.assistant.app.data

import android.content.Context
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

/**
 * The skills available to the assistant. Only the name and description go into the system prompt;
 * the instructions are returned by [use] when the model calls use_skill, so unused skills cost almost nothing.
 * Bundled skills live in assets/skills/<name>/SKILL.md; the user can add or override one in files/skills/<name>/SKILL.md.
 */
object SkillRegistry {
    @Volatile private var skills: List<Skill> = emptyList()

    fun all(): List<Skill> = skills

    fun load(context: Context) {
        val found = LinkedHashMap<String, Skill>()
        runCatching {
            context.assets.list("skills")?.sorted()?.forEach { dir ->
                runCatching { context.assets.open("skills/$dir/SKILL.md").bufferedReader().use { it.readText() } }
                    .getOrNull()?.let { SkillParser.parse(it, dir) }?.let { found[it.name] = it }
            }
        }
        File(context.filesDir, "skills").listFiles()?.sortedBy { it.name }?.forEach { dir ->
            val file = File(dir, "SKILL.md")
            if (file.isFile) runCatching { file.readText() }.getOrNull()?.let { SkillParser.parse(it, dir.name) }?.let { found[it.name] = it }
        }
        skills = found.values.toList()
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
