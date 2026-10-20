package com.assistant.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentEmbeddingTextTest {
    @Test fun `embedding input contains source title heading and full body`() {
        assertEquals("Exams\nMCA > Semester 3\nDate: 12 October", documentEmbeddingText("Exams", "MCA > Semester 3", "Date: 12 October"))
        assertEquals("Body", documentEmbeddingText("", null, "Body"))
    }
}
