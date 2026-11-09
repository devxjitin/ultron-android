package com.assistant.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactMatcherTest {
    private fun contact(id: Long, name: String, number: String = "98000000$id", starred: Boolean = false, type: Int = 2) =
        ContactEntry(id, name, number, starred, type)

    private val book = listOf(
        contact(1, "Rahul Sharma"),
        contact(2, "Mom"),
        contact(3, "Papa"),
        contact(4, "Amit Bhai"),
        contact(5, "Sonu"),
        contact(6, "Priya Verma"),
        contact(7, "Priya Singh"),
        contact(8, "राहुल Office")
    )

    private fun oneName(spoken: String, entries: List<ContactEntry> = book): String? =
        (ContactMatcher.match(spoken, entries) as? ContactMatch.One)?.name

    @Test fun exactNameMatches() = assertEquals("Sonu", oneName("Sonu"))

    @Test fun spellingVariantsMatch() {
        assertEquals("Sonu", oneName("Sonuu"))
        assertEquals("Sonu", oneName("Sunu"))
        assertEquals("Amit Bhai", oneName("Ameet"))
    }

    @Test fun familyWordsAreInterchangeable() {
        assertEquals("Mom", oneName("Mummy"))
        assertEquals("Mom", oneName("Maa"))
        assertEquals("Papa", oneName("Dad"))
    }

    @Test fun relationAndPoliteWordsDoNotBlockAMatch() {
        assertEquals("Amit Bhai", oneName("Amit"))
        assertEquals("Amit Bhai", oneName("Amit ji"))
        assertEquals("Sonu", oneName("Sonu bhai"))
    }

    @Test fun devanagariMatchesRomanNames() {
        assertEquals("Rahul Sharma", oneName("Rahul Sharma"))
        assertEquals("Mom", oneName("मम्मी"))
        assertEquals("Sonu", oneName("सोनू"))
    }

    @Test fun savedDevanagariNamesMatchRomanSpeech() {
        val entries = listOf(contact(1, "राहुल"))
        assertEquals("राहुल", oneName("Raahul", entries))
    }

    @Test fun sameFirstNameAsksWhichOne() {
        val result = ContactMatcher.match("Priya", book)
        assertTrue(result is ContactMatch.Many)
        assertEquals(setOf("Priya Verma", "Priya Singh"), (result as ContactMatch.Many).names.toSet())
    }

    @Test fun fullNameResolvesTheAmbiguity() = assertEquals("Priya Singh", oneName("Priya Singh"))

    @Test fun aContactNamedExactlyBeatsLongerNames() {
        val entries = listOf(contact(1, "Amit"), contact(2, "Amit Sharma"))
        assertEquals("Amit", oneName("Amit", entries))
    }

    @Test fun unrelatedNameFindsNothing() {
        assertEquals(ContactMatch.None, ContactMatcher.match("Zebra", book))
        assertEquals(ContactMatch.None, ContactMatcher.match("   ", book))
    }

    @Test fun mobileNumberComesFirst() {
        val entries = listOf(contact(1, "Rahul", "0120111", type = 1), contact(1, "Rahul", "9800000001", type = 2))
        val match = ContactMatcher.match("Rahul", entries) as ContactMatch.One
        assertEquals("9800000001", match.numbers.first().number)
    }

    @Test fun duplicateNumbersAreListedOnce() {
        val entries = listOf(contact(1, "Rahul", "+91 98000 00001"), contact(1, "Rahul", "9800000001"))
        assertEquals(1, (ContactMatcher.match("Rahul", entries) as ContactMatch.One).numbers.size)
    }

    @Test fun transliterationKeepsWordEndsClean() {
        assertEquals("raahul", ContactMatcher.transliterate("राहुल"))
    }
}
