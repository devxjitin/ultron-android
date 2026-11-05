package com.assistant.app.data

/** The phone-side connectors and the device tools each one owns, read from [ConnectorRegistry]. */
class PhoneConnector(val id: String, val tools: List<DeviceTool>)

object PhoneConnectors {
    val all: List<PhoneConnector> = ConnectorRegistry.all
        .map { PhoneConnector(it.id, it.tools) }

    private val byId = all.associateBy { it.id }

    fun tools(id: String): List<DeviceTool> = byId.getValue(id).tools
}
