package com.assistant.app.decisions

import com.assistant.app.data.MemoryItem
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MemoryTidierTest {
    private lateinit var server: MockWebServer

    @Before fun start() { server = MockWebServer().also { it.start() } }
    @After fun stop() { server.shutdown() }

    private fun tidier() = MemoryTidier(DecisionsClient("k", baseUrl = server.url("/").toString().trimEnd('/')))

    private val stored = listOf(
        MemoryItem("a", "User lives in Delhi", 1),
        MemoryItem("b", "User likes strong tea", 2),
        MemoryItem("c", "Sister is called Neha", 3),
    )

    private fun answer(vararg pairs: Triple<String, Double, Double>): String =
        pairs.joinToString(",", """{"answers":{""", """},"usage":{"total_input_tokens":50}}""") { (k, p, c) ->
            """"$k":{"type":"noul","noul":$p,"confidence":$c}"""
        }

    private fun allNo(except: Map<String, Pair<Double, Double>> = emptyMap()): String =
        answer(*stored.indices.flatMap { i -> listOf("dup_$i", "con_$i") }
            .map { k -> except[k]?.let { Triple(k, it.first, it.second) } ?: Triple(k, 0.02, 0.98) }.toTypedArray())

    private fun ids(note: String, items: List<MemoryItem> = stored) = runBlocking { tidier().outdatedIds(note, items) }

    @Test fun contradictedNoteIsMarkedOutdated() {
        server.enqueue(MockResponse().setBody(allNo(mapOf("con_0" to (0.97 to 0.97)))))
        assertEquals(setOf("a"), ids("User moved to Pune"))
    }

    @Test fun duplicateNoteIsMarkedOutdated() {
        server.enqueue(MockResponse().setBody(allNo(mapOf("dup_2" to (0.95 to 0.95)))))
        assertEquals(setOf("c"), ids("Her sister's name is Neha"))
    }

    @Test fun unsureAnswersRemoveNothing() {
        server.enqueue(MockResponse().setBody(allNo(mapOf("dup_1" to (0.7 to 0.7), "con_0" to (0.84 to 0.84)))))
        assertTrue(ids("User prefers tea").isEmpty())
    }

    @Test fun failedCallRemovesNothing() {
        server.enqueue(MockResponse().setResponseCode(503))
        assertTrue(ids("anything").isEmpty())
    }

    @Test fun noStoredNotesMeansNoCall() {
        assertTrue(ids("first note", emptyList()).isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test fun sameTextNoteIsNotComparedWithItself() {
        server.enqueue(MockResponse().setBody(allNo().replace("\"c0\"", "\"x\"")))
        ids("user lives in delhi")
        val body = org.json.JSONObject(server.takeRequest().body.readUtf8())
        assertEquals(4, body.getJSONObject("questions").length()) // two other notes, two questions each
    }

    @Test fun asksTwoQuestionsPerStoredNoteWithNoteTextInInstructions() {
        server.enqueue(MockResponse().setBody(allNo()))
        ids("User moved to Pune")
        val body = org.json.JSONObject(server.takeRequest().body.readUtf8())
        val questions = body.getJSONObject("questions")
        assertEquals(6, questions.length())
        assertTrue(questions.getJSONObject("dup_1").getString("instructions").contains("strong tea"))
        assertTrue(body.getString("input").contains("Pune"))
    }
}
