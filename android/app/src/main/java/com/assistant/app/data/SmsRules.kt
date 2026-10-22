package com.assistant.app.data

/**
 * Checks applied before the assistant sends a text message on its own. Sending is automatic (no screen to confirm on),
 * so the number and the pace are limited here rather than trusted to the model.
 */
object SmsRules {
    /** Three ordinary text parts; longer messages are refused instead of silently split into many. */
    const val MAX_CHARS = 480
    const val MAX_PER_HOUR = 10
    private const val HOUR_MS = 3_600_000L

    private val sentAt = ArrayDeque<Long>()

    /**
     * The number reduced to digits (and a leading +), or null when it cannot be a normal phone number. Numbers shorter
     * than 7 digits are refused because 4 to 6 digit short codes are often premium-rate services that bill the sender.
     */
    fun cleanNumber(raw: String): String? {
        val trimmed = raw.trim()
        val digits = trimmed.filter { it.isDigit() }
        if (digits.length !in 7..15) return null
        return if (trimmed.startsWith("+")) "+$digits" else digits
    }

    /** True and records the send when fewer than [MAX_PER_HOUR] messages went out in the last hour. */
    @Synchronized
    fun allowSend(nowMs: Long = System.currentTimeMillis()): Boolean {
        while (sentAt.isNotEmpty() && nowMs - sentAt.first() >= HOUR_MS) sentAt.removeFirst()
        if (sentAt.size >= MAX_PER_HOUR) return false
        sentAt.addLast(nowMs)
        return true
    }

    /** Gives back a slot taken by [allowSend] when the message was refused before it left the phone. */
    @Synchronized
    fun refund() {
        if (sentAt.isNotEmpty()) sentAt.removeLast()
    }

    @Synchronized
    fun resetForTest() = sentAt.clear()
}
