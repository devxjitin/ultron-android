package com.assistant.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsNewTest {
    private fun entry(v: String) = ChangelogEntry(v, "", listOf(ChangelogSection("Added", listOf("x"))))
    private val all = listOf("1.23.0", "1.22.1", "1.22.0", "1.21.0", "1.20.1", "1.20.0").map(::entry)

    @Test fun nothingWhenCurrentVersionAlreadySeen() {
        assertTrue(WhatsNew.entriesToShow(all, "1.23.0", "1.23.0").isEmpty())
    }

    @Test fun showsEveryVersionSinceLastSeenNewestFirst() {
        assertEquals(listOf("1.23.0", "1.22.1"), WhatsNew.entriesToShow(all, "1.22.0", "1.23.0").map { it.version })
    }

    @Test fun neverSeenShowsOnlyNewest() {
        assertEquals(listOf("1.23.0"), WhatsNew.entriesToShow(all, null, "1.23.0").map { it.version })
    }

    @Test fun longGapIsCapped() {
        assertEquals(WhatsNew.MAX_VERSIONS, WhatsNew.entriesToShow(all, "1.0.0", "1.23.0").size)
    }

    @Test fun versionsNewerThanTheRunningAppAreIgnored() {
        assertEquals(listOf("1.22.1"), WhatsNew.entriesToShow(all, "1.22.0", "1.22.1").map { it.version })
    }

    @Test fun comparesNumbersNotText() {
        assertTrue(WhatsNew.compare("1.9.0", "1.10.0") < 0)
    }
}
