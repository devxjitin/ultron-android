package com.assistant.app.network

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SseAnswerTest {
    private fun source(text: String) = Buffer().writeUtf8(text)

    @Test fun `the matching answer is returned and later events stay unread`() {
        val buffer = source(
            "data: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/progress\"}\n\n" +
                "data: {\"jsonrpc\":\"2.0\",\"id\":7,\"result\":{\"ok\":true}}\n\n" +
                "data: {\"jsonrpc\":\"2.0\",\"id\":8,\"result\":{}}\n\n"
        )
        val answer = readSseAnswer(buffer, 7)
        assertTrue(answer.getJSONObject("result").getBoolean("ok"))
        assertTrue("a later event should not have been consumed", buffer.size > 0)
    }

    @Test fun `an event may span several data lines`() {
        val answer = readSseAnswer(source("data: {\"id\":3,\n" + "data: \"result\":{\"v\":1}}\n\n"), 3)
        assertEquals(1, answer.getJSONObject("result").getInt("v"))
    }

    @Test fun `an answer for another request is skipped`() {
        val answer = readSseAnswer(source("data: {\"id\":1,\"result\":{}}\n\ndata: {\"id\":2,\"result\":{\"x\":2}}\n\n"), 2)
        assertEquals(2, answer.getJSONObject("result").getInt("x"))
    }

    @Test fun `a final event without a blank line is still used`() {
        val answer = readSseAnswer(source("event: message\ndata: {\"id\":5,\"result\":{\"x\":1}}"), 5)
        assertEquals(1, answer.getJSONObject("result").getInt("x"))
    }

    @Test fun `no answer at all is an error`() {
        try {
            readSseAnswer(source("data: {\"id\":9,\"result\":{}}\n\n"), 1)
            fail("expected McpException")
        } catch (expected: McpException) {
        }
    }
}
