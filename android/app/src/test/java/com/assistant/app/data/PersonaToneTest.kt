package com.assistant.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaToneTest {
    @Test fun voicePersonaIsLoyalToOwnerAndSupportPerPersonTone() {
        listOf(DEFAULT_SYSTEM_PROMPT).forEach { prompt ->
            assertTrue(prompt.contains("Jitin is your person"))
            assertTrue(prompt.contains("Tone rule: be polite with"))
            assertTrue(prompt.contains("save_memory"))
            assertTrue(prompt.contains("never in your actions"))
        }
    }

    @Test fun policyKeepsToolNamesOutOfSpeech() {
        assertTrue(KNOWLEDGE_GROUNDING_POLICY.contains("never speak an internal name"))
        assertFalse(KNOWLEDGE_GROUNDING_POLICY.contains("tool is switched off"))
    }
}
