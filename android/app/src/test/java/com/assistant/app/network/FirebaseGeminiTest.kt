package com.assistant.app.network

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class FirebaseGeminiTest {

    private fun declaration(name: String, parameters: JSONObject? = null): JSONObject =
        JSONObject().put("name", name).put("description", "does $name").apply { parameters?.let { put("parameters", it) } }

    @Test
    fun declarationWithoutParametersConverts() {
        val result = FirebaseGemini.functionDeclarations(JSONArray().put(declaration("get_time")))
        assertEquals(1, result.size)
    }

    @Test
    fun flatNestedAndEnumParametersConvert() {
        val parameters = JSONObject()
            .put("type", "OBJECT")
            .put(
                "properties",
                JSONObject()
                    .put("query", JSONObject().put("type", "STRING").put("description", "what to find"))
                    .put("count", JSONObject().put("type", "INTEGER"))
                    .put("ratio", JSONObject().put("type", "NUMBER"))
                    .put("flag", JSONObject().put("type", "BOOLEAN"))
                    .put("mode", JSONObject().put("type", "STRING").put("enum", JSONArray(listOf("a", "b"))))
                    .put("tags", JSONObject().put("type", "ARRAY").put("items", JSONObject().put("type", "STRING")))
                    .put(
                        "filter",
                        JSONObject().put("type", "OBJECT")
                            .put("properties", JSONObject().put("owner", JSONObject().put("type", "STRING")))
                            .put("required", JSONArray().put("owner"))
                    )
            )
            .put("required", JSONArray().put("query"))
        val result = FirebaseGemini.functionDeclarations(JSONArray().put(declaration("search", parameters)).put(declaration("other")))
        assertEquals(2, result.size)
    }

    @Test
    fun resultAndArgsRoundTrip() {
        val args = FirebaseGemini.argsObject(mapOf("city" to kotlinx.serialization.json.JsonPrimitive("Delhi"), "days" to kotlinx.serialization.json.JsonPrimitive(3)))
        assertEquals("Delhi", args.getString("city"))
        assertEquals(3, args.getInt("days"))
        assertEquals("ok", FirebaseGemini.resultObject("ok")["result"].toString().trim('"'))
    }
}
