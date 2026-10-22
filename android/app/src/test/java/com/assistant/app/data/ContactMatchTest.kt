package com.assistant.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ContactMatchTest {

    @Test fun oneNumberSavedInSeveralFormsIsOneChoice() {
        val found = listOf("Mom" to "+91 98765 43210", "Mom" to "098765-43210", "Mom" to "9876543210")
        assertEquals(listOf("Mom" to "+919876543210"), ContactMatch.numbers("mom", found))
    }

    @Test fun anExactNameWinsOverNamesThatOnlyContainIt() {
        val found = listOf("Rahul" to "9876543210", "Rahul Office" to "02212345678")
        assertEquals(listOf("Rahul" to "9876543210"), ContactMatch.numbers("Rahul", found))
    }

    @Test fun differentPeopleStayAChoice() {
        val found = listOf("Rahul Sharma" to "9876543210", "Rahul Verma" to "9123456789")
        assertEquals(2, ContactMatch.numbers("Rahul", found).size)
    }

    @Test fun entriesWithoutDigitsAreIgnored() {
        assertEquals(emptyList<Pair<String, String>>(), ContactMatch.numbers("Bob", listOf("Bob" to "n/a")))
    }
}
