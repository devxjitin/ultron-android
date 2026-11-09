package com.assistant.app.data

/** Decides which changelog entries the "What's new" card shows after an update. */
object WhatsNew {
    /** Never more than this many versions, so a long gap between updates does not make a wall of text. */
    const val MAX_VERSIONS = 4

    /** Splits "1.22.1" into numbers; null when it is not a plain x.y.z version. */
    internal fun parts(version: String): List<Int>? =
        version.trim().split(".").map { it.toIntOrNull() ?: return null }.takeIf { it.size == 3 }

    /** Negative when [a] is older than [b]; 0 when equal or unreadable. */
    internal fun compare(a: String, b: String): Int {
        val pa = parts(a) ?: return 0
        val pb = parts(b) ?: return 0
        for (i in 0 until 3) if (pa[i] != pb[i]) return pa[i].compareTo(pb[i])
        return 0
    }

    /**
     * The versions released after [lastSeen] up to [current], newest first. With no [lastSeen] (the card was never
     * shown on this phone) only the newest version is returned. Nothing is returned when [current] was already seen.
     */
    fun entriesToShow(entries: List<ChangelogEntry>, lastSeen: String?, current: String): List<ChangelogEntry> {
        val upToCurrent = entries.filter { compare(it.version, current) <= 0 }
        if (lastSeen == null) return upToCurrent.take(1)
        return upToCurrent.filter { compare(it.version, lastSeen) > 0 }.take(MAX_VERSIONS)
    }
}
