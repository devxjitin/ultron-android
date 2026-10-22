package com.assistant.app.data

import android.content.Context
import com.assistant.app.network.ChatAttachment
import java.io.File
import java.util.UUID

/** Keeps the files sent in typed chats in the app's private storage, so an old chat still shows them when reopened. */
class ChatAttachmentStore(context: Context) {

    private val root = File(context.filesDir, "chat_attachments")

    /** Saves [file] under the chat [sessionId]; null if the disk write fails. */
    fun save(sessionId: String, file: ChatAttachment): AttachmentRef? = runCatching {
        val dir = File(root, sessionId).also { it.mkdirs() }
        val id = UUID.randomUUID().toString()
        File(dir, id).writeBytes(file.data)
        AttachmentRef(file.name, file.mimeType, "$sessionId/$id", file.size)
    }.getOrNull()

    /** A saved file as an attachment whose bytes are read when first used; null if it is gone. */
    fun load(ref: AttachmentRef): ChatAttachment? {
        val file = File(root, ref.path)
        val inside = runCatching { file.canonicalPath.startsWith(root.canonicalPath + File.separator) }.getOrDefault(false)
        if (!inside || !file.isFile) return null
        return ChatAttachment(ref.name, ref.mimeType, ref.size) { file.readBytes() }
    }

    /** Deletes one saved file; only paths inside the attachment folder are touched. */
    fun delete(path: String) {
        runCatching {
            val file = File(root, path)
            if (file.canonicalPath.startsWith(root.canonicalPath + File.separator)) file.delete()
        }
    }

    /** Deletes every saved file of every chat. */
    fun clearAll() {
        runCatching { root.deleteRecursively() }
    }

    fun deleteSession(sessionId: String) {
        runCatching { File(root, sessionId).deleteRecursively() }
    }

    /**
     * Deletes saved files that no turn refers to any more (a trimmed or edited turn drops its reference but not the file).
     * Files from the last hour are left alone, since a turn being saved may not be recorded yet.
     */
    fun pruneUnreferenced(referencedPaths: Set<String>) {
        val cutoff = System.currentTimeMillis() - 60 * 60_000L
        root.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
            dir.listFiles()?.forEach { file ->
                if ("${dir.name}/${file.name}" !in referencedPaths && file.lastModified() < cutoff) runCatching { file.delete() }
            }
        }
    }

    /** Removes the files of chats that no longer exist (deleted, or dropped when the history is full). */
    fun prune(keepSessionIds: Set<String>) {
        root.listFiles()?.filter { it.isDirectory && it.name !in keepSessionIds }?.forEach { runCatching { it.deleteRecursively() } }
    }
}
