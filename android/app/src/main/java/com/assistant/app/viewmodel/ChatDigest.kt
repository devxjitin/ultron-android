package com.assistant.app.viewmodel

private const val DIGEST_LINE_CHARS = 160
private const val DIGEST_MAX_CHARS = 2_000

/**
 * A short reminder of the oldest messages that no longer fit the model's context, so the model still knows
 * roughly what was said before. [keptCount] is how many of the newest usable messages are sent in full.
 * Empty when nothing was left out.
 */
internal fun earlierDigest(all: List<ChatMessage>, keptCount: Int): String {
    val usable = all.filter { !it.isError && (it.text.isNotBlank() || it.attachments.isNotEmpty()) }
    val older = usable.take((usable.size - keptCount.coerceAtLeast(0)).coerceAtLeast(0))
    if (older.isEmpty()) return ""
    val lines = older.map { message ->
        val text = message.text.replace(Regex("\\s+"), " ").trim().ifEmpty { "[attachment]" }
        (if (message.fromUser) "User: " else "Assistant: ") + text.take(DIGEST_LINE_CHARS)
    }
    // When they do not all fit, the newest of the older ones are kept.
    val kept = ArrayDeque<String>()
    var size = 0
    for (line in lines.asReversed()) {
        if (size + line.length + 1 > DIGEST_MAX_CHARS) break
        kept.addFirst(line)
        size += line.length + 1
    }
    if (kept.isEmpty()) return ""
    return "\n\nEarlier in this conversation (shortened; the full messages are no longer included):\n" + kept.joinToString("\n")
}
