package com.assistant.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangelogTest {
    private val text = """
        # Changelog

        Intro text that is not a version.

        ## 1.2.0 - 2026-01-31

        ### Added
        - New thing
        - Another thing

        ### Fixed
        - A bug

        ## 1.1.0

        ### Changed
        - Something changed
        -

        ### Removed
    """.trimIndent()

    @Test fun `parses versions newest first with dates sections and points`() {
        val entries = Changelog.parse(text)
        assertEquals(listOf("1.2.0", "1.1.0"), entries.map { it.version })
        assertEquals("2026-01-31", entries[0].date)
        assertEquals("", entries[1].date)
        assertEquals(listOf("Added", "Fixed"), entries[0].sections.map { it.title })
        assertEquals(listOf("New thing", "Another thing"), entries[0].sections[0].items)
    }

    @Test fun `skips empty points and sections without points`() {
        val second = Changelog.parse(text)[1]
        assertEquals(listOf("Changed"), second.sections.map { it.title })
        assertEquals(listOf("Something changed"), second.sections[0].items)
    }

    @Test fun `text without a version heading gives no entries`() {
        assertTrue(Changelog.parse("# Changelog\n\n### Added\n- x").isEmpty())
    }
}
