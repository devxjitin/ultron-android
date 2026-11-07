package com.assistant.app.data

import com.assistant.app.network.liveFunctionDeclarations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectorsTest {
    @Test fun everyDeviceToolBelongsToExactlyOneConnector() {
        val owners = PhoneConnectors.all.flatMap { c -> c.tools.map { it to c.id } }.groupBy({ it.first }, { it.second })
        DeviceTool.entries.filter { it !in DeviceTool.ASSISTANT_OWN }.forEach { assertEquals("$it", 1, owners[it]?.size ?: 0) }
        DeviceTool.ASSISTANT_OWN.forEach { assertEquals("$it", 0, owners[it]?.size ?: 0) }
    }

    @Test fun functionNamesAreUnique() {
        val names = DeviceTool.entries.map { it.functionName }
        assertEquals(names.size, names.toSet().size)
    }

    @Test fun screenAndNavigationAreOffOnAFreshInstall() {
        val on = effectiveDeviceTools(null)
        PhoneConnectors.all.filter { it.id == "screen" || it.id == "navigation" }.forEach { connector ->
            connector.tools.forEach { assertTrue("${it.functionName} should be off", it !in on) }
        }
    }

    /** Every extra tool makes the Live setup bigger and gives the model one more wrong choice. */
    @Test fun allConnectorsTogetherStayUnderTheToolCap() {
        val settings = AssistantSettings(enabledDeviceTools = PhoneConnectors.all.flatMap { it.tools }.toSet())
        val count = liveFunctionDeclarations(settings).length()
        assertTrue("$count tools declared", count <= TOOL_CAP)
    }

    @Test fun connectorIdsAreUniqueAndEveryOneHasAPageText() {
        val ids = ConnectorRegistry.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { assertTrue("$it has no info text", ConnectorRegistry.info(it) != null) }
    }

    @Test fun aFreshInstallStartsWithTheSameSafeConnectorsAsBefore() {
        assertEquals(
            setOf("device_info", "device_controls", "alarms", "reminders", "apps_settings", "duckduckgo", "web_reader", "weather"),
            ConnectorRegistry.defaultIds.toSet()
        )
    }

    @Test fun onlyEssentialsAndSearchAreOnByDefault() {
        ConnectorRegistry.all.filter { it.defaultOn }.forEach {
            assertTrue("${it.id} is on by default", it.category in setOf(ConnectorCategory.ESSENTIALS, ConnectorCategory.SEARCH_MEDIA))
        }
    }

    @Test fun riskyConnectorsLiveInTheirOwnCategories() {
        fun category(id: String) = ConnectorRegistry.get(id).category
        assertEquals(ConnectorCategory.PHONE_CONTROL, category("screen"))
        assertEquals(ConnectorCategory.PHONE_CONTROL, category("navigation"))
        assertTrue(ConnectorCategory.PHONE_CONTROL.advanced)
    }

    private companion object {
        const val TOOL_CAP = 110
    }
}
