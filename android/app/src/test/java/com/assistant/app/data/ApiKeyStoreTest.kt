package com.assistant.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ApiKeyStoreTest {
    private val failing = ApiKeyStore { throw IllegalStateException("Keystore unavailable") }

    @Test fun `unavailable secure storage reads as no key instead of crashing`() {
        assertNull(failing.getApiKey())
        assertNull(failing.getEmbeddingApiKey())
    }

    @Test fun `unavailable secure storage reports every write as failed`() {
        assertFalse(failing.setApiKey("key"))
        assertFalse(failing.setEmbeddingApiKey("key"))
        assertFalse(failing.clearApiKey())
        assertFalse(failing.clearEmbeddingApiKey())
    }
}
