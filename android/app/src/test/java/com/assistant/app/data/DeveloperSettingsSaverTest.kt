package com.assistant.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DeveloperSettingsSaverTest {
    private class FakeKeys(
        var api: String? = "old-api",
        var embedding: String? = "old-embedding",
        var failApiWrite: Boolean = false,
        var failEmbeddingWrite: Boolean = false,
        var failRestore: Boolean = false
    ) : ApiKeyStorage {
        val writes = mutableListOf<String>()
        override fun getApiKey() = api
        override fun getEmbeddingApiKey() = embedding
        override fun setApiKey(key: String): Boolean {
            writes += "api=$key"
            if (failApiWrite || (failRestore && key == "old-api")) return false
            api = key; return true
        }
        override fun clearApiKey(): Boolean { writes += "api=clear"; if (failRestore) return false; api = null; return true }
        override fun setEmbeddingApiKey(key: String): Boolean {
            writes += "embedding=$key"
            if ((failEmbeddingWrite && key == "new-embedding") || (failRestore && key == "old-embedding")) return false
            embedding = key; return true
        }
        override fun clearEmbeddingApiKey(): Boolean {
            writes += "embedding=clear"; if (failRestore) return false; embedding = null; return true
        }
    }

    private suspend fun failure(block: suspend () -> Unit): DeveloperSaveFailedException = try {
        block(); fail("expected DeveloperSaveFailedException"); error("unreachable")
    } catch (e: DeveloperSaveFailedException) { e }

    @Test fun `success writes only changed keys and then settings`() = runBlocking {
        val keys = FakeKeys()
        var settingsWritten = false
        saveDeveloperConfiguration(keys, "new-api", null) { settingsWritten = true }
        assertEquals(listOf("api=new-api"), keys.writes)
        assertEquals("new-api", keys.api)
        assertEquals("old-embedding", keys.embedding)
        assertTrue(settingsWritten)
    }

    @Test fun `settings failure restores both changed keys`() = runBlocking {
        val keys = FakeKeys()
        val e = failure {
            saveDeveloperConfiguration(keys, "new-api", "new-embedding") { throw java.io.IOException("disk") }
        }
        assertTrue(e.rollbackComplete)
        assertEquals("old-api", keys.api)
        assertEquals("old-embedding", keys.embedding)
    }

    @Test fun `key write failure rolls back keys already changed and skips settings`() = runBlocking {
        val keys = FakeKeys(failEmbeddingWrite = true)
        var settingsWritten = false
        val e = failure {
            saveDeveloperConfiguration(keys, "new-api", "new-embedding") { settingsWritten = true }
        }
        assertFalse(settingsWritten)
        assertEquals("old-api", keys.api)
        assertEquals("old-embedding", keys.embedding)
        assertTrue(e.rollbackComplete)
    }

    @Test fun `previously absent key is cleared on rollback`() = runBlocking {
        val keys = FakeKeys(api = null)
        failure { saveDeveloperConfiguration(keys, "new-api", null) { error("settings failed") } }
        assertNull(keys.api)
    }

    @Test fun `failed rollback is reported`() = runBlocking {
        val keys = FakeKeys(failRestore = true)
        val e = failure { saveDeveloperConfiguration(keys, "new-api", null) { error("settings failed") } }
        assertFalse(e.rollbackComplete)
    }

    @Test fun `no key changes means keys are never touched`() = runBlocking {
        val keys = FakeKeys()
        failure { saveDeveloperConfiguration(keys, null, null) { error("settings failed") } }
        assertTrue(keys.writes.isEmpty())
    }

    @Test fun `cancellation restores keys and is rethrown as cancellation`() = runBlocking {
        val keys = FakeKeys()
        try {
            saveDeveloperConfiguration(keys, "new-api", null) { throw CancellationException("cancelled") }
            fail("expected cancellation")
        } catch (e: CancellationException) {
            assertEquals("old-api", keys.api)
        }
    }
}
