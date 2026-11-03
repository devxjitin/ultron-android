package com.assistant.app.data

import com.assistant.app.network.GeminiEmbeddingClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class KnowledgeToolResponseTest {
    private fun http(code: Int) = GeminiEmbeddingClient.HttpFailure(code, "failed")

    @Test fun `http statuses map to config or service`() {
        assertEquals(SearchFailureCategory.CONFIG, classifySearchFailure(http(401)))
        assertEquals(SearchFailureCategory.CONFIG, classifySearchFailure(http(403)))
        assertEquals(SearchFailureCategory.CONFIG, classifySearchFailure(http(400)))
        assertEquals(SearchFailureCategory.SERVICE, classifySearchFailure(http(429)))
        assertEquals(SearchFailureCategory.SERVICE, classifySearchFailure(http(503)))
        assertEquals(SearchFailureCategory.UNKNOWN, classifySearchFailure(http(404)))
    }

    @Test fun `transport and state errors are classified`() {
        assertEquals(SearchFailureCategory.TIMEOUT, classifySearchFailure(SocketTimeoutException()))
        assertEquals(SearchFailureCategory.NETWORK, classifySearchFailure(UnknownHostException()))
        assertEquals(SearchFailureCategory.NETWORK, classifySearchFailure(IOException()))
        assertEquals(SearchFailureCategory.INDEX, classifySearchFailure(IllegalStateException()))
        assertEquals(SearchFailureCategory.UNKNOWN, classifySearchFailure(RuntimeException()))
    }

    @Test fun `pending embedding migration reports not ready without retrying`() {
        val category = classifySearchFailure(EmbeddingMigrationRequiredException())
        assertEquals(SearchFailureCategory.REINDEX_REQUIRED, category)
        assertFalse(category.transient)
        val text = KnowledgeToolResponse.failed(category)
        assertTrue(text.contains("reason=reindex_required"))
        assertTrue(text.contains("not ready yet"))
    }

    @Test fun `only transport and service failures are retried`() {
        assertTrue(SearchFailureCategory.NETWORK.transient)
        assertTrue(SearchFailureCategory.TIMEOUT.transient)
        assertTrue(SearchFailureCategory.SERVICE.transient)
        assertFalse(SearchFailureCategory.CONFIG.transient)
        assertFalse(SearchFailureCategory.INDEX.transient)
        assertFalse(SearchFailureCategory.UNKNOWN.transient)
    }

    @Test fun `results and empty results use distinct statuses`() {
        val ok = KnowledgeToolResponse.results(listOf("[Source: A]\none", "[Source: B]\ntwo"))
        assertTrue(ok.startsWith("status=results\n"))
        assertTrue(ok.contains("[Source: A]\none\n\n[Source: B]\ntwo"))
        val empty = KnowledgeToolResponse.results(emptyList())
        assertTrue(empty.startsWith("status=no_match"))
        assertTrue(empty.contains("not proof"))
    }

    @Test fun `empty query asks the user to repeat and never claims absence`() {
        val text = KnowledgeToolResponse.invalidQuery()
        assertTrue(text.startsWith("status=invalid_query"))
        assertTrue(text.contains("Do not say the information doesn't exist"))
    }

    @Test fun `failure responses never claim absence or point at reception`() {
        val texts = SearchFailureCategory.values().map { KnowledgeToolResponse.failed(it) } +
            KnowledgeToolResponse.notConfigured()
        texts.forEach {
            assertTrue(it, it.startsWith("status=search_failed; reason="))
            assertTrue(it, it.contains("Do not say the information doesn't exist") ||
                it.contains("do not say the information doesn't exist"))
            assertFalse(it, it.contains("reception", ignoreCase = true))
        }
        assertTrue(KnowledgeToolResponse.failed(SearchFailureCategory.TIMEOUT).contains("reason=timeout"))
        assertTrue(KnowledgeToolResponse.failed(SearchFailureCategory.CONFIG).contains("Developer settings"))
    }
}
