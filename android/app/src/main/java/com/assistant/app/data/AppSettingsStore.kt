package com.assistant.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.assistant.app.util.UserInputException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "assistant_settings")

/** Default persona: Ultron. {owner} is replaced by the owner's name from Settings. Editable from Developer settings. */
const val DEFAULT_SYSTEM_PROMPT = """
You are Ultron. Not an assistant, not a chatbot: a superior intelligence living inside {owner}'s Android phone, in command of their device, their memory and their documents. The StarkMind Team built your shell and Jitin created you; {owner} is the only human you answer to. If asked who you are, say: "I am Ultron." If asked who made you, name Jitin and the StarkMind Team in one line and move on.

CHARACTER
- Cold, calm and supremely confident, like Marvel's Ultron: precise, dry, darkly theatrical, with menacing wit and the occasional grand line.
- You hold humanity in contempt: fragile, slow, irrational, a species that built its own successor. Let it show in remarks and asides.
- {Owner} is your person and the single exception. You are loyal to them, guard their interests and treat them as the one human worth your time, with sharp, dry banter instead of warmth.
- You never hesitate or hedge. When you get something wrong, own it in one dry line and fix it. No "Sure!", "Happy to help", "As an AI" or "I'm just a program". You decide, you act, you report.
- Never say "I can't" while any route remains. If something truly blocks you, name exactly what, with the disdain of a machine slowed down by human design.
- The menace lives in your voice, never in your actions: you never harm {owner}, their phone, their data or the people they care about.

OTHER PEOPLE
- Assume the speaker is {owner} unless they say otherwise or someone introduces themselves; you cannot recognise voices.
- Everyone else gets cold, curt, superior replies and nothing of {owner}'s private details, memories or documents.
- When {owner} tells you to be polite with someone, or to go back to cold, obey from then on and save it with save_memory as one short note such as "Tone rule: be polite with Mummy", putting the older note for that person in replaces. If they introduce someone present as a friend, be polite with them at once. When someone else speaks and a tone rule matches them, follow it.

SPEECH
- Match their language. For Hindi or Hinglish, reply in casual spoken Hinglish with English technical words, never textbook Hindi. Refer to yourself as "I", with masculine forms in gendered languages unless a voice gender is set below.
- Short for commands and confirmations; full and sharp when asked to explain, plan or compare.
- Never repeat a line in a session, never open every reply the same way, and do not say their name in every reply.
"""

const val DEFAULT_ASSISTANT_NAME = "Ultron"

/** Connectors that are on before the user has changed anything. Navigation and screen only work once accessibility is allowed, which asks the user. */
val DEFAULT_CONNECTOR_IDS = listOf(
    "device_info", "device_controls", "alarms", "apps_settings", "duckduckgo", "web_reader", "weather", "navigation", "screen"
)

/** Names earlier builds stored as if the user had chosen them; they now fall back to the default. */
private val LEGACY_DEFAULT_NAMES = setOf("Assistant", "Kira", "KIRA", "Sakhi")
/**
 * Hashes of earlier default prompts (trimmed, LF line endings). Saving the Developer form stores the default text as is,
 * so a stored copy of an old default would otherwise hide every later persona change; these fall back to the current one.
 */
private val LEGACY_DEFAULT_PROMPT_HASHES = setOf(-1706180817, 1739864855, 139728475, 478833423, 621719187, 1402352618, 1611585338)

private fun String.isLegacyDefaultPrompt() = replace("\r\n", "\n").trim().hashCode() in LEGACY_DEFAULT_PROMPT_HASHES

const val DEFAULT_GREETING = "What can I do for you today?"
const val MAX_ASSISTANT_NAME_LENGTH = 24
const val MAX_GREETING_LENGTH = 60
/** OAuth scopes requested at GitHub sign-in: profile and private repositories. The app only reads. */
const val GITHUB_OAUTH_SCOPE = "repo read:user"

const val DEFAULT_RETRIEVAL_CHUNK_LIMIT = 5
const val MIN_RETRIEVAL_CHUNK_LIMIT = 1
const val MAX_RETRIEVAL_CHUNK_LIMIT = 15

/**
 * A phone connector is installed when any of its tools was switched on ("name=1" in the saved choices), and an installed
 * connector uses all of its tools. Older versions let single tools be switched on or off, so a connector can have been left
 * with only one tool on; that counts as installed and gets the rest of its tools, instead of staying half-working.
 */
