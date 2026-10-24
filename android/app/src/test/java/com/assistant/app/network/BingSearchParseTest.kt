package com.assistant.app.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Result text goes through android.text.Html, which plain unit tests cannot run, so only the pure parts are covered.
class BingSearchParseTest {
    @Test
    fun ignoresPageWithoutResults() {
        assertEquals(emptyList<SearchHit>(), DuckDuckGoClient.parseBing("<html>nothing</html>"))
    }

    @Test
    fun plainAddressPassesThroughAndOtherSchemesAreDropped() {
        assertEquals("https://openai.com/news/", DuckDuckGoClient.bingTarget("https://openai.com/news/"))
        assertNull(DuckDuckGoClient.bingTarget("javascript:alert(1)"))
    }
}
