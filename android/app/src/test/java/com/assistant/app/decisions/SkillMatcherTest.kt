package com.assistant.app.decisions

import com.assistant.app.data.SkillGroup
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SkillMatcherTest {
    private lateinit var server: MockWebServer

    @Before fun start() { server = MockWebServer().also { it.start() } }
    @After fun stop() { server.shutdown() }

    private val skills = listOf(
        SkillGroup("whatsapp", "Send and read WhatsApp messages", "", emptyList()),
        SkillGroup("ultron-features", "Explain what Ultron can do", "", emptyList()),
    )

    private fun suggest(request: String = "tell Rahul on whatsapp I'm late", list: List<SkillGroup> = skills) = runBlocking {
        SkillMatcher(DecisionsClient("k", baseUrl = server.url("/").toString().trimEnd('/'))).suggest(request, list)
    }

    private fun reply(choice: String, confidence: Double) =
        """{"answers":{"skill":{"type":"choice","choice":"$choice","probabilities":{"$choice":$confidence},"confidence":$confidence}},"usage":{"total_input_tokens":60}}"""

    @Test fun confidentMatchIsSuggested() {
        server.enqueue(MockResponse().setBody(reply("whatsapp", 0.93)))
        assertEquals("whatsapp", suggest())
    }

    @Test fun noneOrUnsureGivesNull() {
        server.enqueue(MockResponse().setBody(reply("none", 0.95)))
        assertNull(suggest())
        server.enqueue(MockResponse().setBody(reply("whatsapp", 0.5)))
        assertNull(suggest("another request"))
    }

    @Test fun failedCallOrNoSkillsMakesNoSuggestion() {
        server.enqueue(MockResponse().setResponseCode(503))
        assertNull(suggest())
        assertNull(suggest(list = emptyList()))
        assertNull(suggest(request = " "))
    }

    @Test fun optionsAreSkillNamesPlusNone() {
        server.enqueue(MockResponse().setBody(reply("whatsapp", 0.9)))
        suggest()
        val body = org.json.JSONObject(server.takeRequest().body.readUtf8())
        val criteria = body.getJSONObject("questions").getJSONObject("skill").getJSONObject("criteria")
        assertEquals(setOf("whatsapp", "ultron-features", "none"), criteria.keys().asSequence().toSet())
    }
}
