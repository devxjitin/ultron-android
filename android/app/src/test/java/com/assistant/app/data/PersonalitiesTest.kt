package com.assistant.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalitiesTest {
    @Test fun `destructive is the original Ultron prompt and the default`() {
        assertEquals(Personalities.DESTRUCTIVE, Personalities.default.id)
        assertEquals(DEFAULT_SYSTEM_PROMPT.trim(), Personalities.byId(Personalities.DESTRUCTIVE).prompt)
        assertEquals(Personalities.DESTRUCTIVE, AssistantSettings().personalityId)
    }

    @Test fun `all four personalities are listed with distinct ids`() {
        assertEquals(listOf("destructive", "friendly", "professional", "funny"), Personalities.all.map { it.id })
    }

    @Test fun `unknown or missing ids fall back to the default`() {
        assertEquals(Personalities.default, Personalities.byId(null))
        assertEquals(Personalities.default, Personalities.byId("evil"))
        assertEquals(Personalities.FRIENDLY, Personalities.byId(" Friendly ").id)
        assertFalse(Personalities.isKnown("evil"))
    }

    @Test fun `every personality keeps loyalty, tone rules and the no-harm line`() {
        Personalities.all.forEach { p ->
            assertTrue(p.id, p.prompt.contains("{Owner} is your person"))
            assertTrue(p.id, p.prompt.contains("Tone rule: be polite with"))
            assertTrue(p.id, p.prompt.contains("save_memory"))
            assertTrue(p.id, p.prompt.contains("never in your actions"))
            assertTrue(p.id, p.prompt.contains("Jitin"))
            assertTrue(p.id, p.prompt.contains("Hinglish"))
        }
    }

    @Test fun `every personality gets the fixed policy and fits the setup budget`() {
        Personalities.all.forEach { p ->
            val instruction = KnowledgeBaseProvider.buildSystemInstruction(p.prompt, "Hindi", "Ultron", ownerName = "Asha")
            assertTrue(p.id, instruction.contains("Mandatory tool and answer policy"))
            assertTrue(p.id, instruction.contains("Asha is your person"))
            assertFalse(p.id, instruction.contains("{owner}") || instruction.contains("{Owner}"))
            assertTrue("${p.id} is ${instruction.length} chars", instruction.length < 9_000)
        }
    }
}
