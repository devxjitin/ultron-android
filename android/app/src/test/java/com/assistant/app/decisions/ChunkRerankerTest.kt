package com.assistant.app.decisions

import com.assistant.app.data.RagChunk
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ChunkRerankerTest {
    private lateinit var server: MockWebServer

    @Before fun start() { server = MockWebServer().also { it.start() } }
    @After fun stop() { server.shutdown() }

    private fun reranker() =
        ChunkReranker(DecisionsClient("k", baseUrl = server.url("/").toString().trimEnd('/')), timeoutMs = 2_000)

    private fun chunks(n: Int) = List(n) { RagChunk("doc", it, "chunk text $it", FloatArray(1)) }

    private fun scores(vararg values: Double): String =
        values.withIndex().joinToString(",", """{"answers":{""", """},"usage":{"total_input_tokens":10}}""") { (i, v) ->
            """"c$i":{"type":"score","score":$v,"confidence":0.8}"""
        }

    private fun rerank(list: List<RagChunk>) = runBlocking { reranker().rerank("what is the fee", list) }

    @Test fun keepsOnlyRelevantChunksBestFirst() {
        server.enqueue(MockResponse().setBody(scores(0.2, 2.8, 1.6, 0.9, 2.1)))
        val kept = rerank(chunks(5))!!
        assertEquals(listOf(1, 4, 2), kept.map { it.chunkIndex })
    }

    @Test fun keepsAtMostFour() {
        server.enqueue(MockResponse().setBody(scores(3.0, 2.9, 2.8, 2.7, 2.6, 2.5)))
        assertEquals(4, rerank(chunks(6))!!.size)
    }

    @Test fun nothingRelevantIsEmptyNotNull() {
        server.enqueue(MockResponse().setBody(scores(0.1, 0.4, 1.0)))
        val kept = rerank(chunks(3))
        assertTrue(kept != null && kept.isEmpty())
    }

    @Test fun failedCallReturnsNullSoCallerKeepsOldRanking() {
        server.enqueue(MockResponse().setResponseCode(429))
        assertNull(rerank(chunks(3)))
    }

    @Test fun tooFewCandidatesSkipsTheCall() {
        assertNull(rerank(chunks(1)))
        assertEquals(0, server.requestCount)
    }

    @Test fun sendsOnlyFirstTwentyCandidatesWithQuestionAsInput() {
        server.enqueue(MockResponse().setBody(scores(*DoubleArray(20) { 0.0 })))
        rerank(chunks(30))
        val body = org.json.JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("what is the fee", body.getString("input"))
        assertEquals(20, body.getJSONObject("questions").length())
        assertEquals(4, body.getJSONObject("questions").getJSONObject("c0").getJSONArray("criteria").length())
    }
}
