package com.assistant.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageSearchTest {
    private fun image(id: Long, title: String, description: String = "", at: Long = id) =
        StoredImage(id, title, description, "$id.jpg", 1000, at)

    private val library = listOf(
        image(1, "Mom's birthday", "Mom cutting a chocolate cake with the family. At home."),
        image(2, "Goa trip", "Beach at sunset near Baga"),
        image(3, "Passport", "My passport photo page")
    )

    private fun titles(query: String?) = ImageSearch.rank(library, query).map { it.title }

    @Test fun `request words like show and picture are ignored`() {
        assertEquals(listOf("Goa trip"), titles("show me the picture of goa"))
    }

    @Test fun `the description is searched too`() {
        assertEquals(listOf("Mom's birthday"), titles("chocolate cake"))
        assertEquals(listOf("Goa trip"), titles("sunset"))
    }

    @Test fun `a typo still finds the picture`() {
        assertEquals(listOf("Passport"), titles("pasport"))
    }

    @Test fun `a full stop inside the description does not hide later words`() {
        assertEquals(listOf("Mom's birthday"), titles("home"))
    }

    @Test fun `no words lists everything, newest first`() {
        assertEquals(listOf("Passport", "Goa trip", "Mom's birthday"), titles(""))
    }

    @Test fun `unrelated words find nothing`() {
        assertTrue(titles("helicopter").isEmpty())
    }

    @Test fun `scaled size keeps small pictures and shrinks big ones to the limit`() {
        assertEquals(800 to 600, ImageLibrary.scaledSize(800, 600))
        val (w, h) = ImageLibrary.scaledSize(4000, 3000)
        assertEquals(ImageLibrary.MAX_EDGE, w)
        assertEquals(1200, h)
    }
}
