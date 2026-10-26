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

private fun allLibraryItems(sessions: List<ChatSession>): List<LibraryItem> =
    sessions.flatMap { session ->
        session.turns.flatMap { turn ->
            turn.attachments.map { LibraryItem(it, session.id, session.title, turn.timestampMillis) }
        }
    }.sortedByDescending { it.timestampMillis }

/** Two saved copies of the same file (sent again, or re-sent by an edit) share a name, type and size. */
private fun AttachmentRef.sameFile(other: AttachmentRef) = name == other.name && mimeType == other.mimeType && size == other.size

/** Every file in the saved chats, newest first; a file that was sent more than once is listed once, as its newest copy. */
fun libraryItems(sessions: List<ChatSession>): List<LibraryItem> =
    allLibraryItems(sessions).distinctBy { Triple(it.file.name, it.file.mimeType, it.file.size) }

/** All saved copies of [item]'s file, as (sessionId, path); deleting a listed file has to remove every one of them. */
fun libraryCopies(sessions: List<ChatSession>, item: LibraryItem): List<Pair<String, String>> =
    allLibraryItems(sessions).filter { it.file.sameFile(item.file) }.map { it.sessionId to it.file.path }.distinct()
