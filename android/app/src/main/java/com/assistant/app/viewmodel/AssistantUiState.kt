package com.assistant.app.viewmodel

import android.net.Uri
import com.assistant.app.network.ChatAttachment

import com.assistant.app.network.GithubDeviceCode
import com.assistant.app.data.ChatSession
import com.assistant.app.data.AssistantSettings
import com.assistant.app.data.MemoryItem
import com.assistant.app.data.RagDocument

/** Which admin page [AssistantUiState.showSettings] opens. */
enum class AdminPage { SETTINGS, DEVELOPER, ABOUT, KNOWLEDGE, SEARCH, LIBRARY, PERSONALIZATION, USAGE, STORAGE }

/** Where the "is there a newer build" check stands; About shows it. */
enum class UpdateCheckState { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, FAILED }

enum class KnowledgePreparationStage { ANALYZING, PREPARING }

enum class AppNotificationType { SUCCESS, ERROR, WARNING, INFO }

enum class VoiceState {
    IDLE,
    CONNECTING,
    LISTENING,
    /** The user has stopped talking (mic's gone quiet after saying something) and the assistant
     *  hasn't started replying yet -- distinct from LISTENING so the UI doesn't look
     *  stuck/unresponsive during that gap. */
    PROCESSING,
    SPEAKING,
    /** The Live session was closed by the server (session/time limit) while the user was still
     *  talking to the assistant -- a fresh session is being opened automatically to continue. */
    RESETTING,
    ERROR
}

/** One message of the typed chat on the home screen. [isError] marks a failed reply (shown in red). */
data class ChatMessage(
    val id: Long,
    val fromUser: Boolean,
    val text: String,
    val isError: Boolean = false,
    /** Files sent with this message (kept in memory for the open chat only). */
    val attachments: List<ChatAttachment> = emptyList(),
    /** For a reply still waiting to start: what the assistant is doing, e.g. "Analysing image…". */
    val status: String? = null,
    /** Tools the assistant used while writing this reply, in order. */
    val toolCalls: List<ChatToolCall> = emptyList()
)

enum class ChatToolState { RUNNING, DONE, FAILED }

data class ChatToolCall(val index: Int, val label: String, val state: ChatToolState)

/** Something another app shared to Ultron (a link or text, and/or files), waiting for the chat to take it. */
data class IncomingShare(val id: Long, val text: String?, val uris: List<Uri>)

