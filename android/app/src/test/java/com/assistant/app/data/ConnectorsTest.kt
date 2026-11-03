package com.assistant.app.data

import com.assistant.app.network.liveFunctionDeclarations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectorsTest {
    @Test fun everyDeviceToolBelongsToExactlyOneConnector() {
        val owners = PhoneConnectors.all.flatMap { c -> c.tools.map { it to c.id } }.groupBy({ it.first }, { it.second })
        DeviceTool.entries.forEach { assertEquals("$it", 1, owners[it]?.size ?: 0) }
    }

    @Test fun functionNamesAreUnique() {
        val names = DeviceTool.entries.map { it.functionName }
        assertEquals(names.size, names.toSet().size)
    }

    /** Every extra tool makes the Live setup bigger and gives the model one more wrong choice. */
    @Test fun allConnectorsTogetherStayUnderTheToolCap() {
        val settings = AssistantSettings(enabledDeviceTools = PhoneConnectors.all.flatMap { it.tools }.toSet())
        val count = liveFunctionDeclarations(settings).length()
        assertTrue("$count tools declared", count <= TOOL_CAP)
    }

    private companion object {
        const val TOOL_CAP = 110
    }
}