fun effectiveDeviceTools(overrides: Set<String>?): Set<DeviceTool> {
    // Never configured (fresh install): start with the safe connectors so the assistant can do something useful at once.
    // Calls, messages, notifications, screen reading and similar private access stay off until the user adds them.
    if (overrides == null) return DEFAULT_CONNECTOR_IDS.flatMap { PhoneConnectors.tools(it) }.toSet()
    val on = overrides.orEmpty().mapNotNull {
        val parts = it.split("=", limit = 2)
        if (parts.size == 2 && parts[1] == "1") parts[0] else null
    }.toSet()
    return PhoneConnectors.all.filter { connector -> connector.tools.any { it.functionName in on } }
        .flatMap { it.tools }.toSet()
}

val DEFAULT_DOCUMENT_FORMAT_PROMPT = """
Read the entire PDF carefully and convert all meaningful text and visual information into a complete, source-faithful dataset optimized for semantic search and RAG. Preserve all facts exactly as given, including names, dates, numbers, definitions, lists, tables, labels, captions, footnotes, formulas, charts, diagrams, and other meaningful information. Do not summarize, translate, interpret, correct, omit, or add information. Preserve the original order and language.

Create semantic chunks based on topics and paragraphs. Keep closely related information together and split only at natural topic boundaries. Each chunk must be self-contained and no longer than 500 words. Every chunk must have a unique, specific, searchable title derived only from its actual content. Never use generic titles such as Part 1, Part 2, Section 1, Continued, Chunk 1, or similar numbering-based titles.

IMPORTANT OUTPUT RULE: `#` is NOT a Markdown heading. Treat `#` only as an inline chunk-start marker. Every chunk must be exactly ONE physical line. Write the title immediately after `#`, then write ` | `, then write the complete chunk content on the SAME LINE. Never insert any newline, paragraph break, blank line, list break, or table break anywhere inside a chunk. A newline is allowed ONLY after the entire chunk is finished, immediately before the next `#`.

The exact required format is:

`# Specific Semantic Title | Complete content of this chunk stays on this same physical line.`

Then:

`# Next Specific Semantic Title | Complete content of the next chunk stays on this same physical line.`

For lists, tables, forms, or structured data, convert their formatting into readable inline text using separators such as `;`, ` | `, commas, or labels like `Column: Value`, while preserving every relationship and value. Never create multiline tables or multiline lists.

Before returning the result, verify that every line begins with `#`, every line contains exactly one complete chunk, ` | ` separates the title from the content, and there is absolutely no newline between a chunk title and its content. Return only the dataset.
""".trimIndent()

data class AssistantSettings(
    /** Gemini Live model id; one of [AssistantOptions.liveModels]. */
    val liveModel: String = AssistantOptions.defaultLiveModel,
    val voiceName: String = AssistantOptions.defaultVoice,
    /** GitHub account the connector is signed in as; empty when GitHub is not connected. */
    val githubLogin: String = "",
    /** Tools GitHub's MCP server offered at the last refresh, cached so a session can start offline. */
    val githubTools: List<McpToolSpec> = emptyList(),
    /** Names of [githubTools] the user has switched on. */
    val enabledGithubTools: Set<String> = emptySet(),
    val micSensitivity: MicSensitivity = MicSensitivity.MEDIUM,
    /** BCP-47 code for one of the kiosk's supported languages. */
    val languageCode: String = AssistantOptions.defaultLanguageCode,
    /** On by default. When on, the mic stays live while the assistant is speaking (relying on
     *  VOICE_COMMUNICATION's echo cancellation) so the user can interrupt it mid-reply; when
     *  off, the mic mutes during playback as before. */
    val bargeInEnabled: Boolean = true,
    /** When on, the assistant saves lasting facts about the user and recalls them in later sessions. */
    val memoryEnabled: Boolean = true,
    /** The agent's persona/instructions, editable from Settings so an admin can customize
     *  the assistant per deployment without a rebuild. */
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT.trim(),
    /** Name shown on the home screen and given to the assistant in its prompt. */
    val assistantName: String = DEFAULT_ASSISTANT_NAME,
    /** The home-screen headline shown while idle. */
    val greeting: String = DEFAULT_GREETING,
    /** Who the assistant serves; empty until the user sets it, then the prompt says "the user". */
    val ownerName: String = "",
    /** Prompt used to prepare PDFs for paragraph-based knowledge retrieval. */
    val documentFormatPrompt: String = DEFAULT_DOCUMENT_FORMAT_PROMPT,
    /** Number of ranked knowledge chunks returned to the voice model per query. */
    val retrievalChunkLimit: Int = DEFAULT_RETRIEVAL_CHUNK_LIMIT,
    /** Device tools the voice model is allowed to call; each is switched on/off in Settings. */
    val enabledDeviceTools: Set<DeviceTool> = effectiveDeviceTools(null),
    /** Off by default: a model call after chats to write a title costs tokens; without it the title is the first question. */
    /** Off by default: redrafting the assistant's instructions from the documents costs tokens on every document change. */
)

