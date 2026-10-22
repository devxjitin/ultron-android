package com.assistant.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class IncrementalMarkdownTest {
    private val sample = """
        # Title

        First paragraph
        continues here.

        - one
        - two

        ```kotlin
        val a = 1

        val b = 2
        ```

        | a | b |
        |---|---|
        | 1 | 2 |

        > quoted

        Last paragraph without an ending
    """.trimIndent()

    @Test fun `growing the text one character at a time always matches parsing it whole`() {
        val parser = IncrementalMarkdown()
        for (end in 1..sample.length) {
            val prefix = sample.substring(0, end)
            assertEquals("at $end", parseMarkdown(prefix), parser.update(prefix))
        }
    }

    @Test fun `a blank line inside a code block does not end the block early`() {
        val parser = IncrementalMarkdown()
        val text = "```\na\n\nb\n```\n\nafter"
        for (end in 1..text.length) assertEquals(parseMarkdown(text.substring(0, end)), parser.update(text.substring(0, end)))
    }

    @Test fun `text that stops being a continuation starts over`() {
        val parser = IncrementalMarkdown()
        parser.update("first\n\nsecond")
        assertEquals(parseMarkdown("other text"), parser.update("other text"))
    }

    @Test fun `blank or empty text gives no blocks`() {
        assertEquals(emptyList<MdBlock>(), IncrementalMarkdown().update(""))
        assertEquals(emptyList<MdBlock>(), IncrementalMarkdown().update("\n\n  \n"))
    }
}
