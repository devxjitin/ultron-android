package com.assistant.app.viewmodel

import com.assistant.app.data.UsageDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatDigestTest {
    private fun message(id: Long, fromUser: Boolean, text: String, isError: Boolean = false) =
        ChatMessage(id, fromUser = fromUser, text = text, isError = isError)

    @Test fun `nothing left out means no digest`() {
        val all = listOf(message(1, true, "hi"), message(2, false, "hello"))
        assertEquals("", earlierDigest(all, 2))
    }

    @Test fun `only the messages that were not sent in full are summarised`() {
        val all = listOf(message(1, true, "first question"), message(2, false, "first answer"), message(3, true, "second"), message(4, false, "second answer"))
        val digest = earlierDigest(all, 2)
        assertTrue(digest.contains("User: first question"))
        assertTrue(digest.contains("Assistant: first answer"))
        assertTrue(!digest.contains("second"))
    }

    @Test fun `failed replies are not part of the digest`() {
        val all = listOf(message(1, true, "q"), message(2, false, "boom", isError = true), message(3, true, "q2"))
        assertTrue(!earlierDigest(all, 1).contains("boom"))
    }

    @Test fun `a long history keeps the newest older lines within the limit`() {
        val all = (1..200L).map { message(it, it % 2 == 1L, "line $it " + "x".repeat(150)) } + message(201, true, "now")
        val digest = earlierDigest(all, 1)
        assertTrue(digest.length < 2_300)
        assertTrue(digest.contains("line 200"))
        assertTrue(!digest.contains("line 1 "))
    }
}

class UsagePurposeTest {
    @Test fun `purposes add up when days are combined`() {
        val a = UsageDay("d", inputTokens = 10, purposes = mapOf("chat" to 10L))
        val b = UsageDay("d", inputTokens = 5, purposes = mapOf("chat" to 5L, "title" to 3L))
        val sum = a + b
        assertEquals(15L, sum.purposes["chat"])
        assertEquals(3L, sum.purposes["title"])
    }
}
