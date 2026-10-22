package com.assistant.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryReplacementTest {
    private fun note(id: String, text: String) = MemoryItem(id, text, 0L)

    private val notes = listOf(
        note("home", "Lives at 12 Park Road in Delhi"),
        note("uncle", "His uncle also lives in Delhi"),
        note("tea", "Likes strong tea without sugar")
    )

    @Test fun `one clear match is the one that gets replaced`() {
        val choice = chooseReplacement(notes, "Likes strong tea without sugar")
        assertEquals("tea", choice.remove?.id)
        assertTrue(choice.ambiguous.isEmpty())
    }

    @Test fun `a short hint matching two notes equally deletes nothing`() {
        val choice = chooseReplacement(notes, "Delhi")
        assertNull(choice.remove)
        assertEquals(setOf("home", "uncle"), choice.ambiguous.map { it.id }.toSet())
    }

    @Test fun `a full old note beats a loose overlap`() {
        val choice = chooseReplacement(notes, "Lives at 12 Park Road in Delhi")
        assertEquals("home", choice.remove?.id)
    }

    @Test fun `a hint that matches nothing changes nothing`() {
        val choice = chooseReplacement(notes, "Plays cricket on weekends")
        assertNull(choice.remove)
        assertTrue(choice.ambiguous.isEmpty())
    }

    @Test fun `an empty or filler only hint changes nothing`() {
        assertNull(chooseReplacement(notes, "").remove)
        assertNull(chooseReplacement(notes, "the and with").remove)
    }

    @Test fun `hindi words are matched too`() {
        val hindi = listOf(note("a", "वह दिल्ली में रहता है"), note("b", "उसे चाय पसंद है"))
        assertEquals("b", chooseReplacement(hindi, "उसे चाय पसंद है").remove?.id)
    }

    @Test fun `forget matches only notes that contain every word`() {
        assertEquals(listOf("tea"), factsMatching(notes, "strong tea").map { it.id })
        assertEquals(setOf("home", "uncle"), factsMatching(notes, "Delhi").map { it.id }.toSet())
        assertTrue(factsMatching(notes, "").isEmpty())
    }
}
