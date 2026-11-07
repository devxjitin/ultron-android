package com.assistant.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyPickTest {
    private fun c(id: String, app: String, chat: String, time: Long, vararg extra: String) =
        ReplyCandidate(id, app, chat, listOf(chat) + extra, time)

    private val chats = listOf(
        c("1", "WhatsApp", "Rahul", 10),
        c("2", "WhatsApp", "Rahul Sharma", 30),
        c("3", "WhatsApp", "Family", 20, "Mom"),
        c("4", "Telegram", "Rahul", 40)
    )

    @Test
    fun repliesToTheNamedChatInTheNamedApp() {
        val pick = pickReply(chats, "whatsapp", "family") as ReplyPick.One
        assertEquals("3", pick.candidate.id)
    }

    @Test
    fun exactNameWinsOverPartialMatches() {
        val pick = pickReply(chats, "WhatsApp", "Rahul") as ReplyPick.One
        assertEquals("1", pick.candidate.id)
    }

    @Test
    fun severalDifferentChatsAreReturnedInsteadOfGuessing() {
        val pick = pickReply(chats, "WhatsApp", "Rah") as ReplyPick.Many
        assertEquals(listOf("Rahul", "Rahul Sharma"), pick.chats)
    }

    @Test
    fun newestNotificationOfTheSameChatIsUsed() {
        val same = listOf(c("a", "WhatsApp", "Family", 5), c("b", "WhatsApp", "Family", 9))
        assertEquals("b", (pickReply(same, "WhatsApp", "Family") as ReplyPick.One).candidate.id)
    }

    @Test
    fun conversationTitleCountsAsAName() {
        val pick = pickReply(chats, "WhatsApp", "Mom") as ReplyPick.One
        assertEquals("3", pick.candidate.id)
    }

    @Test
    fun noMatchSendsNothing() {
        assertEquals(ReplyPick.None, pickReply(chats, "WhatsApp", "Priya"))
        assertEquals(ReplyPick.None, pickReply(chats, "Signal", "Rahul"))
    }

    @Test
    fun tooShortAppOrContactIsRefused() {
        assertEquals(ReplyPick.TooShort, pickReply(chats, "a", "Rahul"))
        assertEquals(ReplyPick.TooShort, pickReply(chats, "WhatsApp", " "))
        assertTrue(pickReply(chats, "Tel", "Rahul") is ReplyPick.One)
    }
}
