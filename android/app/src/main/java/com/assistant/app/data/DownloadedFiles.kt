package com.assistant.app.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/** One file Ultron saved to the phone's Downloads/Ultron folder. */
data class DownloadedFile(
    val name: String,
    val uri: Uri,
    val mime: String,
    val bytes: Long,
    val addedMillis: Long
) {
    val kind: DownloadKind
        get() = when {
            mime.startsWith("video/") -> DownloadKind.VIDEO
            mime.startsWith("audio/") -> DownloadKind.AUDIO
            else -> DownloadKind.OTHER
        }
}

enum class DownloadKind(val label: String) { VIDEO("Video"), AUDIO("Audio"), OTHER("Other") }

/**
 * Everything Ultron has downloaded. These are ordinary files in Downloads/Ultron, so the phone's own Files app and gallery
 * show them too; this lists the same folder inside the app.
 */
object DownloadedFiles {

    /** Newest first. Android 10+ keeps them in the MediaStore; older phones keep plain files in the app's Downloads folder. */
    fun list(context: Context): List<DownloadedFile> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) listFromMediaStore(context) else listFromFolder(context)
    }.getOrDefault(emptyList()).sortedByDescending { it.addedMillis }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun listFromMediaStore(context: Context): List<DownloadedFile> {
        val found = mutableListOf<DownloadedFile>()
        val columns = arrayOf(
            MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_ADDED
        )
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI, columns,
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?", arrayOf("${Environment.DIRECTORY_DOWNLOADS}/Ultron%"),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                found += DownloadedFile(
                    name = cursor.getString(1).orEmpty(),
                    uri = android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id),
                    mime = cursor.getString(2).orEmpty(),
                    bytes = cursor.getLong(3),
                    addedMillis = cursor.getLong(4) * 1000
                )
            }
        }
        return found
    }

    private fun listFromFolder(context: Context): List<DownloadedFile> {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Ultron")
        return dir.listFiles()?.filter { it.isFile }?.map { file ->
            DownloadedFile(
                name = file.name,
                uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file),
                mime = android.webkit.MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(file.extension.lowercase()).orEmpty(),
                bytes = file.length(),
                addedMillis = file.lastModified()
            )
        }.orEmpty()
    }

    /** Deletes the file from the phone. Returns false when Android refused, for example a file left by an older install. */
    fun delete(context: Context, file: DownloadedFile): Boolean = runCatching {
        if (file.uri.scheme == "content" && file.uri.authority == MediaStore.AUTHORITY) {
            context.contentResolver.delete(file.uri, null, null) > 0
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Ultron")
            File(dir, file.name).delete()
        }
    }.getOrDefault(false)
}
