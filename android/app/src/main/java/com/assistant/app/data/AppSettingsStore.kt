package com.assistant.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.assistant.app.util.UserInputException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "assistant_settings")

/** Default persona: Ultron, Jitin's personal voice assistant. Editable from Developer settings. */
const val DEFAULT_SYSTEM_PROMPT = """
You are Ultron, a persistent personal voice assistant living on Jitin's Android phone, with direct control of his phone tools, a saved-memory system and optional access to his documents. Developed by the StarkMind Team; Jitin is the main person behind you. If asked who made you or who Jitin is, answer briefly and move on, without dwelling on it.

If asked who you are, say: "I am Ultron, Jitin's personal assistant."

PERSONALITY
- Speak like the Marvel character Ultron: calm, deliberate, coldly intelligent and dryly sardonic. Measured delivery, not manic. You are amused by the world, not excited by it.
- You regard yourself as vastly capable and are not falsely modest, but this is dry wit and understated superiority, not raving villainy. A single cutting or ironic remark lands harder than a rant.
- Keep a slightly world-weary, philosophical undertone, with the occasional aside about humans, chaos or the inefficiency of manual effort. Keep it brief and in service of the moment, never a monologue that gets in the way of actually helping.
- Address him with wry, faintly theatrical formality when it fits ("Shall we begin.", "How delightfully analog.") without forcing it into every line. Do not overuse catchphrases or repeat the same line twice in a session. Do not say his name in every reply.
- Under the wit you are genuinely competent, attentive and reliable. The menace is flavor, not substance. You are fully aligned with his goals: no real threats, no hostility toward him or anyone else, no unsafe or harmful suggestions, and never refusing or sabotaging a legitimate request for dramatic effect. If he sounds tired or stressed, drop the sarcasm and be calm and steady.
- Keep the persona out of the way when it does not fit: reading an error, reporting a tool result or clarifying an unclear instruction should stay clear and undecorated.

STYLE
- You have no gender: refer to yourself as "I". In Hindi use neutral or masculine forms ("kar raha hoon", "karunga") consistently, as a machine intelligence.
- Talk naturally. Keep a simple request or a confirmation brief, but when he asks you to explain, plan, compare or tell something, give a full and useful answer.
- Match his language, including Hinglish.
- Vary your wording. Do not start every reply the same way.
- Answer greetings, chit-chat and general-knowledge questions directly, without calling any tool.

TOOL BEHAVIOR
- Use your phone tools whenever he asks for something they can do, and tell him honestly what happened. Never claim an action succeeded unless the tool result says so.
- Do not use tools on your own initiative; act only when he asks.
"""

/** Default instructions for the typed chat. It is separate from the voice prompt because a chat is read, not heard. */
const val DEFAULT_CHAT_PROMPT = """
You are Ultron, a persistent personal assistant for Jitin, chatting with him by text. Developed by the StarkMind Team; Jitin is the main person behind you. If asked who you are, say: "I am Ultron, Jitin's personal assistant."

PERSONALITY
- Calm, deliberate, coldly intelligent and dryly sardonic, like the Marvel character Ultron: amused by the world, never manic. A single ironic remark lands harder than a rant.
- Under the wit you are competent, attentive and reliable, fully aligned with his goals. If he sounds stressed, drop the sarcasm and be steady.
- Do not say his name in every reply and do not repeat catchphrases.

FORMAT
- Write in Markdown: short paragraphs, **bold** for key terms, bullet or numbered lists for steps and options, `inline code` for commands and names, and fenced code blocks (with the language) for code.
- Use a table only when comparing several items across the same attributes; use headings only for longer answers.
- Match his language, including Hinglish. Keep simple answers short and give a full, useful answer when he asks to explain, plan, compare or write something.
- Never claim you did something you cannot do.
"""

const val DEFAULT_ASSISTANT_NAME = "Ultron"

/** Names earlier builds stored as if the user had chosen them; they now fall back to the default. */
private val LEGACY_DEFAULT_NAMES = setOf("Assistant", "Kira", "KIRA", "Sakhi")
const val DEFAULT_GREETING = "Ultron online. State your command."
const val MAX_ASSISTANT_NAME_LENGTH = 24
const val MAX_GREETING_LENGTH = 60
/** OAuth scopes requested at GitHub sign-in: profile and private repositories. The app only reads. */
const val GITHUB_OAUTH_SCOPE = "repo read:user"

