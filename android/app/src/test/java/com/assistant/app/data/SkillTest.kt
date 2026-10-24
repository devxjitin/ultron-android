package com.assistant.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillTest {
    private val text = "---\nname: Write-Message\ndescription: \"Draft a message\"\n---\n# Steps\n1. Do it\n"

    @Test fun parsesFrontmatterAndBody() {
        val skill = SkillParser.parse(text)!!
        assertEquals("write-message", skill.name)
        assertEquals("Draft a message", skill.description)
        assertEquals("# Steps\n1. Do it", skill.body)
    }

    @Test fun usesFolderNameWhenNameMissing() {
        assertEquals("fallback", SkillParser.parse("---\ndescription: d\n---\nbody", "fallback")?.name)
    }

    @Test fun rejectsMissingDescriptionEmptyBodyAndBadName() {
        assertNull(SkillParser.parse("---\nname: a\n---\nbody"))
        assertNull(SkillParser.parse("---\nname: a\ndescription: d\n---\n"))
        assertNull(SkillParser.parse("---\nname: bad name!\ndescription: d\n---\nbody"))
    }

    @Test fun promptListsOnlyDescriptionsAndUseReturnsBody() {
        val skills = listOf(SkillParser.parse(text)!!)
        val prompt = SkillRegistry.promptSection(skills)
        assertTrue("write-message: Draft a message" in prompt)
        assertTrue("Do it" !in prompt)
        assertTrue("Do it" in SkillRegistry.use("write-message", skills))
        assertTrue(SkillRegistry.use("nope", skills).startsWith("status=not_found"))
        assertEquals("", SkillRegistry.promptSection(emptyList()))
        assertNotNull(skills)
    }
}
