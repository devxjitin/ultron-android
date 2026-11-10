package com.assistant.app.data

import java.io.InputStream

/**
 * Reads at most [limit] bytes and returns null if the stream holds more, so a huge or endless
 * provider stream is rejected after [limit] + 1 bytes instead of being copied whole into memory.
 */
fun InputStream.readAtMost(limit: Int): ByteArray? {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (true) {
        val n = read(buffer)
        if (n < 0) break
        total += n
        if (total > limit) return null
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}