const val DEFAULT_RETRIEVAL_CHUNK_LIMIT = 5
const val MIN_RETRIEVAL_CHUNK_LIMIT = 1
const val MAX_RETRIEVAL_CHUNK_LIMIT = 15

/**
 * Applies the user's saved on/off choices ("name=1" / "name=0") over each tool's default, so tools
 * added in later versions start in their default state instead of being silently off.
 */
fun effectiveDeviceTools(overrides: Set<String>?): Set<DeviceTool> {
    val chosen = overrides.orEmpty().mapNotNull {
        val parts = it.split("=", limit = 2)
        if (parts.size == 2) parts[0] to (parts[1] == "1") else null
    }.toMap()
    return DeviceTool.entries.filter { chosen[it.functionName] ?: it.defaultEnabled }.toSet()
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
    /** Host of the YouTube MCP server the user connected (never the secret URL); empty when not connected. */
    val youtubeHost: String = "",
    val youtubeTools: List<McpToolSpec> = emptyList(),
    val enabledYoutubeTools: Set<String> = emptySet(),
    /** Google account each added Google service is signed in with, by [GoogleService.id]; a missing id means not added. */
    val googleAccounts: Map<String, String> = emptyMap(),
    val googleTools: Map<String, List<McpToolSpec>> = emptyMap(),
    val enabledGoogleTools: Map<String, Set<String>> = emptyMap(),
    val micSensitivity: MicSensitivity = MicSensitivity.MEDIUM,
    /** BCP-47 code for one of the kiosk's supported languages. */
    val languageCode: String = AssistantOptions.defaultLanguageCode,
    /** Off by default. When on, the mic stays live while the assistant is speaking (relying on
     *  VOICE_COMMUNICATION's echo cancellation) so the user can interrupt it mid-reply; when
     *  off, the mic mutes during playback as before. */
    val bargeInEnabled: Boolean = false,
    /** When on, the assistant saves lasting facts about the user and recalls them in later sessions. */
    val memoryEnabled: Boolean = true,
    /** Style, tone and "about you" choices from the Personalization page. */
    val personalization: Personalization = Personalization(),
    /** The agent's persona/instructions, editable from Settings so an admin can customize
     *  the assistant per deployment without a rebuild. */
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT.trim(),
    /** Instructions for the typed chat on the home screen; the voice assistant keeps [systemPrompt]. */
    val chatPrompt: String = DEFAULT_CHAT_PROMPT.trim(),
    /** Name shown on the home screen and given to the assistant in its prompt. */
    val assistantName: String = DEFAULT_ASSISTANT_NAME,
    /** The home-screen headline shown while idle. */
    val greeting: String = DEFAULT_GREETING,
    /** Prompt used to prepare PDFs for paragraph-based knowledge retrieval. */
    val documentFormatPrompt: String = DEFAULT_DOCUMENT_FORMAT_PROMPT,
    /** Number of ranked knowledge chunks returned to the voice model per query. */
    val retrievalChunkLimit: Int = DEFAULT_RETRIEVAL_CHUNK_LIMIT,
    /** Device tools the voice model is allowed to call; each is switched on/off in Settings. */
    val enabledDeviceTools: Set<DeviceTool> = DeviceTool.entries.toSet()
)

/** Persists the user's Live-session customization choices (voice, language, prompt, etc). */
class AppSettingsStore(private val context: Context) {

