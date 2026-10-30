package com.assistant.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaToneTest {
    @Test fun voicePersonaIsLoyalToOwnerAndSupportPerPersonTone() {
        listOf(DEFAULT_SYSTEM_PROMPT).forEach { prompt ->
            assertTrue(prompt.contains("{Owner} is your person"))
            assertTrue(prompt.contains("Tone rule: be polite with"))
            assertTrue(prompt.contains("save_memory"))
            assertTrue(prompt.contains("never in your actions"))
        }
    }

    @Test fun ownerNameFillsPlaceholdersAndFallsBackToTheUser() {
        val named = KnowledgeBaseProvider.buildSystemInstruction(DEFAULT_SYSTEM_PROMPT, "Hindi", "Ultron", ownerName = "Asha")
        assertTrue(named.contains("Asha is your person"))
        assertFalse(named.contains("{owner}") || named.contains("{Owner}"))
        val unnamed = KnowledgeBaseProvider.buildSystemInstruction(DEFAULT_SYSTEM_PROMPT, "Hindi", "Ultron")
        assertTrue(unnamed.contains("The user is your person"))
    }

    @Test fun policyKeepsToolNamesOutOfSpeech() {
        assertTrue(KNOWLEDGE_GROUNDING_POLICY.contains("never speak an internal name"))
        assertFalse(KNOWLEDGE_GROUNDING_POLICY.contains("tool is switched off"))
    }
}
