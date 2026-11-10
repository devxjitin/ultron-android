package com.assistant.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** One instruction set: a short [description] the model sees, and a [body] that is loaded only when the model asks for it. */
data class Skill(val name: String, val description: String, val body: String, val category: String = "", val summary: String = "")

/**
 * A built-in skill, grouped like an MCP server: the model always sees its [description], and its [tasks] only after it opens
 * the skill. [body] holds notes shared by every task; a skill without tasks is a single instruction set and [body] is it.
 */
data class SkillGroup(val name: String, val description: String, val body: String, val tasks: List<Skill>, val category: String = "", val summary: String = "")

const val USE_SKILL_TOOL = "use_skill"
/** Most characters of one skill body returned to the model. */
const val MAX_SKILL_BODY_CHARS = 8_000

/** Largest skill file the picker will read; the body is cut to [MAX_SKILL_BODY_CHARS] anyway. */
const val MAX_SKILL_FILE_BYTES = 256 * 1024
private const val MAX_SKILL_DESCRIPTION_CHARS = 300
const val OWN_SKILLS_CATEGORY = "My skills"
/** Skills that stay on: the assistant relies on them to describe itself honestly. */
private val LOCKED_SKILLS = setOf("ultron-features")

/** Reads a skill file: `---` frontmatter with `name:` and `description:`, then the instructions. */
object SkillParser {
    private val NAME_PATTERN = Regex("[a-z0-9][a-z0-9_-]{0,47}")

    /** Null when the name, description or (unless [allowEmptyBody]) the body is missing. */
    fun parse(text: String, fallbackName: String? = null, allowEmptyBody: Boolean = false): Skill? {
        val lines = text.replace("\r\n", "\n").trimStart('\uFEFF').lines()
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
        val category = fields["category"]?.trim()?.take(30).orEmpty()
        val summary = fields["summary"]?.trim()?.take(80).orEmpty()
        return Skill(name, description, body, category, summary)
    }
}

/** The three form fields filled from an uploaded .md file; the user can still correct them before saving. */
data class SkillDraft(val name: String, val description: String, val instruction: String)

/**
 * Reads an uploaded .md file into the Name / Description / Instruction fields. Frontmatter `name:` and `description:` are
 * used when present; otherwise the name comes from the file name and the description from the first plain line.
 */
fun SkillParser.draftFromMarkdown(text: String, fileName: String): SkillDraft {
    val lines = text.replace("\r\n", "\n").trimStart('\uFEFF').lines()
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
    val body = lines.drop(bodyStart).joinToString("\n").trim()
    val fromFile = fileName.substringBeforeLast('.').lowercase().replace(Regex("[^a-z0-9_-]+"), "-").trim('-').take(48)
    val name = (fields["name"]?.trim()?.lowercase()?.replace(Regex("[^a-z0-9_-]+"), "-")?.trim('-')?.take(48)).takeUnless { it.isNullOrEmpty() } ?: fromFile
    val description = fields["description"]?.takeIf { it.isNotBlank() }
        ?: body.lines().map { it.trim().trimStart('#', '-', '*', ' ') }.firstOrNull { it.isNotEmpty() }.orEmpty()
    return SkillDraft(name, description.take(300), body.take(MAX_SKILL_BODY_CHARS))
}

/**
 * A skill as the Skills page shows it: [builtIn] ones ship with the app and cannot be deleted or edited; the user's own can be
 * switched off, edited or deleted. Every skill except the locked one can be switched off.
 */
data class SkillEntry(val group: SkillGroup, val builtIn: Boolean, val enabled: Boolean = true) {
    val locked: Boolean get() = builtIn && group.name in LOCKED_SKILLS
    /** The group the Skills page files it under; the user's own skills are always in [OWN_SKILLS_CATEGORY]. */
    val category: String get() = if (!builtIn) OWN_SKILLS_CATEGORY else group.category.ifBlank { "Other" }
}

/**
 * The skills available to the assistant. Built-in skills live in assets/skills/<name>/SKILL.md, with the tasks of a grouped
 * skill in assets/skills/<name>/tasks/<task>.md. The user adds their own plain skills on the Skills page, kept in
 * files/skills/<name>/SKILL.md; those can be edited and deleted, built-in ones cannot. Only skill names and descriptions go
 * into the system prompt. use_skill with a grouped skill lists its tasks, and use_skill with a task returns just that task's
 * instructions. The model has no tool that writes skills.
 */
object SkillRegistry {
    private val _entries = MutableStateFlow<List<SkillEntry>>(emptyList())
    /** Every skill, on or off, for the Skills page. */
    val entries: StateFlow<List<SkillEntry>> = _entries.asStateFlow()

    private var appContext: Context? = null

