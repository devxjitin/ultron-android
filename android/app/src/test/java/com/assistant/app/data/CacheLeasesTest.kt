package com.assistant.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheLeasesTest {
    @Test fun `a leased name is in use until the lease is closed`() {
        val lease = CacheLeases.acquire("yt-test-1")
        assertTrue(CacheLeases.isLeased("yt-test-1"))
        lease.close()
        assertFalse(CacheLeases.isLeased("yt-test-1"))
    }

    @Test fun `two leases on one name both have to end`() {
        val first = CacheLeases.acquire("preview-test-2")
        val second = CacheLeases.acquire("preview-test-2")
        first.close()
        assertTrue(CacheLeases.isLeased("preview-test-2"))
        second.close()
        assertFalse(CacheLeases.isLeased("preview-test-2"))
    }

    @Test fun `closing twice does not release someone else's lease`() {
        val first = CacheLeases.acquire("preview-test-3")
        val second = CacheLeases.acquire("preview-test-3")
        first.close()
        first.close()
        assertTrue(CacheLeases.isLeased("preview-test-3"))
        second.close()
    }
}