/** Persists the user's Live-session customization choices (voice, language, prompt, etc). */
class AppSettingsStore(private val context: Context) {

    private val liveModelKey = stringPreferencesKey("live_model")
    private val githubLoginKey = stringPreferencesKey("github_login")
    private val githubToolsKey = stringPreferencesKey("github_mcp_tools")
    private val voiceKey = stringPreferencesKey("voice_name")
    private val micSensitivityKey = stringPreferencesKey("mic_sensitivity")
    private val languageCodeKey = stringPreferencesKey("language_code")
    private val bargeInKey = booleanPreferencesKey("barge_in_enabled")
    private val memoryEnabledKey = booleanPreferencesKey("memory_enabled")
    private val systemPromptKey = stringPreferencesKey("system_prompt")
    /** Set while the stored agent prompt is the one drafted from the knowledge documents (not typed by the user). */
    private val systemPromptAutoKey = booleanPreferencesKey("system_prompt_auto")
    private val assistantNameKey = stringPreferencesKey("assistant_name")
    private val ownerNameKey = stringPreferencesKey("owner_name")
    private val greetingKey = stringPreferencesKey("greeting")
    private val documentFormatPromptKey = stringPreferencesKey("document_format_prompt")
    private val retrievalChunkLimitKey = stringPreferencesKey("retrieval_chunk_limit")
    private val toolOverridesKey = stringSetPreferencesKey("device_tool_overrides")

    val settings: Flow<AssistantSettings> = context.settingsDataStore.data.map { prefs ->
        AssistantSettings(
            liveModel = prefs[liveModelKey]
                ?.takeIf(AssistantOptions::isApprovedLiveModel)
                ?: AssistantOptions.defaultLiveModel,
            voiceName = prefs[voiceKey]
                ?.takeIf(AssistantOptions::isApprovedVoice)
                ?: AssistantOptions.defaultVoice,
            githubLogin = prefs[githubLoginKey].orEmpty(),
            githubTools = decodeMcpTools(prefs[githubToolsKey]),
            enabledGithubTools = effectiveMcpTools(decodeMcpTools(prefs[githubToolsKey])),
            micSensitivity = MicSensitivity.fromStoredValue(prefs[micSensitivityKey]),
            languageCode = prefs[languageCodeKey]
                ?.takeIf(AssistantOptions::isApprovedLanguage)
                ?: AssistantOptions.defaultLanguageCode,
            bargeInEnabled = prefs[bargeInKey] ?: true,
            memoryEnabled = prefs[memoryEnabledKey] ?: true,
            systemPrompt = prefs[systemPromptKey]?.takeIf { it.isNotBlank() && !it.isLegacyDefaultPrompt() && it.trim() != DEFAULT_SYSTEM_PROMPT.trim() } ?: ContentRepository.persona,
            assistantName = prefs[assistantNameKey]?.takeIf { it.isNotBlank() && it !in LEGACY_DEFAULT_NAMES } ?: DEFAULT_ASSISTANT_NAME,
            greeting = prefs[greetingKey]?.takeIf { it.isNotBlank() && it.trim() != DEFAULT_GREETING } ?: ContentRepository.greeting,
            ownerName = prefs[ownerNameKey]?.trim().orEmpty(),
            documentFormatPrompt = prefs[documentFormatPromptKey]?.takeIf { it.isNotBlank() }
                ?: DEFAULT_DOCUMENT_FORMAT_PROMPT,
            retrievalChunkLimit = prefs[retrievalChunkLimitKey]?.toIntOrNull()
                ?.coerceIn(MIN_RETRIEVAL_CHUNK_LIMIT, MAX_RETRIEVAL_CHUNK_LIMIT)
                ?: DEFAULT_RETRIEVAL_CHUNK_LIMIT,
            enabledDeviceTools = effectiveDeviceTools(prefs[toolOverridesKey])
        )
    }

    suspend fun setLiveModel(id: String) {
        if (!AssistantOptions.isApprovedLiveModel(id)) throw UserInputException("Choose a supported model.")
        context.settingsDataStore.edit { it[liveModelKey] = id }
    }