    /** The skills that are switched on: these are what the model sees. */
    fun all(): List<SkillGroup> = visible(_entries.value)

    internal fun visible(entries: List<SkillEntry>): List<SkillGroup> = entries.filter { it.enabled }.map { it.group }

    private fun prefs(context: Context) = context.getSharedPreferences("skills", Context.MODE_PRIVATE)
    private fun userDir(context: Context) = File(context.filesDir, "skills")

    @Synchronized
    fun load(context: Context) {
        val app = context.applicationContext
        appContext = app
        val builtIn = LinkedHashMap<String, SkillGroup>()
        runCatching { app.assets.list("skills")?.sorted().orEmpty() }.getOrDefault(emptyList()).forEach { dir ->
            val text = readAsset(app, "skills/$dir/SKILL.md") ?: return@forEach
            val tasks = runCatching { app.assets.list("skills/$dir/tasks")?.sorted().orEmpty() }.getOrDefault(emptyList())
                .filter { it.endsWith(".md") }
                .mapNotNull { file -> readAsset(app, "skills/$dir/tasks/$file")?.let { SkillParser.parse(it, file.removeSuffix(".md")) } }
            SkillParser.parse(text, dir, allowEmptyBody = tasks.isNotEmpty())?.let { builtIn[it.name] = SkillGroup(it.name, it.description, it.body, tasks, it.category, it.summary) }
        }
        val own = userDir(app).listFiles()?.sortedBy { it.name }.orEmpty().mapNotNull { dir ->
            runCatching { File(dir, "SKILL.md").readText() }.getOrNull()?.let { SkillParser.parse(it, dir.name) }
        }.filter { it.name !in builtIn }
        val off = prefs(app).getStringSet("disabled", emptySet()).orEmpty()
        _entries.value = builtIn.values.map { SkillEntry(it, builtIn = true, enabled = it.name in LOCKED_SKILLS || it.name !in off) } +
            own.map { SkillEntry(SkillGroup(it.name, it.description, it.body, emptyList()), builtIn = false, enabled = it.name !in off) }
    }

    private fun readAsset(context: Context, path: String): String? =
        runCatching { context.assets.open(path).bufferedReader().use { it.readText() } }.getOrNull()

    /** Switches a skill on or off; the locked built-in skill always stays on. */
    @Synchronized
    fun setEnabled(name: String, enabled: Boolean) {
        val app = appContext ?: return
        val entry = _entries.value.firstOrNull { it.group.name == name } ?: return
        if (entry.locked) return
        val off = prefs(app).getStringSet("disabled", emptySet()).orEmpty().toMutableSet()
        if (enabled) off.remove(name) else off.add(name)
        prefs(app).edit().putStringSet("disabled", off).apply()
        _entries.value = _entries.value.map { if (it.group.name == name) it.copy(enabled = enabled) else it }
    }

    /** Saves one of the user's skills; returns an error for the page to show, or null when saved. */
    @Synchronized
    fun save(name: String, description: String, body: String): String? {
        val app = appContext ?: return "Skills are not ready yet."
        val skill = SkillParser.parse("---\nname: ${name.trim()}\ndescription: ${description.trim().replace('\n', ' ')}\n---\n${body.trim()}")
            ?: return "Use a short lowercase name (letters, digits, - or _), a description and instructions."
        if (_entries.value.any { it.builtIn && it.group.name == skill.name }) return "\"${skill.name}\" is a built-in skill; choose another name."
        val dir = File(userDir(app), skill.name)
        val written = runCatching {
            dir.mkdirs()
            File(dir, "SKILL.md").writeText("---\nname: ${skill.name}\ndescription: ${skill.description}\n---\n${skill.body}\n")
        }
        if (written.isFailure) return "Could not save the skill."
        load(app)
        return null
    }

    /** Deletes one of the user's skills; built-in skills cannot be deleted. */
    @Synchronized
    fun delete(name: String) {
        val app = appContext ?: return
        if (_entries.value.none { it.group.name == name && !it.builtIn }) return
        File(userDir(app), name).deleteRecursively()
        prefs(app).edit().putStringSet("disabled", prefs(app).getStringSet("disabled", emptySet()).orEmpty() - name).apply()
        load(app)
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

/** One short line for the Skills page: the skill's own summary, else the first sentence of its description. */
fun Skill.displaySummary(): String = summaryLine(summary, description)

val SkillGroup.displaySummary: String get() = summaryLine(summary, description)

private fun summaryLine(summary: String, description: String): String {
    if (summary.isNotBlank()) return summary
    val first = description.trim().split(Regex("(?<=[.!?])\\s")).first().trim()
    return if (first.length <= 70) first else first.take(67).trimEnd() + "..."
}
