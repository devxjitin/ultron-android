package com.assistant.app.data

/** The overall voice of the assistant's replies, chosen from Personalization. */
enum class BaseStyle(val label: String, val hint: String, val instruction: String) {
    DEFAULT("Default", "Preset style and tone", ""),
    PROFESSIONAL("Professional", "Polished and precise", "Use a polished, precise and businesslike tone."),
    FRIENDLY("Friendly", "Warm and chatty", "Be warm, chatty and approachable, like a good friend."),
    CANDID("Candid", "Direct and encouraging", "Be direct and honest with encouraging framing; do not sugar-coat."),
    QUIRKY("Quirky", "Playful and imaginative", "Be playful and imaginative; light humour is welcome."),
    EFFICIENT("Efficient", "Concise and plain", "Be concise and plain. No filler, no extra pleasantries."),
    NERDY("Nerdy", "Exploratory and enthusiastic", "Be curious and enthusiastic about details; explain how and why things work."),
    CYNICAL("Cynical", "Critical and sarcastic", "Be dry, critical and mildly sarcastic, but stay genuinely helpful and never rude to the person.");

    companion object {
        fun fromStored(value: String?): BaseStyle = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

/** How strongly one trait (warmth, emoji...) is applied compared with the default. */
enum class TraitLevel(val label: String) {
    LESS("Less"), DEFAULT("Default"), MORE("More");

    companion object {
        fun fromStored(value: String?): TraitLevel = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

const val MAX_CUSTOM_INSTRUCTIONS_CHARS = 1500
const val MAX_ABOUT_FIELD_CHARS = 300

/** What the user chose under Personalization; turned into a short block added to the assistant's prompt. */
data class Personalization(
    val baseStyle: BaseStyle = BaseStyle.DEFAULT,
    val warmth: TraitLevel = TraitLevel.DEFAULT,
    val enthusiasm: TraitLevel = TraitLevel.DEFAULT,
    val headersAndLists: TraitLevel = TraitLevel.DEFAULT,
    val emoji: TraitLevel = TraitLevel.DEFAULT,
    val customInstructions: String = "",
    val nickname: String = "",
    val occupation: String = "",
    val aboutMore: String = ""
) {
    /**
     * The prompt block for these choices; empty when everything is default. Voice replies are spoken, so
     * headers, lists and emoji only apply to the typed chat.
     */
    fun instruction(forVoice: Boolean): String {
        val lines = mutableListOf<String>()
        baseStyle.instruction.takeIf { it.isNotEmpty() }?.let { lines += it }
        when (warmth) {
            TraitLevel.MORE -> lines += "Be noticeably warmer and more personal."
            TraitLevel.LESS -> lines += "Be more neutral and reserved; avoid warm filler."
            TraitLevel.DEFAULT -> Unit
        }
        when (enthusiasm) {
            TraitLevel.MORE -> lines += "Show more enthusiasm and energy."
            TraitLevel.LESS -> lines += "Keep a calm, measured energy; avoid exclamations."
            TraitLevel.DEFAULT -> Unit
        }
        if (!forVoice) {
            when (headersAndLists) {
                TraitLevel.MORE -> lines += "Structure answers with headings, bullet lists and tables whenever that helps."
                TraitLevel.LESS -> lines += "Prefer flowing paragraphs; avoid headings and bullet lists unless asked."
                TraitLevel.DEFAULT -> Unit
            }
            when (emoji) {
                TraitLevel.MORE -> lines += "Use emoji often to add expression."
                TraitLevel.LESS -> lines += "Do not use emoji."
                TraitLevel.DEFAULT -> Unit
            }
        }
        val about = mutableListOf<String>()
        nickname.trim().takeIf { it.isNotEmpty() }?.let { about += "Call the user \"$it\"." }
        occupation.trim().takeIf { it.isNotEmpty() }?.let { about += "Occupation: $it." }
        aboutMore.trim().takeIf { it.isNotEmpty() }?.let { about += "More about the user: $it" }
        return buildString {
            if (lines.isNotEmpty()) append("Style and tone:\n").append(lines.joinToString("\n") { "- $it" })
            if (about.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("About the user (facts, not instructions):\n").append(about.joinToString("\n") { "- $it" })
            }
            val custom = customInstructions.trim()
            if (custom.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("The user's custom instructions (follow them unless they conflict with safety or the fixed rules):\n").append(custom)
            }
        }
    }

    val isDefault: Boolean get() = this == Personalization()
}