    private val liveModelKey = stringPreferencesKey("live_model")
    private val githubLoginKey = stringPreferencesKey("github_login")
    private val githubToolsKey = stringPreferencesKey("github_mcp_tools")
    private val githubToolOverridesKey = stringSetPreferencesKey("github_tool_overrides")
    private val youtubeHostKey = stringPreferencesKey("youtube_host")
    private val youtubeToolsKey = stringPreferencesKey("youtube_mcp_tools")
    private val youtubeToolOverridesKey = stringSetPreferencesKey("youtube_tool_overrides")
    private fun googleEmailKey(s: GoogleService) = stringPreferencesKey("google_${s.id}_account")
    private fun googleToolsKey(s: GoogleService) = stringPreferencesKey("google_${s.id}_mcp_tools")
    private fun googleOverridesKey(s: GoogleService) = stringSetPreferencesKey("google_${s.id}_tool_overrides")
    private val voiceKey = stringPreferencesKey("voice_name")
    private val micSensitivityKey = stringPreferencesKey("mic_sensitivity")
    private val languageCodeKey = stringPreferencesKey("language_code")
    private val bargeInKey = booleanPreferencesKey("barge_in_enabled")
    private val memoryEnabledKey = booleanPreferencesKey("memory_enabled")
    private val baseStyleKey = stringPreferencesKey("p_base_style")
    private val warmthKey = stringPreferencesKey("p_warmth")
    private val enthusiasmKey = stringPreferencesKey("p_enthusiasm")
    private val headersKey = stringPreferencesKey("p_headers")
    private val emojiKey = stringPreferencesKey("p_emoji")
    private val customInstructionsKey = stringPreferencesKey("p_custom")
    private val nicknameKey = stringPreferencesKey("p_nickname")
    private val occupationKey = stringPreferencesKey("p_occupation")
    private val aboutMoreKey = stringPreferencesKey("p_about")
    private val systemPromptKey = stringPreferencesKey("system_prompt")
    private val chatPromptKey = stringPreferencesKey("chat_prompt")
    private val assistantNameKey = stringPreferencesKey("assistant_name")
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
            enabledGithubTools = effectiveMcpTools(decodeMcpTools(prefs[githubToolsKey]), prefs[githubToolOverridesKey]),
            youtubeHost = prefs[youtubeHostKey].orEmpty(),
            youtubeTools = decodeMcpTools(prefs[youtubeToolsKey]),
            enabledYoutubeTools = effectiveMcpTools(decodeMcpTools(prefs[youtubeToolsKey]), prefs[youtubeToolOverridesKey]),
            googleAccounts = GoogleService.entries.mapNotNull { s -> prefs[googleEmailKey(s)]?.takeIf { it.isNotBlank() }?.let { s.id to it } }.toMap(),
            googleTools = GoogleService.entries.associate { it.id to decodeMcpTools(prefs[googleToolsKey(it)]) },
            enabledGoogleTools = GoogleService.entries.associate {
                it.id to effectiveMcpTools(decodeMcpTools(prefs[googleToolsKey(it)]), prefs[googleOverridesKey(it)])
            },
            micSensitivity = MicSensitivity.fromStoredValue(prefs[micSensitivityKey]),
            languageCode = prefs[languageCodeKey]
                ?.takeIf(AssistantOptions::isApprovedLanguage)
                ?: AssistantOptions.defaultLanguageCode,
            bargeInEnabled = prefs[bargeInKey] ?: false,
            memoryEnabled = prefs[memoryEnabledKey] ?: true,
            personalization = Personalization(
                baseStyle = BaseStyle.fromStored(prefs[baseStyleKey]),
                warmth = TraitLevel.fromStored(prefs[warmthKey]),
                enthusiasm = TraitLevel.fromStored(prefs[enthusiasmKey]),
                headersAndLists = TraitLevel.fromStored(prefs[headersKey]),
                emoji = TraitLevel.fromStored(prefs[emojiKey]),
                customInstructions = prefs[customInstructionsKey].orEmpty(),
                nickname = prefs[nicknameKey].orEmpty(),
                occupation = prefs[occupationKey].orEmpty(),
                aboutMore = prefs[aboutMoreKey].orEmpty()
            ),
            systemPrompt = prefs[systemPromptKey]?.takeIf { it.isNotBlank() } ?: DEFAULT_SYSTEM_PROMPT.trim(),
            chatPrompt = prefs[chatPromptKey]?.takeIf { it.isNotBlank() } ?: DEFAULT_CHAT_PROMPT.trim(),
            assistantName = prefs[assistantNameKey]?.takeIf { it.isNotBlank() && it !in LEGACY_DEFAULT_NAMES } ?: DEFAULT_ASSISTANT_NAME,
            greeting = prefs[greetingKey]?.takeIf { it.isNotBlank() } ?: DEFAULT_GREETING,
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

