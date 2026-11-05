package com.assistant.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class KnowledgeKeyTest {
    @Test fun `uses the knowledge key when one is saved`() {
        assertEquals("k", knowledgeKeyFor("k", "g"))
    }

    @Test fun `falls back to the gemini key when the knowledge key is missing or blank`() {
        assertEquals("g", knowledgeKeyFor(null, "g"))
        assertEquals("g", knowledgeKeyFor("  ", "g"))
    }

    @Test fun `is blank when no key exists`() {
        assertEquals("", knowledgeKeyFor(null, null))
    }
}
