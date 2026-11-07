package com.assistant.app.network

import com.assistant.app.data.AssistantSettings
import com.assistant.app.data.DeviceTool
import com.assistant.app.data.effectiveDeviceTools
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NonBlockingToolsTest {
    private fun declaration(name: String): JSONObject {
        val settings = AssistantSettings(enabledDeviceTools = DeviceTool.entries.toSet())
        val all = liveFunctionDeclarations(settings)
        return (0 until all.length()).map { all.getJSONObject(it) }.first { it.getString("name") == name }
    }

    @Test fun slowToolsAreDeclaredNonBlocking() {
        listOf("web_search", "fetch_webpage", "get_weather", "youtube_search", "look_at_screen", "search_knowledge_base")
            .forEach { assertEquals(it, "NON_BLOCKING", declaration(it).optString("behavior")) }
    }

    @Test fun quickToolsStayBlocking() {
        listOf("set_volume", "set_flashlight", "set_alarm", "read_screen")
            .forEach { assertFalse(it, declaration(it).has("behavior")) }
    }

    @Test fun everyNonBlockingNameIsARealTool() {
        val known = DeviceTool.entries.map { it.functionName } + "search_knowledge_base"
        assertTrue(DeviceTool.NON_BLOCKING_NAMES.all { it in known })
    }

    @Test fun defaultToolsStillBuild() {
        val settings = AssistantSettings(enabledDeviceTools = effectiveDeviceTools(null))
        assertTrue(liveFunctionDeclarations(settings).length() > 0)
    }
}
