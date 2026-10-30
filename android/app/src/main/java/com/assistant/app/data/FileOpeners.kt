package com.assistant.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** A kind of file whose opening app the user can choose in Settings. */
enum class FileKind(val label: String, val mime: String) {
    IMAGE("Images", "image/*"),
    PDF("PDF", "application/pdf"),
    DOCX("Word documents (docx)", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    companion object {
        fun forMime(mime: String): FileKind? = when {
            mime.startsWith("image/") -> IMAGE
            mime == PDF.mime -> PDF
            mime == DOCX.mime -> DOCX
            else -> null
        }
    }
}

/**
 * Which app opens images, PDFs and docx files when the assistant shows one. Nothing chosen means the phone's own
 * default app, which is also what every other kind of file uses.
 */
object FileOpeners {
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("file_openers", Context.MODE_PRIVATE)

    fun chosen(context: Context, kind: FileKind): String? = prefs(context).getString(kind.name, null)

    fun choose(context: Context, kind: FileKind, packageName: String?) {
        prefs(context).edit().apply { if (packageName == null) remove(kind.name) else putString(kind.name, packageName) }.apply()
    }

    /** Installed apps that can open [kind], as package name to app name, sorted by name. */
    fun candidates(context: Context, kind: FileKind): List<Pair<String, String>> {
        val pm = context.packageManager
        val probe = Intent(Intent.ACTION_VIEW).setType(kind.mime)
        return pm.queryIntentActivities(probe, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
    }

    /** Points [intent] at the app chosen for its file type, if one is chosen and still installed. */
    fun apply(context: Context, intent: Intent, mime: String): Intent {
        val kind = FileKind.forMime(mime) ?: return intent
        val pkg = chosen(context, kind) ?: return intent
        val installed = runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
        if (!installed) return intent
        val targeted = Intent(intent).setPackage(pkg)
        // If the chosen app cannot open this file, fall back to the phone's default instead of failing.
        return if (context.packageManager.queryIntentActivities(targeted, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()) targeted else intent
    }
}
