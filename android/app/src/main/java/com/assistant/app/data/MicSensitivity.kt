package com.assistant.app.data

/** Software gain applied to microphone PCM before it is sent to the Live API. */
enum class MicSensitivity(val label: String, val gain: Float) {
    LOW("Low", 0.6f),
    MEDIUM("Medium", 1f),
    HIGH("High", 1.8f);

    companion object {
        fun fromStoredValue(value: String?): MicSensitivity =
            values().firstOrNull { it.name == value } ?: MEDIUM
    }
}
