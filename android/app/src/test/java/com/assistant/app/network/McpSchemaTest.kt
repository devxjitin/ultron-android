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

    @Test fun `required fields and arrays survive`() {
        val result = convert(
            """{"type":"object","properties":{"tags":{"type":"array","items":{"type":"string"}},"id":{"type":"integer"}},"required":["id"]}"""
        )!!
        assertEquals("ARRAY", result.getJSONObject("properties").getJSONObject("tags").getString("type"))
        assertEquals("id", result.getJSONArray("required").getString(0))
        assertNotNull(result.getJSONObject("properties").getJSONObject("tags").getJSONObject("items"))
    }
}
