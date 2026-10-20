package com.assistant.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfSizeLimitTest {
    private val limit = 10 * 1024 * 1024

    @Test fun `declared size within the limit is accepted`() {
        assertTrue(isPdfWithinLimit(5L * 1024 * 1024))
        assertTrue(isPdfWithinLimit(limit.toLong()))
    }

    @Test fun `declared size over the limit is rejected`() {
        assertFalse(isPdfWithinLimit(limit + 1L))
        assertFalse(isPdfWithinLimit(50L * 1024 * 1024))
    }

    @Test fun `unknown size defers to the streaming check`() {
        assertTrue(isPdfWithinLimit(null))
    }
}