data class AssistantUiState(
    /** A share from another app, until the home chat has picked it up. */
    val incomingShare: IncomingShare? = null,
    /** The typed chat shown on the home screen while no voice session is running. */
    val chatMessages: List<ChatMessage> = emptyList(),
    val isChatSending: Boolean = false,
    /** Saved chat the home chat belongs to (highlighted in the menu); null until its first turn is saved. */
    val activeChatId: String? = null,
    val voiceState: VoiceState = VoiceState.IDLE,
    val micAmplitude: Float = 0f,
    /** User pressed Mute during a session: mic audio isn't sent, the session stays open. */
    val isMicMuted: Boolean = false,
    val playbackAmplitude: Float = 0f,
    /** Saved chats (newest first), browsable from the History dialog. */
    val chatSessions: List<ChatSession> = emptyList(),
    val showConnectors: Boolean = false,
    /** Ids of added connectors whose sign-in stopped working; Connectors shows them with Reconnect. */
    val connectorIssues: Set<String> = emptySet(),
    /** A newer build waiting on the AWS update server; shown in a dialog until installed or dismissed. */
    val updateInfo: com.assistant.app.network.UpdateInfo? = null,
    /** 0..1 while that build downloads; null otherwise. */
    val updateDownloadProgress: Float? = null,
    val updateCheckState: UpdateCheckState = UpdateCheckState.IDLE,
    /** The newest build found, kept even after the dialog was dismissed so About can still offer it. */
    val availableUpdate: com.assistant.app.network.UpdateInfo? = null,
    val lastUpdateCheckAt: Long = 0L,
    val updateCheckError: String? = null,
    /** Per-day usage counters, oldest first. */
    val usageDays: List<com.assistant.app.data.UsageDay> = emptyList(),
    /** Bytes of temporary files (downloaded updates, previews) the app can clear. */
    val cacheBytes: Long = 0L,
    /** The unsent chat message and files, kept while the user visits another page. */
    val chatDraftText: String = "",
    /** Position in [chatMessages] to scroll to once, after a chat was opened from a search result. */
    val chatFocusIndex: Int? = null,
    val chatDraftFiles: List<ChatAttachment> = emptyList(),
    /** Chats that could not be read and were set aside (shown in Storage so they can be saved or deleted). */
    val unreadableChats: Int = 0,
    /** Set while a Google service waits for the user to pick an account and allow access; the activity shows it. */
    val googleConsent: android.app.PendingIntent? = null,
    /** With [showConnectors]: show the Marketplace (everything available to add) instead of the added connectors. */
    val connectorsMarketplace: Boolean = false,
    val showMemory: Boolean = false,
    /** Facts the assistant remembers about the user, newest last. */
    val memories: List<MemoryItem> = emptyList(),
    /** Notes the assistant removed itself, newest last; the Memory page offers to restore them. */
    val removedMemories: List<MemoryItem> = emptyList(),
    /** Set while a GitHub sign-in waits for the user to approve it on github.com. */
    val githubDeviceCode: GithubDeviceCode? = null,
    val errorMessage: String? = null,
    /** Name of the tool the assistant is running right now (or just ran), shown on the home screen. */
    val activeTool: String? = null,
    val hasApiKey: Boolean = false,
    val currentApiKey: String = "",
    /** Azure OpenAI settings for the typed chat; when active, the chat uses it instead of Gemini. */
    val azure: com.assistant.app.data.AzureConfig = com.assistant.app.data.AzureConfig(),
    val showSettings: Boolean = false,
    /** True when the stored knowledge data cannot be decoded; the admin can reset it. */
    val knowledgeStoreCorrupt: Boolean = false,
    val adminPage: AdminPage = AdminPage.SETTINGS,
    val settings: AssistantSettings = AssistantSettings(),
    /** User-managed knowledge documents, searched by the search_knowledge_base tool. Starts
     *  empty -- nothing ships bundled; the admin adds documents from Settings. */
    val knowledgeDocuments: List<RagDocument> = emptyList(),
    /** True while a document is being embedded (network call) after an add/edit save. */
    val isSavingKnowledgeDocument: Boolean = false,
    /** Human-friendly phase shown while a PDF is being read or knowledge is being prepared. */
    val knowledgePreparationStage: KnowledgePreparationStage? = null,
    /** Which add/edit operation [isSavingKnowledgeDocument]/[knowledgeDocumentError] belong to --
     *  [ADD_DOCUMENT_TARGET_ID] for a new document, or the existing document's id for an update.
     *  Lets each open editor tell "my save" apart from an unrelated concurrent one, instead of
     *  every editor reacting to whichever operation happens to finish first. Stays set on
     *  failure (so the right editor can show its own error) and clears on success or dismissal. */
    val savingKnowledgeDocumentTarget: String? = null,
    val knowledgeDocumentError: String? = null,
    /** Chunk-embedding progress for the document currently being saved/uploaded, e.g. 3 of 10.
     *  totalChunks is 0 until the text has been chunked, so the UI can tell "not started yet"
     *  apart from "0 of N done". */
    val uploadedChunks: Int = 0,
    val totalChunks: Int = 0,
    /** Required key for document formatting, embedding, and knowledge-base search. */
    val currentEmbeddingApiKey: String = "",
    /** True while the AI is drafting a persona from the current knowledge documents. */
    val isGeneratingPrompt: Boolean = false,
    val promptGenerationError: String? = null,
    /** True while the agent prompt is being redrafted after a knowledge change; the UI blocks
     *  interaction until it finishes. */
    val isUpdatingAgentPrompt: Boolean = false,
    /** The AI-drafted persona text, shown in the prompt field for review -- not applied to
     *  [AssistantSettings.systemPrompt] until the admin explicitly hits Save. Cleared once
     *  consumed (Save pressed, or a fresh generation/dialog reopen). */
    val generatedPromptDraft: String? = null,
    /** A short-lived, typed notification shown at the top of the app. */
    val toastMessage: String? = null,
    val toastType: AppNotificationType = AppNotificationType.SUCCESS,
    val toastId: Int = 0,
    /** Increments only after Developer settings are safely persisted, requesting an app restart. */
    val restartRequestId: Int = 0,
    /** True while a Developer Save is in flight; blocks duplicate submissions. */
    val isSavingDeveloperSettings: Boolean = false,
    /** Window brightness override (1-100) set by the brightness tool; null follows the system. */
    val appBrightnessPercent: Int? = null,
    /** Bumps each time a tool needs Android permissions; the activity shows the dialog for [requestedPermissions]. */
    val permissionRequestId: Int = 0,
    val requestedPermissions: List<String> = emptyList()
)
