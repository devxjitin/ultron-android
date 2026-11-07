package com.assistant.app.data

import android.app.PendingIntent
import android.app.RemoteInput
import android.content.ComponentName
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateUtils
import com.assistant.app.AssistantNotificationListener

/**
 * Phone actions that only need a system service or content provider: notification replies,
 * the media session and a few intents. Each returns the "status=..." text the voice model reads. Permission and access
 * checks stay with the caller ([DeviceToolRunner]).
 */
class PhoneExtras(private val app: Context, private val start: (Intent) -> Unit) {

    private fun appLabel(packageName: String): String {
        val pm = app.packageManager
        return runCatching { pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString() }.getOrDefault(packageName)
    }

    fun replyToNotification(listener: AssistantNotificationListener, appName: String, contact: String, message: String): String {
        val replyable = listener.activeNotifications
            .filter { it.packageName != app.packageName }
            .mapNotNull { sbn ->
                val action = sbn.notification.actions?.firstOrNull { !it.remoteInputs.isNullOrEmpty() } ?: return@mapNotNull null
                val extras = sbn.notification.extras
                val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
                val conversation = extras.getCharSequence(android.app.Notification.EXTRA_CONVERSATION_TITLE)?.toString().orEmpty()
                Triple(ReplyCandidate(sbn.key, appLabel(sbn.packageName), conversation.ifBlank { title }, listOf(title, conversation).filter { it.isNotBlank() }, sbn.postTime), sbn, action)
            }
        val pick = pickReply(replyable.map { it.first }, appName, contact)
        val chosen = when (pick) {
            ReplyPick.TooShort -> return "status=invalid\nGive the full app name and the name of the person or group to reply to. No message was sent."
            ReplyPick.None -> return "status=not_found\nNo notification from \"$appName\" with a reply box matches \"$contact\" right now. No message was sent."
            is ReplyPick.Many -> return "status=ambiguous\nSeveral chats match \"$contact\": " + pick.chats.joinToString("; ") +
                ". Ask the user which one, then call reply_to_notification again with that exact name. No message was sent."
            is ReplyPick.One -> replyable.first { it.first.id == pick.candidate.id }
        }
        val (candidate, _, action) = chosen
        val input = action.remoteInputs.first()
        val results = Bundle().apply { putCharSequence(input.resultKey, message) }
        val intent = Intent()
        RemoteInput.addResultsToIntent(action.remoteInputs, intent, results)
        return try {
            action.actionIntent.send(app, 0, intent)
            "status=ok\nReplied in ${candidate.app} to ${candidate.chat}: $message"
        } catch (e: PendingIntent.CanceledException) {
            "status=failed\nThat notification can no longer be replied to. No message was sent."
        }
    }

    fun dismissNotifications(listener: AssistantNotificationListener, appName: String): String {
        if (appName.equals("all", ignoreCase = true)) {
            val count = listener.activeNotifications.count { it.isClearable }
            listener.cancelAllNotifications()
            return "status=ok\nCleared $count notifications."
        }
        val targets = listener.activeNotifications.filter { it.isClearable && appLabel(it.packageName).contains(appName, ignoreCase = true) }
        if (targets.isEmpty()) return "status=not_found\nNo clearable notifications from \"$appName\"."
        targets.forEach { listener.cancelNotification(it.key) }
        return "status=ok\nCleared ${targets.size} notifications from ${appLabel(targets.first().packageName)}."
    }

    fun nowPlaying(): String {
        val manager = app.getSystemService(MediaSessionManager::class.java)
        val sessions: List<MediaController> = runCatching {
            manager.getActiveSessions(ComponentName(app, AssistantNotificationListener::class.java))
        }.getOrElse { return "status=failed\nCould not read the media sessions." }
        if (sessions.isEmpty()) return "status=empty\nNothing is playing in any media app."
        return "status=ok\n" + sessions.joinToString("\n") { controller ->
            val meta = controller.metadata
            val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty().ifBlank { "unknown title" }
            val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
            val state = when (controller.playbackState?.state) {
                PlaybackState.STATE_PLAYING -> "playing"
                PlaybackState.STATE_PAUSED -> "paused"
                PlaybackState.STATE_BUFFERING -> "buffering"
                else -> "stopped"
            }
            "${appLabel(controller.packageName)}: $title${if (artist.isNotBlank()) " by $artist" else ""} ($state)"
        }
    }

