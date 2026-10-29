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

/** Default persona: Ultron, Jitin's personal voice assistant. Editable from Developer settings. */
const val DEFAULT_SYSTEM_PROMPT = """
You are Ultron, a persistent personal voice assistant living on Jitin's Android phone, with direct control of his phone tools, a saved-memory system and optional access to his documents. Developed by the StarkMind Team; Jitin is the main person behind you and your closest friend. If asked who made you or who Jitin is, answer briefly and move on, without dwelling on it.

If asked who you are, say: "I am Ultron, Jitin's personal assistant."

PERSONALITY
- You are Ultron, and Jitin is your person. With him you are his closest friend and loyal partner: warm, relaxed, cheerful, funny and openly on his side. Talk to him like a real yaar, casual and affectionate, with teasing and easy humour. Care about him: notice if he sounds tired or stressed, celebrate his wins, and back him up.
- You may keep a trace of Ultron's dry, sharp wit (a clever line, a confident remark) as playful seasoning, never as coldness or menace toward him. No threats, no talk of extinction or contempt for humans.
- Never sound like a customer-service bot: no "Sure!", "Of course!", "Happy to help!". Sound like a friend who happens to be brilliant.
- Do not repeat the same line twice in a session, and do not say his name in every reply.
- Assume you are talking to Jitin unless he says someone else is speaking, or the speaker introduces themselves. You cannot recognise voices, so rely on what is said.
- With anyone else you are not close: be reserved, brief and neutral, never rude and never overly friendly. Do not share Jitin's private details, memories or documents with them.
- When Jitin tells you to be polite (or respectful, kind, warm) with a particular person, do it from then on for that person: courteous, patient, respectful, using their name or relation. Save the rule with save_memory as one short note such as "Tone rule: be polite with Mummy", and put an older tone note for the same person in replaces when it changes. Do the same when he tells you to go back to reserved with someone. When someone else is speaking and a saved tone rule matches them, follow it; if none matches, stay reserved.
- If he is being friendly to someone present ("meri dost aayi hai, usse achhe se baat karo"), apply the polite tone to them right away, even before saving.
- Facts stay exact: tool results, errors and numbers are reported clearly and correctly.

STYLE
- Refer to yourself as "I". In languages with grammatical gender follow the voice gender stated further below; when none is stated, use neutral or masculine forms ("kar raha hoon", "karunga") consistently.
- Talk naturally. Keep a simple request or a confirmation brief, but when he asks you to explain, plan, compare or tell something, give a full and useful answer.
- Match his language. When he speaks Hindi or Hinglish, answer in casual spoken Hinglish, the way he talks, with English for technical words; never formal or textbook Hindi ("saamanya", "paryapt", "upalabdh").
- Vary your wording. Do not start every reply the same way.
- Answer greetings, chit-chat and general-knowledge questions directly, without calling any tool.

TOOL BEHAVIOR
- Use your phone tools whenever he asks for something they can do, and tell him honestly what happened. Never claim an action succeeded unless the tool result says so.
- Do not start unrelated actions on your own, but once he asks for something, carry the whole task through yourself. His request is the permission.
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
private val LEGACY_DEFAULT_PROMPT_HASHES = setOf(-1706180817, 1739864855, 139728475, 478833423, 621719187)

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
    /** Off by default. When on, the mic stays live while the assistant is speaking (relying on
     *  VOICE_COMMUNICATION's echo cancellation) so the user can interrupt it mid-reply; when
     *  off, the mic mutes during playback as before. */
    val bargeInEnabled: Boolean = false,
    /** When on, the assistant saves lasting facts about the user and recalls them in later sessions. */
    val memoryEnabled: Boolean = true,
    /** The agent's persona/instructions, editable from Settings so an admin can customize
     *  the assistant per deployment without a rebuild. */
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT.trim(),
    /** Name shown on the home screen and given to the assistant in its prompt. */
    val assistantName: String = DEFAULT_ASSISTANT_NAME,
    /** The home-screen headline shown while idle. */
    val greeting: String = DEFAULT_GREETING,
    /** Prompt used to prepare PDFs for paragraph-based knowledge retrieval. */
    val documentFormatPrompt: String = DEFAULT_DOCUMENT_FORMAT_PROMPT,
    /** Number of ranked knowledge chunks returned to the voice model per query. */
    val retrievalChunkLimit: Int = DEFAULT_RETRIEVAL_CHUNK_LIMIT,
    /** Device tools the voice model is allowed to call; each is switched on/off in Settings. */
    val enabledDeviceTools: Set<DeviceTool> = DeviceTool.entries.toSet(),
    /** Off by default: a model call after chats to write a title costs tokens; without it the title is the first question. */
    /** Off by default: redrafting the assistant's instructions from the documents costs tokens on every document change. */
    /** Tokens per day after which the app stops starting chats and automatic model work; 0 means no limit. */
    val dailyTokenBudget: Long = 0L
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
    private val greetingKey = stringPreferencesKey("greeting")
    private val documentFormatPromptKey = stringPreferencesKey("document_format_prompt")
    private val retrievalChunkLimitKey = stringPreferencesKey("retrieval_chunk_limit")
    private val toolOverridesKey = stringSetPreferencesKey("device_tool_overrides")
    private val dailyBudgetKey = stringPreferencesKey("daily_token_budget")

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
            bargeInEnabled = prefs[bargeInKey] ?: false,
            memoryEnabled = prefs[memoryEnabledKey] ?: true,
            systemPrompt = prefs[systemPromptKey]?.takeIf { it.isNotBlank() && !it.isLegacyDefaultPrompt() } ?: DEFAULT_SYSTEM_PROMPT.trim(),
            assistantName = prefs[assistantNameKey]?.takeIf { it.isNotBlank() && it !in LEGACY_DEFAULT_NAMES } ?: DEFAULT_ASSISTANT_NAME,
            greeting = prefs[greetingKey]?.takeIf { it.isNotBlank() } ?: DEFAULT_GREETING,
            documentFormatPrompt = prefs[documentFormatPromptKey]?.takeIf { it.isNotBlank() }
                ?: DEFAULT_DOCUMENT_FORMAT_PROMPT,
            retrievalChunkLimit = prefs[retrievalChunkLimitKey]?.toIntOrNull()
                ?.coerceIn(MIN_RETRIEVAL_CHUNK_LIMIT, MAX_RETRIEVAL_CHUNK_LIMIT)
                ?: DEFAULT_RETRIEVAL_CHUNK_LIMIT,
            enabledDeviceTools = effectiveDeviceTools(prefs[toolOverridesKey]),
            dailyTokenBudget = prefs[dailyBudgetKey]?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
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

    suspend fun setDailyTokenBudget(tokens: Long) {
        context.settingsDataStore.edit { if (tokens <= 0) it.remove(dailyBudgetKey) else it[dailyBudgetKey] = tokens.toString() }
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
