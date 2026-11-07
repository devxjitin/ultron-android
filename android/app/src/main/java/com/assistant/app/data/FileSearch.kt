package com.assistant.app.data

import java.time.Instant
import java.time.ZoneId

/** A file the phone knows about, with only what ranking needs. */
data class FileCandidate(
    val name: String,
    /** Folder the file sits in, such as "DCIM/Camera/". May be empty. */
    val folder: String,
    val dateMillis: Long,
    val key: Long
)

/** How well a candidate fits a request. [exact] is true when every word matched without any typo tolerance. */
data class FileMatch(val file: FileCandidate, val score: Int, val exact: Boolean)

/**
 * Forgiving file search. People rarely know a file's exact name, so a request is split into words, filler words and
 * dates are understood, and each file is scored on its name and its folder, tolerating small typos.
 */
object FileSearch {
    private val STOP_WORDS = setOf(
        "the", "a", "an", "of", "my", "me", "file", "files", "show", "open", "find", "search", "play", "please", "get",
        "photo", "photos", "picture", "pictures", "image", "images", "video", "videos", "pdf", "document", "documents",
        "named", "called", "dikhao", "dikha", "kholo", "khol", "mera", "meri", "mere", "ka", "ki", "ke", "wala", "wali",
        "from", "in", "on", "at", "last", "this", "ek", "do", "mujhe", "wo", "vo", "woh", "song", "songs", "gana"
    )

    /** What the words of [query] mean: the words to look for, and an optional time window (start millis, end millis). */
    data class Request(val words: List<String>, val from: Long?, val to: Long?)

    fun parse(query: String?, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Request {
        val text = query.orEmpty().lowercase()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        fun start(d: java.time.LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
        var from: Long? = null
        var to: Long? = null
        var rest = text
        fun take(vararg phrases: String, apply: () -> Unit) {
            val hit = phrases.map { Regex("\\b" + Regex.escape(it) + "\\b") }.firstOrNull { it.containsMatchIn(rest) } ?: return
            rest = rest.replace(hit, " ")
            apply()
        }
        take("day before yesterday", "parso") { from = start(today.minusDays(2)); to = start(today.minusDays(1)) }
        take("yesterday", "kal") { from = start(today.minusDays(1)); to = start(today) }
        take("today", "aaj") { from = start(today); to = start(today.plusDays(1)) }
        take("last week", "pichle hafte", "past week") { from = start(today.minusDays(7)); to = start(today.plusDays(1)) }
        take("this week") { from = start(today.minusDays(6)); to = start(today.plusDays(1)) }
        take("last month", "pichle mahine", "past month") { from = start(today.minusDays(31)); to = start(today.plusDays(1)) }
        take("this month") { from = start(today.withDayOfMonth(1)); to = start(today.plusDays(1)) }
        take("last year") { from = start(today.minusDays(365)); to = start(today.plusDays(1)) }
        val words = tokens(rest).filter { it !in STOP_WORDS }
        return Request(words, from, to)
    }

    /** Lower-case words split on anything that is not a letter or digit, so "IMG_2024-05" gives img, 2024, 05. */
    fun tokens(text: String): List<String> =
        text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }

    /** Matches best first. Files that fit no word are dropped; with no words every file in the window is kept, newest first. */
    fun rank(candidates: List<FileCandidate>, request: Request, hasExtension: Boolean = true): List<FileMatch> {
        val inWindow = candidates.filter { f ->
            (request.from == null || f.dateMillis >= request.from) && (request.to == null || f.dateMillis < request.to)
        }
        if (request.words.isEmpty()) return inWindow.sortedByDescending { it.dateMillis }.map { FileMatch(it, 0, true) }
        return inWindow.mapNotNull { score(it, request.words, hasExtension) }
            .sortedWith(compareByDescending<FileMatch> { it.score }.thenByDescending { it.file.dateMillis })
    }

    /** The files that are closest to [request] even when no word fits, to offer as "did you mean". */
    fun closest(candidates: List<FileCandidate>, request: Request, limit: Int): List<FileCandidate> =
        candidates.map { f ->
            val nameWords = tokens(f.name.substringBeforeLast('.'))
            f to request.words.sumOf { w -> nameWords.maxOfOrNull { similarity(w, it) } ?: 0 }
        }.filter { it.second > 0 }.sortedByDescending { it.second }.take(limit).map { it.first }

    private fun score(file: FileCandidate, words: List<String>, hasExtension: Boolean): FileMatch? {
        val nameWords = tokens(if (hasExtension) file.name.substringBeforeLast('.') else file.name)
        val folderWords = tokens(file.folder)
        var total = 0
        var exact = true
        for (word in words) {
            val inName = wordScore(word, nameWords, 10)
            val inFolder = wordScore(word, folderWords, 4)
            val best = if (inFolder.first > inName.first) inFolder else inName
            if (best.first == 0) return null
            total += best.first
            if (!best.second) exact = false
        }
        return FileMatch(file, total, exact)
    }

    /** Score and whether it was exact: whole word, then prefix or inside a word, then one typo. */
    private fun wordScore(word: String, pool: List<String>, weight: Int): Pair<Int, Boolean> {
        var best = 0
        var exact = true
        for (candidate in pool) {
            val (s, e) = when {
                candidate == word -> weight * 3 to true
                candidate.startsWith(word) -> weight * 2 to true
                word.length >= 3 && candidate.contains(word) -> weight to true
                word.length >= 4 && candidate.length >= 4 && distance(word, candidate) <= (if (word.length >= 7) 2 else 1) -> weight to false
                else -> 0 to true
            }
            if (s > best) { best = s; exact = e }
        }
        return best to exact
    }

    private fun similarity(a: String, b: String): Int = when {
        a == b -> 3
        b.startsWith(a) || a.startsWith(b) && b.length >= 3 -> 2
        a.length >= 3 && b.contains(a) -> 2
        a.length >= 4 && b.length >= 4 && distance(a, b) <= 2 -> 1
        else -> 0
    }

    /** Edit distance, stopping early once it is clearly beyond 2. */
    fun distance(a: String, b: String): Int {
        if (kotlin.math.abs(a.length - b.length) > 2) return 3
        var previous = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val current = IntArray(b.length + 1)
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
            }
            previous = current
        }
        return previous[b.length]
    }
}
