package com.assistant.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.assistant.app.network.GeminiTextClient

class RagDocumentChunkingTest {

    @Test
    fun `body paragraphs inherit their nested heading path`() {
        val chunks = chunkMarkdownText(
            """
            # Admissions

            ## Fees

            The annual fee is paid in two instalments.

            ## Timings

            Office hours are 9 AM to 4 PM.
            """.trimIndent()
        )

        assertEquals(
            listOf(
                "Admissions > Fees" to "The annual fee is paid in two instalments.",
                "Admissions > Timings" to "Office hours are 9 AM to 4 PM."
            ),
            chunks
        )
    }

    @Test
    fun `heading with following body in the same paragraph keeps that body`() {
        val chunks = chunkMarkdownText("## Contact\nCall reception at 555-0100.")

        assertEquals(listOf("Contact" to "Call reception at 555-0100."), chunks)
    }

    @Test
    fun `single inline dataset chunk preserves its title and complete body`() {
        assertEquals(
            listOf("MCA Semester 3" to "Date: 12 October | Course: Competitive Coding | Shift: C"),
            searchableDocumentChunks("# MCA Semester 3 | Date: 12 October | Course: Competitive Coding | Shift: C")
        )
    }

    @Test
    fun `consecutive inline chunks remain separate without blank lines`() {
        assertEquals(
            listOf("MCA" to "Exam on 12 October.", "BCA" to "Exam on 13 October."),
            searchableDocumentChunks("# MCA | Exam on 12 October.\r\n# BCA | Exam on 13 October.")
        )
    }

    @Test
    fun `inline dataset and legacy Markdown can coexist`() {
        assertEquals(
            listOf("Admissions > Fees" to "Annual fee details.", "MCA" to "Exam on 12 October."),
            searchableDocumentChunks("# Admissions\n## Fees\nAnnual fee details.\n# MCA | Exam on 12 October.")
        )
    }

    @Test
    fun `legacy plain text and tables preserve their content`() {
        val table = "| Course | Date |\n| --- | --- |\n| MCA | 12 October |"
        assertEquals(listOf(null to "Contact reception."), searchableDocumentChunks("Contact reception."))
        assertEquals(listOf("Schedule" to table), searchableDocumentChunks("# Schedule\n\n$table"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty document is rejected before embedding`() {
        searchableDocumentChunks(" \n\t")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `heading-only document is rejected before embedding`() {
        searchableDocumentChunks("# MCA\n## Semester 3")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `inline chunk with no body is rejected before embedding`() {
        searchableDocumentChunks("# MCA | ")
    }

    @Test
    fun `long paragraph is split at sentence boundaries under its topic`() {
        val sentence = "Each sentence carries a separate detail for semantic retrieval."
        val chunks = chunkMarkdownText("# Policies\n\n" + List(20) { sentence }.joinToString(" "))

        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.first == "Policies" && it.second.length <= 900 })
        assertEquals(20, chunks.sumOf { it.second.split("semantic retrieval.").size - 1 })
    }

    @Test
    fun `long MCA table preserves every complete row and repeats context and headers`() {
        val header = "| Date | Course | Shift |"
        val delimiter = "| --- | --- | --- |"
        val context = "Programme: MCA; Semester: 3; Examination: October 2026"
        val rows = (1..25).map {
            "| $it October 2026 | APPLIED RESEARCH METHODOLOGIES IN COMPUTER SCIENCE $it | C |"
        }
        val chunks = chunkMarkdownText(
            "# MCA\n## Semester 3\n$context\n\n" + (listOf(header, delimiter) + rows).joinToString("\n")
        )
        val tableChunks = chunks.filter { it.second.contains(header) }

        assertTrue(tableChunks.size > 1)
        assertTrue(tableChunks.all {
            it.first == "MCA > Semester 3" &&
                it.second.startsWith("$context\n$header\n$delimiter\n") && it.second.length <= 900
        })
        assertEquals(rows, tableChunks.flatMap { it.second.lines().drop(3) })
    }

    @Test
    fun `oversized table row stays intact and continuation retains the header`() {
        val header = "| Course | Date |"
        val delimiter = "| --- | --- |"
        val oversized = "| " + "APPLIED RESEARCH METHODOLOGIES ".repeat(40) + "| 12 October |"
        val nextRow = "| Competitive Coding | 13 October |"
        val chunks = chunkMarkdownText("# Schedule\n$header\n$delimiter\n$oversized\n$nextRow")

        assertEquals(listOf(
            "Schedule" to "$header\n$delimiter\n$oversized",
            "Schedule" to "$header\n$delimiter\n$nextRow"
        ), chunks)
    }

    @Test
    fun `inline structured record is indivisible even beyond the size target`() {
        val record = "Programme: MCA | Semester: 3 | Course: " + "Research ".repeat(130) + "| Date: 12 October"
        assertEquals(listOf("MCA Semester 3" to record), chunkMarkdownText("# MCA Semester 3 | $record"))
    }

    @Test
    fun `structured lines preserve source records once without sentence splitting`() {
        val rows = (1..15).map { ("Date: $it October | Course: " + "Research methods. ".repeat(8)).trim() }
        val chunks = chunkMarkdownText("# MCA\n" + rows.joinToString("\n"))
        assertTrue(chunks.size > 1)
        assertEquals(rows, chunks.flatMap { it.second.lines() })
    }

    @Test
    fun `table context does not leak across sections`() {
        val table = "| Course | Date |\n| --- | --- |\n| BCA | 13 October |"
        val chunks = chunkMarkdownText("# MCA\nSemester: 3\n\n# BCA\n$table")
        assertEquals("BCA" to table, chunks.last())
    }

    @Test
    fun `dataset preparation prompt keeps topic facts in self-contained chunks`() {
        assertEquals(5, DEFAULT_RETRIEVAL_CHUNK_LIMIT)
        assertEquals("gemini-3.8-flash", GeminiTextClient.DOCUMENT_FORMAT_MODEL)
        assertTrue(DEFAULT_DOCUMENT_FORMAT_PROMPT.contains("`#` only as an inline chunk-start marker"))
        assertTrue(DEFAULT_DOCUMENT_FORMAT_PROMPT.contains("` | ` separates the title from the content"))
    }

    @Test
    fun `well-formed dataset output is accepted and code fences are stripped`() {
        val dataset = "# Fees | Annual fee is 1000.\n# Timings | Open 9 to 5."
        assertEquals(dataset, validateFormattedDataset(dataset))
        assertEquals(dataset, validateFormattedDataset("```markdown\n$dataset\n```"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `dataset output with a preface is rejected`() {
        validateFormattedDataset("Here is the dataset:\n# Fees | Annual fee is 1000.")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `dataset output without title and content separator is rejected`() {
        validateFormattedDataset("# Fees\nAnnual fee is 1000.")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank dataset output is rejected`() {
        validateFormattedDataset("  \n ")
    }
}