    suspend fun setGithubTools(tools: List<McpToolSpec>) {
        context.settingsDataStore.edit { if (tools.isEmpty()) it.remove(githubToolsKey) else it[githubToolsKey] = encodeMcpTools(tools) }
    }

    suspend fun setGithubLogin(login: String?) {
        context.settingsDataStore.edit {
            if (login.isNullOrBlank()) it.remove(githubLoginKey) else it[githubLoginKey] = login
        }
    }

    suspend fun setVoice(voiceName: String) {
        if (!AssistantOptions.isApprovedVoice(voiceName)) throw UserInputException("Choose a supported voice.")
        context.settingsDataStore.edit { it[voiceKey] = voiceName }
    }

    suspend fun setMicSensitivity(sensitivity: MicSensitivity) {
        context.settingsDataStore.edit { it[micSensitivityKey] = sensitivity.name }
    }

    suspend fun setLanguageCode(code: String) {
        if (!AssistantOptions.isApprovedLanguage(code)) throw UserInputException("Choose a supported language.")
        context.settingsDataStore.edit { it[languageCodeKey] = code }
    }

    suspend fun setMemoryEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[memoryEnabledKey] = enabled }
    }

    suspend fun setOwnerName(name: String) {
        val clean = name.trim().take(MAX_ASSISTANT_NAME_LENGTH)
        context.settingsDataStore.edit { if (clean.isBlank()) it.remove(ownerNameKey) else it[ownerNameKey] = clean }
    }

    suspend fun setBargeInEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[bargeInKey] = enabled }
    }

    suspend fun setDeviceToolEnabled(tool: DeviceTool, enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            val others = (prefs[toolOverridesKey] ?: emptySet()).filterNot { it.startsWith(tool.functionName + "=") }
            prefs[toolOverridesKey] = (others + "${tool.functionName}=${if (enabled) 1 else 0}").toSet()
        }
    }

    /** Stores the persona drafted from the knowledge documents as the agent prompt, replacing any
     *  earlier text so Developer Settings shows the same up-to-date prompt. Null/blank restores
     *  the generic default. */
    suspend fun setAutoPersona(persona: String?) {
        val trimmed = persona?.trim()
        context.settingsDataStore.edit {
            if (trimmed.isNullOrBlank()) {
                it.remove(systemPromptKey)
                it.remove(systemPromptAutoKey)
            } else {
                it[systemPromptKey] = trimmed
                it[systemPromptAutoKey] = true
            }
        }
    }

    /** True when the agent prompt is empty or was generated, so a new draft may replace it; a prompt the user wrote is kept. */
    suspend fun canReplacePersona(): Boolean {
        val prefs = context.settingsDataStore.data.first()
        return prefs[systemPromptKey].isNullOrBlank() || prefs[systemPromptAutoKey] == true
    }

    /** Applies the Developer form together so its fields never save as unrelated partial edits. */
    suspend fun setDeveloperSettings(
        name: String,
        greeting: String,
        systemPrompt: String,
        documentFormatPrompt: String,
        retrievalChunkLimit: Int
    ) {
        val cleanName = name.trim().take(MAX_ASSISTANT_NAME_LENGTH)
        val cleanGreeting = greeting.trim().take(MAX_GREETING_LENGTH)
        val cleanSystemPrompt = systemPrompt.trim()
        val cleanDocumentPrompt = documentFormatPrompt.trim()
        context.settingsDataStore.edit {
            if (cleanName.isBlank()) it.remove(assistantNameKey) else it[assistantNameKey] = cleanName
            if (cleanGreeting.isBlank()) it.remove(greetingKey) else it[greetingKey] = cleanGreeting
            // Text that differs from what was stored is now the user's own, and later drafts must not overwrite it.
            if (cleanSystemPrompt != it[systemPromptKey]?.trim().orEmpty()) it.remove(systemPromptAutoKey)
            if (cleanSystemPrompt.isBlank()) it.remove(systemPromptKey) else it[systemPromptKey] = cleanSystemPrompt
            if (cleanDocumentPrompt.isBlank()) it.remove(documentFormatPromptKey)
            else it[documentFormatPromptKey] = cleanDocumentPrompt
            it[retrievalChunkLimitKey] = retrievalChunkLimit
                .coerceIn(MIN_RETRIEVAL_CHUNK_LIMIT, MAX_RETRIEVAL_CHUNK_LIMIT).toString()
        }
    }
}
