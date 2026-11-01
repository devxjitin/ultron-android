package com.assistant.app.data

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val MAX_REMOTE_PERSONA_CHARS = 20_000
private const val MAX_REMOTE_POLICY_CHARS = 12_000
private const val MAX_REMOTE_DOCUMENT_PROMPT_CHARS = 10_000

/**
 * Text the owner edits in the Ultron web panel. A null field means "use the one bundled in the app". Skills in [skills] replace
 * a bundled skill of the same name or add a new one, and [hiddenSkills] names bundled skills the panel has unpublished.
 */
data class RemoteSnapshot(
    val persona: String? = null,
    val policy: String? = null,
    val greeting: String? = null,
    val documentFormatPrompt: String? = null,
    val assistantName: String? = null,
    val retrievalChunkLimit: Int? = null,
    val skills: List<SkillGroup> = emptyList(),
    val hiddenSkills: Set<String> = emptySet()
) {
    companion object {
        /**
         * Reads the raw Firestore maps. Anything blank, too long, malformed or meant for a newer build is dropped, so a bad edit in
         * the panel falls back to the bundled text instead of breaking the assistant.
         */
        fun from(prompts: Map<String, Any?>?, skillDocs: Map<String, Map<String, Any?>>, appVersion: Int): RemoteSnapshot {
            val promptsOk = prompts != null && fitsVersion(prompts, appVersion)
            fun field(key: String, max: Int) = (prompts?.get(key) as? String)?.trim()?.takeIf { promptsOk && it.isNotEmpty() && it.length <= max }
            val policy = field("policy", MAX_REMOTE_POLICY_CHARS)?.takeIf { USE_SKILL_TOOL in it }
            val hidden = HashSet<String>()
            val skills = skillDocs.entries.sortedBy { it.key }.mapNotNull { (id, doc) ->
                if (!fitsVersion(doc, appVersion)) return@mapNotNull null
                if (doc["published"] == false) {
                    hidden += id.trim().lowercase()
                    return@mapNotNull null
                }
                parseSkill(id, doc)
            }
            return RemoteSnapshot(
                persona = field("persona", MAX_REMOTE_PERSONA_CHARS),
                policy = policy,
                greeting = field("greeting", MAX_GREETING_LENGTH),
                documentFormatPrompt = field("documentFormatPrompt", MAX_REMOTE_DOCUMENT_PROMPT_CHARS),
                assistantName = field("assistantName", MAX_ASSISTANT_NAME_LENGTH),
                retrievalChunkLimit = (prompts?.get("retrievalChunkLimit") as? Number)?.toInt()
                    ?.takeIf { promptsOk && it in MIN_RETRIEVAL_CHUNK_LIMIT..MAX_RETRIEVAL_CHUNK_LIMIT },
                skills = skills,
                hiddenSkills = hidden
            )
        }

        private fun fitsVersion(doc: Map<String, Any?>, appVersion: Int): Boolean =
            ((doc["minAppVersion"] as? Number)?.toLong() ?: 0L) <= appVersion

        private fun skillText(name: String, description: String, body: String) =
            "---\nname: $name\ndescription: ${description.replace('\n', ' ').trim()}\n---\n${body.trim()}"

        private fun parseSkill(id: String, doc: Map<String, Any?>): SkillGroup? {
            val description = doc["description"] as? String ?: return null
            val body = doc["body"] as? String ?: ""
            val tasks = (doc["tasks"] as? Map<*, *>).orEmpty().entries.sortedBy { it.key.toString() }.mapNotNull { (key, value) ->
                val task = value as? Map<*, *> ?: return@mapNotNull null
                if (task["published"] == false) return@mapNotNull null
                SkillParser.parse(skillText(key.toString(), task["description"] as? String ?: "", task["body"] as? String ?: ""))
            }
            return SkillParser.parse(skillText(id, description, body), allowEmptyBody = tasks.isNotEmpty())
                ?.let { SkillGroup(it.name, it.description, it.body, tasks) }
        }
    }
}

/**
 * Prompt and skill text that can change without an app update. The owner edits it in the Ultron web panel (Firestore, project
 * ultron-5350c); the app listens, keeps Firestore's own offline copy, and falls back to the bundled text for anything missing.
 * Only text lives here. Connectors and tool declarations are code and stay in the app.
 */
object RemoteContent {
    private val _snapshot = MutableStateFlow(RemoteSnapshot())
    val snapshot: StateFlow<RemoteSnapshot> = _snapshot.asStateFlow()

    private var prompts: Map<String, Any?>? = null
    private var skillDocs: Map<String, Map<String, Any?>> = emptyMap()
    private var appVersion = 0
    private var started = false

    /** Answer policy to use: the panel's when it is valid, otherwise the bundled one. */
    fun policy(): String = _snapshot.value.policy ?: KNOWLEDGE_GROUNDING_POLICY

    @Synchronized
    fun start(context: Context, versionCode: Int) {
        if (started) return
        val app = context.applicationContext
        if (FirebaseApp.getApps(app).isEmpty()) return
        started = true
        appVersion = versionCode
        runCatching {
            val db = FirebaseFirestore.getInstance()
            db.collection("remote").document("prompts").addSnapshotListener { doc, error ->
                if (error != null) { Log.w(TAG, "prompts listener", error); return@addSnapshotListener }
                update(prompts = doc?.data)
            }
            db.collection("remote_skills").addSnapshotListener { docs, error ->
                if (error != null) { Log.w(TAG, "skills listener", error); return@addSnapshotListener }
                update(skills = docs?.documents.orEmpty().associate { it.id to it.data.orEmpty() })
            }
        }.onFailure { Log.w(TAG, "remote content unavailable", it) }
    }

    @Synchronized
    private fun update(prompts: Map<String, Any?>? = this.prompts, skills: Map<String, Map<String, Any?>> = this.skillDocs) {
        this.prompts = prompts
        this.skillDocs = skills
        _snapshot.value = RemoteSnapshot.from(prompts, skills, appVersion)
    }

    private const val TAG = "RemoteContent"
}
