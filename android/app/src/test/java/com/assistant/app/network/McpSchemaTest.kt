package com.assistant.app.network

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class McpSchemaTest {
    private fun convert(json: String): JSONObject? = McpSchema.toGeminiParameters(json)

    @Test fun `a tool without arguments has no parameters`() {
        assertNull(convert("""{"type":"object","properties":{}}"""))
    }

    @Test fun `a local reference is resolved to the object it points to`() {
        val result = convert(
            """{"type":"object","properties":{"user":{"${'$'}ref":"#/${'$'}defs/User"}},
               "${'$'}defs":{"User":{"type":"object","properties":{"name":{"type":"string"}}}}}"""
        )!!
        val user = result.getJSONObject("properties").getJSONObject("user")
        assertEquals("OBJECT", user.getString("type"))
        assertEquals("STRING", user.getJSONObject("properties").getJSONObject("name").getString("type"))
    }

    @Test fun `the description next to a reference is kept`() {
        val result = convert(
            """{"type":"object","properties":{"u":{"${'$'}ref":"#/definitions/U","description":"The user"}},
               "definitions":{"U":{"type":"string","description":"generic"}}}"""
        )!!
        assertEquals("The user", result.getJSONObject("properties").getJSONObject("u").getString("description"))
    }

    @Test fun `an unresolvable reference becomes a described string instead of vanishing`() {
        val result = convert("""{"type":"object","properties":{"x":{"${'$'}ref":"#/nowhere"}}}""")!!
        val x = result.getJSONObject("properties").getJSONObject("x")
        assertEquals("STRING", x.getString("type"))
        assertTrue(x.getString("description").contains("reference"))
    }

    @Test fun `a union keeps the first option and names the others`() {
        val result = convert("""{"type":"object","properties":{"v":{"anyOf":[{"type":"integer"},{"type":"string"}]}}}""")!!
        val v = result.getJSONObject("properties").getJSONObject("v")
        assertEquals("INTEGER", v.getString("type"))
        assertTrue(v.getString("description").contains("string"))
    }

    @Test fun `a nullable value is just its real type`() {
        val result = convert("""{"type":"object","properties":{"v":{"anyOf":[{"type":"null"},{"type":"string"}]}}}""")!!
        val v = result.getJSONObject("properties").getJSONObject("v")
        assertEquals("STRING", v.getString("type"))
        assertTrue(!v.has("description"))
    }

    @Test fun `a tool that converts exactly reports no simplifications`() {
        val analysis = McpSchema.analyze("""{"type":"object","properties":{"id":{"type":"integer"},"tags":{"type":"array","items":{"type":"string"}}}}""")
        assertTrue(analysis.issues.isEmpty())
    }

    @Test fun `every place the schema had to be simplified is reported`() {
        val analysis = McpSchema.analyze(
            """{"type":"object","properties":{
                 "v":{"anyOf":[{"type":"integer"},{"type":"string"}]},
                 "x":{"${'$'}ref":"#/nowhere"},
                 "blob":{"type":"object"}}}"""
        )
        assertTrue(analysis.issues.any { it.contains("more than one type") })
        assertTrue(analysis.issues.any { it.contains("unresolved") })
        assertTrue(analysis.issues.any { it.contains("free-form") })
    }

    @Test fun `a declaration says when argument types were simplified and carries the original schema for chat`() {
        val spec = com.assistant.app.data.McpToolSpec(
            "t", "Does things", """{"type":"object","properties":{"v":{"anyOf":[{"type":"integer"},{"type":"string"}]}}}""", false
        )
        val plain = ToolDeclarations.mcp(spec)
        assertTrue(plain.getString("description").contains("simplified"))
        assertTrue(!plain.has(ToolDeclarations.ORIGINAL_SCHEMA_KEY))
        val forChat = ToolDeclarations.mcp(spec, withOriginalSchema = true)
        assertTrue(forChat.getJSONObject(ToolDeclarations.ORIGINAL_SCHEMA_KEY).getJSONObject("properties").getJSONObject("v").has("anyOf"))
        val stripped = ToolDeclarations.withoutPrivateFields(org.json.JSONArray().put(forChat)).getJSONObject(0)
        assertTrue(!stripped.has(ToolDeclarations.ORIGINAL_SCHEMA_KEY))
        assertEquals("t", stripped.getString("name").removePrefix(com.assistant.app.data.GITHUB_TOOL_PREFIX))
    }

    @Test fun `required fields and arrays survive`() {
        val result = convert(
            """{"type":"object","properties":{"tags":{"type":"array","items":{"type":"string"}},"id":{"type":"integer"}},"required":["id"]}"""
        )!!
        assertEquals("ARRAY", result.getJSONObject("properties").getJSONObject("tags").getString("type"))
        assertEquals("id", result.getJSONArray("required").getString(0))
        assertNotNull(result.getJSONObject("properties").getJSONObject("tags").getJSONObject("items"))
    }
}
