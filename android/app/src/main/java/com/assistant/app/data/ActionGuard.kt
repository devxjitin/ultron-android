package com.assistant.app.data

import org.json.JSONObject

/**
 * Remembers when text written by other people (an SMS, a notification, a web page, repository text) last reached the
 * model. Such text can try to talk the model into acting, so [ActionGuard] asks the user for a tap while it is fresh.
 * Every untrusted tool result already carries the words "untrusted content" in its marker line; that is what is matched.
 */
object UntrustedContent {
    /** How long after untrusted text arrived that a call or text message still needs the user's tap. */
    const val WINDOW_MS = 5 * 60 * 1000L
    private const val MARKER = "untrusted content"

    @Volatile private var lastSeenAt = 0L

    fun markIfUntrusted(result: String, now: Long = System.currentTimeMillis()) {
        if (result.contains(MARKER, ignoreCase = true)) lastSeenAt = now
    }

    fun mark(now: Long = System.currentTimeMillis()) {
        lastSeenAt = now
    }

    fun isRecent(now: Long = System.currentTimeMillis()): Boolean = lastSeenAt != 0L && now - lastSeenAt in 0 until WINDOW_MS

    fun reset() {
        lastSeenAt = 0L
    }
}

/** What the user is asked to approve on screen: a short [title] and the exact [detail] (who, and what is said). */
data class ActionConfirmation(val title: String, val detail: String)

/**
 * Calls and text messages leave the phone with no screen of their own to confirm on. While untrusted text is fresh,
 * they wait for a tap in the app, because a spoken "yes" could be produced by the very text being checked.
 */
object ActionGuard {
    private val GUARDED = setOf(DeviceTool.SEND_SMS, DeviceTool.SEND_SMS_CONTACT, DeviceTool.CALL_CONTACT, DeviceTool.DIAL)

    fun needsConfirmation(tool: DeviceTool, now: Long = System.currentTimeMillis()): Boolean =
        tool in GUARDED && UntrustedContent.isRecent(now)

    fun describe(tool: DeviceTool, args: JSONObject): ActionConfirmation {
        fun text(name: String) = args.optString(name).trim().take(300)
        return when (tool) {
            DeviceTool.SEND_SMS -> ActionConfirmation("Send this text message?", "To ${text("number")}\n\n${text("message")}")
            DeviceTool.SEND_SMS_CONTACT -> ActionConfirmation("Send this text message?", "To ${text("name")}\n\n${text("message")}")
            DeviceTool.CALL_CONTACT -> ActionConfirmation("Place this call?", "Call ${text("name")}")
            DeviceTool.DIAL -> ActionConfirmation("Place this call?", "Call ${text("number")}")
            else -> ActionConfirmation("Allow this action?", tool.label)
        }
    }
}