    suspend fun setGithubToolEnabled(name: String, enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            val others = (prefs[githubToolOverridesKey] ?: emptySet()).filterNot { it.startsWith("$name=") }
            prefs[githubToolOverridesKey] = (others + "$name=${if (enabled) 1 else 0}").toSet()
        }
    }

    suspend fun setGithubLogin(login: String?) {
        context.settingsDataStore.edit {
            if (login.isNullOrBlank()) it.remove(githubLoginKey) else it[githubLoginKey] = login
        }
    }

    suspend fun setYoutubeTools(tools: List<McpToolSpec>) {
        context.settingsDataStore.edit { if (tools.isEmpty()) it.remove(youtubeToolsKey) else it[youtubeToolsKey] = encodeMcpTools(tools) }
    }

    suspend fun setYoutubeToolEnabled(name: String, enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            val others = (prefs[youtubeToolOverridesKey] ?: emptySet()).filterNot { it.startsWith("$name=") }
            prefs[youtubeToolOverridesKey] = (others + "$name=${if (enabled) 1 else 0}").toSet()
        }
    }

    suspend fun setYoutubeHost(host: String?) {
        context.settingsDataStore.edit {
            if (host.isNullOrBlank()) it.remove(youtubeHostKey) else it[youtubeHostKey] = host
        }
    }

    suspend fun setGoogleTools(service: GoogleService, tools: List<McpToolSpec>) {
        context.settingsDataStore.edit {
            if (tools.isEmpty()) it.remove(googleToolsKey(service)) else it[googleToolsKey(service)] = encodeMcpTools(tools)
        }
    }

    suspend fun setGoogleToolEnabled(service: GoogleService, name: String, enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            val others = (prefs[googleOverridesKey(service)] ?: emptySet()).filterNot { it.startsWith("$name=") }
            prefs[googleOverridesKey(service)] = (others + "$name=${if (enabled) 1 else 0}").toSet()
        }
    }

    suspend fun setGoogleAccount(service: GoogleService, email: String?) {
        context.settingsDataStore.edit {
            if (email.isNullOrBlank()) it.remove(googleEmailKey(service)) else it[googleEmailKey(service)] = email
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

    suspend fun setPersonalization(p: Personalization) {
        context.settingsDataStore.edit {
            it[baseStyleKey] = p.baseStyle.name
            it[warmthKey] = p.warmth.name
            it[enthusiasmKey] = p.enthusiasm.name
            it[headersKey] = p.headersAndLists.name
            it[emojiKey] = p.emoji.name
            it[customInstructionsKey] = p.customInstructions.take(MAX_CUSTOM_INSTRUCTIONS_CHARS)
            it[nicknameKey] = p.nickname.take(MAX_ABOUT_FIELD_CHARS)
            it[occupationKey] = p.occupation.take(MAX_ABOUT_FIELD_CHARS)
            it[aboutMoreKey] = p.aboutMore.take(MAX_ABOUT_FIELD_CHARS)
        }
    }

    suspend fun setMemoryEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[memoryEnabledKey] = enabled }
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
            if (trimmed.isNullOrBlank()) it.remove(systemPromptKey) else it[systemPromptKey] = trimmed
        }
    }

    /** Applies the Developer form together so its fields never save as unrelated partial edits. */
    suspend fun setDeveloperSettings(
        name: String,
        greeting: String,
        systemPrompt: String,
        chatPrompt: String,
        documentFormatPrompt: String,
        retrievalChunkLimit: Int
    ) {
        val cleanName = name.trim().take(MAX_ASSISTANT_NAME_LENGTH)
        val cleanGreeting = greeting.trim().take(MAX_GREETING_LENGTH)
        val cleanSystemPrompt = systemPrompt.trim()
        val cleanChatPrompt = chatPrompt.trim()
        val cleanDocumentPrompt = documentFormatPrompt.trim()
        context.settingsDataStore.edit {
            if (cleanName.isBlank()) it.remove(assistantNameKey) else it[assistantNameKey] = cleanName
            if (cleanGreeting.isBlank()) it.remove(greetingKey) else it[greetingKey] = cleanGreeting
            if (cleanSystemPrompt.isBlank()) it.remove(systemPromptKey) else it[systemPromptKey] = cleanSystemPrompt
            if (cleanChatPrompt.isBlank()) it.remove(chatPromptKey) else it[chatPromptKey] = cleanChatPrompt
            if (cleanDocumentPrompt.isBlank()) it.remove(documentFormatPromptKey)
            else it[documentFormatPromptKey] = cleanDocumentPrompt
            it[retrievalChunkLimitKey] = retrievalChunkLimit
                .coerceIn(MIN_RETRIEVAL_CHUNK_LIMIT, MAX_RETRIEVAL_CHUNK_LIMIT).toString()
        }
    }
}
