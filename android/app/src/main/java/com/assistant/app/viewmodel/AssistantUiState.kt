package com.assistant.app.viewmodel

import com.assistant.app.data.AssistantSettings
import com.assistant.app.data.MemoryItem
import com.assistant.app.data.RagDocument
import com.assistant.app.data.StoredTurn

/** A picture the assistant is showing over the home screen. [uri] is a file or content address. */
data class ShownImage(val uri: String, val title: String, val id: Int)

/** The page on screen. Exactly one is shown at a time; HOME is the voice screen. */
enum class AppPage { HOME, PROFILE, KNOWLEDGE, MEMORY, HISTORY, IMAGES, SKILLS, SCHEDULE, CONNECTORS, SETTINGS, USAGE, STORAGE, DEVELOPER, HELP, ABOUT }

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

data class AssistantUiState(
    val page: AppPage = AppPage.HOME,
    val voiceState: VoiceState = VoiceState.IDLE,
    /** User pressed Mute during a session: mic audio isn't sent, the session stays open. */
    val isMicMuted: Boolean = false,
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
    /** Facts the assistant remembers about the user, newest last. */
    val memories: List<MemoryItem> = emptyList(),
    /** Notes the assistant removed itself, newest last; the Memory page offers to restore them. */
    val removedMemories: List<MemoryItem> = emptyList(),
    /** Saved past exchanges, newest first, as many as the Memory page has loaded. */
    val pastConversations: List<StoredTurn> = emptyList(),
    /** How many exchanges are saved in all, for the Memory and Storage pages. */
    val pastConversationCount: Int = 0,
    /** Rough space the saved exchanges take; see ConversationHistoryStore.approxBytes. */
    val pastConversationBytes: Long = 0L,
    /** Pictures the user added for the assistant to show, newest first. */
    val images: List<com.assistant.app.data.StoredImage> = emptyList(),
    val imageBytes: Long = 0L,
    /** A picture chosen in the picker that still needs a title before it is saved. */
    val pendingImageUri: String? = null,
    val isSavingImage: Boolean = false,
    /** The picture the assistant is showing right now, or null. */
    val shownImage: ShownImage? = null,
    val errorMessage: String? = null,
    /** Name of the tool the assistant is running right now (or just ran), shown on the home screen. */
    val activeTool: String? = null,
    val hasApiKey: Boolean = false,
    val currentApiKey: String = "",
    /** True when the stored knowledge data cannot be decoded; the admin can reset it. */
    val knowledgeStoreCorrupt: Boolean = false,
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
    val requestedPermissions: List<String> = emptyList(),
)