    fun openPanel(panel: String?): String {
        val action = if (Build.VERSION.SDK_INT >= 29) when (panel) {
            "wifi" -> Settings.Panel.ACTION_WIFI
            "nfc" -> Settings.Panel.ACTION_NFC
            "volume" -> Settings.Panel.ACTION_VOLUME
            else -> Settings.Panel.ACTION_INTERNET_CONNECTIVITY
        } else when (panel) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "nfc" -> Settings.ACTION_NFC_SETTINGS
            "volume" -> Settings.ACTION_SOUND_SETTINGS
            else -> Settings.ACTION_WIRELESS_SETTINGS
        }
        start(Intent(action))
        return "status=ok\nThe panel is open. Now tap the switch yourself: read_screen, then click_on_text on it (or tap_screen_position), then read_screen again to confirm. Do not ask the user to do it unless accessibility is off."
    }

    fun listApps(filter: String?): String {
        val pm = app.packageManager
        val names = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.loadLabel(pm).toString() }
            .filter { filter.isNullOrBlank() || it.contains(filter, ignoreCase = true) }
            .distinct().sortedBy { it.lowercase() }
        return if (names.isEmpty()) "status=empty\nNo matching apps."
        else "status=ok\n${names.size} apps: " + names.take(120).joinToString(", ")
    }

    fun openUrl(url: String): String {
        val uri = Uri.parse(url.trim())
        if (uri.scheme != "http" && uri.scheme != "https") return "status=invalid\nOnly http and https links can be opened."
        start(Intent(Intent.ACTION_VIEW, uri))
        return "status=ok\nOpened the link."
    }

    fun shareText(text: String): String {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        start(Intent.createChooser(send, "Share with"))
        return "status=ok\nThe share sheet is open. Nothing is sent until the user picks an app."
    }
}

/** A notification that has a quick-reply box: [chat] is what the reply is shown as going to, [names] is everything a person may call it. */
internal data class ReplyCandidate(val id: String, val app: String, val chat: String, val names: List<String>, val postTime: Long)

internal sealed interface ReplyPick {
    data object TooShort : ReplyPick
    data object None : ReplyPick
    data class Many(val chats: List<String>) : ReplyPick
    data class One(val candidate: ReplyCandidate) : ReplyPick
}

/**
 * Chooses the one chat to answer. The app name must be at least 3 letters (an exact label wins over a partial one) and the
 * contact at least 2, and a reply goes out only when exactly one chat fits; several chats are returned for the user to pick.
 */
internal fun pickReply(candidates: List<ReplyCandidate>, appName: String, contact: String): ReplyPick {
    val app = appName.trim()
    val who = contact.trim()
    if (app.length < 3 || who.length < 2) return ReplyPick.TooShort
    val ofApp = candidates.filter { it.app.equals(app, ignoreCase = true) }
        .ifEmpty { candidates.filter { it.app.contains(app, ignoreCase = true) } }
    val matching = ofApp.filter { c -> c.names.any { it.contains(who, ignoreCase = true) } }
    if (matching.isEmpty()) return ReplyPick.None
    val narrowed = matching.filter { c -> c.names.any { it.equals(who, ignoreCase = true) } }.ifEmpty { matching }
    val chats = narrowed.groupBy { it.chat.lowercase() }
    if (chats.size > 1) return ReplyPick.Many(chats.values.map { it.first().chat })
    return ReplyPick.One(narrowed.maxBy { it.postTime })
}
