package com.assistant.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BuiltInSettingsTest {
    @Test fun `notifications are not read out until the user switches it on`() {
        assertFalse(AssistantSettings().announceNotificationsEnabled)
    }

    @Test fun `prompts default to the built-in text`() {
        val settings = AssistantSettings()
        assertEquals(DEFAULT_SYSTEM_PROMPT.trim(), settings.systemPrompt)
        assertEquals(DEFAULT_DOCUMENT_FORMAT_PROMPT, settings.documentFormatPrompt)
    }
}
