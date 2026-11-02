package com.assistant.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PromptContentTest {
    private fun doc(vararg pairs: Pair<String, Any?>) = mapOf(*pairs)

    @Test
    fun publishedPromptIsAccepted() {
        val p = PromptContent.parse(doc("status" to "published", "persona" to "  You are Ultron.  ", "greeting" to "Hi"), 10)
        assertEquals("You are Ultron.", p?.persona)
        assertEquals("Hi", p?.greeting)
    }

    @Test
    fun draftOrMissingStatusIsRejected() {
        assertNull(PromptContent.parse(doc("status" to "draft", "persona" to "x"), 10))
        assertNull(PromptContent.parse(doc("persona" to "x"), 10))
    }

    @Test
    fun blankOrHugePersonaIsRejected() {
        assertNull(PromptContent.parse(doc("status" to "published", "persona" to "   "), 10))
        assertNull(PromptContent.parse(doc("status" to "published", "persona" to "a".repeat(20_001)), 10))
    }

    @Test
    fun promptForNewerAppIsRejected() {
        assertNull(PromptContent.parse(doc("status" to "published", "persona" to "x", "minAppVersion" to 11L), 10))
        assertNotNull(PromptContent.parse(doc("status" to "published", "persona" to "x", "minAppVersion" to 10L), 10))
    }

    @Test
    fun badGreetingIsDroppedButPersonaKept() {
        val p = PromptContent.parse(doc("status" to "published", "persona" to "x", "greeting" to " "), 10)
        assertNotNull(p)
        assertNull(p?.greeting)
    }

    @Test
    fun cacheRoundTrip() {
        val original = PromptContent("persona text", "hello")
        assertEquals(original, PromptContent.fromJson(PromptContent.toJson(original)))
        assertNull(PromptContent.fromJson("not json"))
    }
}
