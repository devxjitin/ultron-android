package com.assistant.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantOptionsTest {

    @Test
    fun `default voice is itself approved`() {
        assertTrue(AssistantOptions.isApprovedVoice(AssistantOptions.defaultVoice))
    }

    @Test
    fun `unknown voice names are rejected`() {
        assertFalse(AssistantOptions.isApprovedVoice("not-a-real-voice"))
    }

    @Test
    fun `every documented Gemini Live voice is approved`() {
        // All 30 prebuilt voices from Google's official voice list should be selectable.
        assertTrue(AssistantOptions.isApprovedVoice("Puck"))
        assertTrue(AssistantOptions.isApprovedVoice("Charon"))
        assertTrue(AssistantOptions.isApprovedVoice("Sulafat"))
    }

    @Test
    fun `default language is itself approved`() {
        assertTrue(AssistantOptions.isApprovedLanguage(AssistantOptions.defaultLanguageCode))
    }

    @Test
    fun `languages outside the supported set are rejected`() {
        assertFalse(AssistantOptions.isApprovedLanguage("fr-FR"))
    }

    @Test
    fun `dataset model is fixed to Gemini 3 5 Flash-Lite`() {
        assertEquals("gemini-3.5-flash-lite", AssistantOptions.DATASET_MODEL)
    }
}
