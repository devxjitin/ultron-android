package com.assistant.app.data

/** One tool a connected MCP server offers, as cached on the device. */
data class McpToolSpec(
    val name: String,
    val description: String,
    /** The tool's JSON Schema for its arguments, as JSON text. */
    val schema: String,
    val readOnly: Boolean
)
