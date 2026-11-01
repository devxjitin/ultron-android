package com.assistant.app.data

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** The features guide is what the assistant trusts about itself, so it must name every tool. */
class FeaturesSkillTest {
    private val skillFile = listOf("src/main/assets/skills/ultron-features/SKILL.md", "app/src/main/assets/skills/ultron-features/SKILL.md")
        .map { File(it) }.first { it.isFile }

    @Test fun `names every device tool`() {
        val text = skillFile.readText()
        val missing = DeviceTool.entries.map { it.functionName }.filter { it !in text }
        assertTrue("tools missing from the features skill: $missing", missing.isEmpty())
    }

    @Test fun `frontmatter is stripped from the guide`() {
        val guide = SkillGuide.stripFrontmatter(skillFile.readText())
        assertTrue(guide.startsWith("# What Ultron can do"))
    }
}
