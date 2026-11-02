package com.assistant.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteSettingsTest {
    private fun settings(vararg pairs: Pair<String, String>) = RemoteSettings.from(mapOf(*pairs)::get)

    @Test
    fun emptyConfigGivesBundledDefaults() {
        assertEquals(RemoteSettings.DEFAULTS, settings())
    }

    @Test
    fun validValuesAreApplied() {
        val s = settings(
            "session_max_minutes" to "20",
            "idle_timeout_seconds" to "60",
            "update_check_minutes" to "30",
            "tools_disabled" to """["send_sms"," call_phone "]""",
            "min_supported_version" to "42"
        )
        assertEquals(20, s.sessionMaxMinutes)
        assertEquals(60, s.idleTimeoutSeconds)
        assertEquals(30, s.updateCheckMinutes)
        assertEquals(42, s.minSupportedVersion)
        assertTrue(s.isToolDisabled("send_sms"))
        assertTrue(s.isToolDisabled("call_phone"))
        assertFalse(s.isToolDisabled("get_time"))
    }

    @Test
    fun outOfRangeOrMalformedNumbersFallBack() {
        val s = settings(
            "session_max_minutes" to "9999",
            "idle_timeout_seconds" to "abc",
            "turn_timeout_seconds" to "-3",
            "max_tool_result_chars" to ""
        )
        assertEquals(RemoteSettings.DEFAULTS.sessionMaxMinutes, s.sessionMaxMinutes)
        assertEquals(RemoteSettings.DEFAULTS.idleTimeoutSeconds, s.idleTimeoutSeconds)
        assertEquals(RemoteSettings.DEFAULTS.turnTimeoutSeconds, s.turnTimeoutSeconds)
        assertEquals(RemoteSettings.DEFAULTS.maxToolResultChars, s.maxToolResultChars)
    }

    @Test
    fun brokenJsonKeepsDefaults() {
        val s = settings("tools_disabled" to "not json", "live_models_allowed" to "{oops")
        assertTrue(s.toolsDisabled.isEmpty())
        assertEquals(AssistantOptions.liveModels, s.liveModels)
    }

    @Test
    fun modelListAndDefaultComeFromRemote() {
        val s = settings(
            "live_models_allowed" to """[{"id":"m-a","label":"A"},{"id":"m-b","label":"B"}]""",
            "live_model_default" to "m-b"
        )
        assertEquals(listOf("m-a", "m-b"), s.liveModels.map { it.id })
        assertEquals("m-b", s.liveModelDefault)
    }

    @Test
    fun defaultModelNotInListFallsBackToFirstAllowed() {
        val s = settings(
            "live_models_allowed" to """[{"id":"m-a","label":"A"}]""",
            "live_model_default" to "ghost"
        )
        assertEquals("m-a", s.liveModelDefault)
    }

    @Test
    fun emptyModelListIsRejected() {
        val s = settings("live_models_allowed" to "[]")
        assertEquals(AssistantOptions.liveModels, s.liveModels)
    }
}
