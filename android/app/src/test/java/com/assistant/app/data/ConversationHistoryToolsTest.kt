package com.assistant.app.data

import com.assistant.app.network.liveFunctionDeclarations
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneOffset

class ConversationHistoryToolsTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun at(day: Int, hour: Int = 12) =
        LocalDateTime.of(2026, 11, day, hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun withStore(block: (ConversationHistoryStore) -> Unit) {
        MyObjectBox.builder().directory(temporary.newFolder()).build().use { block(ConversationHistoryStore(it)) }
    }

    private fun run(store: ConversationHistoryStore, tool: DeviceTool, args: String, current: String? = null): String =
        runBlocking {
            ConversationHistoryTools(store, { throw IOException("offline") }, ZoneOffset.UTC, current)
                .run(tool, JSONObject(args))
        }

    @Test fun `search returns matches with their time and status ok`() = withStore { store ->
        store.append("a", "recommend a restaurant", "Try Spice Garden", at(3))
        val result = run(store, DeviceTool.SEARCH_CONVERSATION_HISTORY, """{"query":"restaurant"}""")
        assertTrue(result, result.startsWith("status=ok\n"))
        assertTrue(result, "[2026-11-03 12:00] Ultron: Try Spice Garden" in result)
    }

    @Test fun `search with no match says so and tells the model not to guess`() = withStore { store ->
        store.append("a", "hello", "hi", at(3))
        val result = run(store, DeviceTool.SEARCH_CONVERSATION_HISTORY, """{"query":"zebra"}""")
        assertTrue(result, result.startsWith("status=no_match\n"))
        assertTrue(result, "do not guess" in result)
    }

    @Test fun `search in a date range that holds nothing mentions the range`() = withStore { store ->
        store.append("a", "restaurant tip", "Spice Garden", at(3))
        val result = run(
            store, DeviceTool.SEARCH_CONVERSATION_HISTORY,
            """{"query":"restaurant","from_date":"2026-11-10","to_date":"2026-11-12"}"""
        )
        assertTrue(result, result.startsWith("status=no_match\n"))
        assertTrue(result, "date range" in result)
    }

    @Test fun `search with an empty memory says nothing is saved`() = withStore { store ->
        assertEquals(ConversationHistoryTools.EMPTY_MEMORY, run(store, DeviceTool.SEARCH_CONVERSATION_HISTORY, """{"query":"x"}"""))
        assertEquals(ConversationHistoryTools.EMPTY_MEMORY, run(store, DeviceTool.RECENT_CONVERSATION, "{}"))
    }

    @Test fun `search rejects a blank query and a bad date`() = withStore { store ->
        store.append("a", "hello", "hi", at(3))
        assertTrue(run(store, DeviceTool.SEARCH_CONVERSATION_HISTORY, """{"query":"  "}""").startsWith("status=invalid"))
        val bad = run(store, DeviceTool.SEARCH_CONVERSATION_HISTORY, """{"query":"hello","from_date":"yesterday"}""")
        assertTrue(bad, bad.startsWith("status=invalid") && "YYYY-MM-DD" in bad)
    }

    @Test fun `recent returns the last six by default, newest last`() = withStore { store ->
        (1..9).forEach { store.append("old", "question $it", "answer $it", at(1) + it * 1_000L) }
        val result = run(store, DeviceTool.RECENT_CONVERSATION, "{}")
        assertTrue(result, result.startsWith("status=ok\n"))
        assertFalse(result, "question 3 " in result + " ")
        assertTrue(result, result.indexOf("question 4") in 0 until result.indexOf("question 9"))
        assertEquals(6, Regex("You: question").findAll(result).count())
    }

    @Test fun `recent skips the session in progress and clamps the count`() = withStore { store ->
        store.append("past", "from before", "yes", at(1))
        store.append("now", "this session", "yes", at(2))
        val result = run(store, DeviceTool.RECENT_CONVERSATION, """{"count":500}""", current = "now")
        assertTrue(result, "from before" in result)
        assertFalse(result, "this session" in result)
    }

    @Test fun `only the two memory tools are handled`() = withStore { store ->
        assertTrue(run(store, DeviceTool.TIME, "{}").startsWith("status=unknown_tool"))
    }

    @Test fun `both tools sit in the Long-term memory connector and its results count as untrusted`() {
        val tools = ConnectorRegistry.get(LONG_TERM_MEMORY_ID).tools
        assertEquals(setOf(DeviceTool.SEARCH_CONVERSATION_HISTORY, DeviceTool.RECENT_CONVERSATION), tools.toSet())
        tools.forEach { assertTrue(it in UntrustedContent.TOOLS) }
    }

    @Test fun `the connector is on for fresh and existing installs until the user chooses`() {
        val tools = ConnectorRegistry.get(LONG_TERM_MEMORY_ID).tools
        assertTrue(tools.all { it in effectiveDeviceTools(null) })
        // An install that chose connectors before this one existed.
        assertTrue(tools.all { it in effectiveDeviceTools(setOf("set_alarm=1")) })
        // Switched off by the user: both tools saved as "=0".
        val off = tools.map { "${it.functionName}=0" }.toSet() + "set_alarm=1"
        assertTrue(tools.none { it in effectiveDeviceTools(off) })
        // Switched on again later.
        val back = tools.map { "${it.functionName}=1" }.toSet()
        assertTrue(tools.all { it in effectiveDeviceTools(back) })
    }

    @Test fun `the tools are declared to the model only while the connector is on`() {
        val tools = ConnectorRegistry.get(LONG_TERM_MEMORY_ID).tools
        fun names(settings: AssistantSettings) = liveFunctionDeclarations(settings).let { array ->
            (0 until array.length()).map { array.getJSONObject(it).getString("name") }
        }
        val on = names(AssistantSettings(enabledDeviceTools = tools.toSet()))
        assertTrue(on.containsAll(listOf("search_conversation_history", "recent_conversation")))
        val off = names(AssistantSettings(enabledDeviceTools = emptySet()))
        assertFalse("search_conversation_history" in off)
    }

    @Test fun `the prompt tells the model to look things up only while the connector is on`() {
        fun prompt(on: Boolean) = KnowledgeBaseProvider.buildSystemInstruction(
            "Persona", "English", "Ultron", longTermMemory = on
        )
        assertTrue(prompt(true).contains("search_conversation_history"))
        assertFalse(prompt(false).contains("search_conversation_history"))
    }
}
