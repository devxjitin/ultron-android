package com.assistant.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class McpToolsTest {
    private fun tool(name: String, readOnly: Boolean) = McpToolSpec(name, "", "{}", readOnly)

    @Test fun onlyReadOnlyToolsAreUsed() {
        val tools = listOf(tool("get_file", true), tool("create_issue", false), tool("unknown_kind", false))
        assertEquals(setOf("get_file"), effectiveMcpTools(tools))
    }
}
