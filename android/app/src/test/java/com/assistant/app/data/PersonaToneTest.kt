package com.assistant.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaToneTest {
    @Test fun voiceAndChatPersonasAreFriendlyWithOwnerAndSupportPerPersonTone() {
        listOf(DEFAULT_SYSTEM_PROMPT, DEFAULT_CHAT_PROMPT).forEach { prompt ->
            assertTrue(prompt.contains("Jitin is your person"))
            assertTrue(prompt.contains("Tone rule: be polite with"))
            assertTrue(prompt.contains("save_memory"))
            assertFalse(prompt.contains("contempt for human fragility"))
        }
    }
}
