package com.assistant.app.data

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Names of cache entries that are in use right now. Whoever needs a temporary file or folder to survive "Clear cache"
 * takes a lease on its name and closes it when done; clearing skips every leased name.
 */
object CacheLeases {
    private val held = HashMap<String, Int>()

    fun acquire(name: String): Lease {
        synchronized(held) { held[name] = (held[name] ?: 0) + 1 }
        return Lease(name)
    }

    fun isLeased(name: String): Boolean = synchronized(held) { (held[name] ?: 0) > 0 }

    private fun release(name: String) {
        synchronized(held) {
            val left = (held[name] ?: 0) - 1
            if (left > 0) held[name] = left else held.remove(name)
        }
    }

    class Lease internal constructor(private val name: String) : AutoCloseable {
        private val released = AtomicBoolean(false)

        /** Safe to call more than once. */
        override fun close() {
            if (released.compareAndSet(false, true)) release(name)
        }
    }
}
