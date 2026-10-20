package com.assistant.app.data

/** The groups the Library sorts uploaded files into. */
enum class LibraryCategory(val label: String) {
    IMAGES("Images"), VIDEOS("Videos"), PDFS("PDFs"), DOCUMENTS("Documents");

    companion object {
        fun of(mimeType: String): LibraryCategory = when {
            mimeType.startsWith("image/") -> IMAGES
            mimeType.startsWith("video/") -> VIDEOS
            mimeType == "application/pdf" -> PDFS
            else -> DOCUMENTS
        }
    }
}

/** One file the user sent in a chat, with the chat it belongs to. */
data class LibraryItem(
    val file: AttachmentRef,
    val sessionId: String,
    val chatTitle: String,
    val timestampMillis: Long
) {
    val category: LibraryCategory get() = LibraryCategory.of(file.mimeType)
}

/** Every file in the saved chats, newest first. */
fun libraryItems(sessions: List<ChatSession>): List<LibraryItem> =
    sessions.flatMap { session ->
        session.turns.flatMap { turn ->
            turn.attachments.map { LibraryItem(it, session.id, session.title, turn.timestampMillis) }
        }
    }.sortedByDescending { it.timestampMillis }
