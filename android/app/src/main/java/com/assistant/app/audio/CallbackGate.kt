package com.assistant.app.audio

/**
 * Drops callbacks that were queued before their owner was stopped. Once [close]d, [run] is a
 * no-op, so a late event posted by an old microphone can't reach whatever session replaced it.
 */
class CallbackGate {
    @Volatile private var open = true

    fun close() {
        open = false
    }

    fun run(block: () -> Unit) {
        if (open) block()
    }
}
