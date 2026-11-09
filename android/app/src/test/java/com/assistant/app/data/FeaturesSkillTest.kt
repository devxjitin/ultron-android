package com.assistant.app.data

import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The features skill is what the assistant trusts about itself, so it must name every tool and fit the size limit. */
class FeaturesSkillTest {
    private val skillFile = listOf("src/main/assets/skills/ultron-features/SKILL.md", "app/src/main/assets/skills/ultron-features/SKILL.md")
        .map { File(it) }.first { it.isFile }

    @Test fun `parses and fits within the skill size limit`() {
        val text = skillFile.readText()
        assertNotNull(SkillParser.parse(text, "ultron-features"))
        assertTrue("skill body would be cut at $MAX_SKILL_BODY_CHARS characters", text.length < MAX_SKILL_BODY_CHARS)
    }

    @Test fun `names every device tool`() {
        val text = skillFile.readText()
        val missing = DeviceTool.entries.map { it.functionName }.filter { it !in text }
        assertTrue("tools missing from the features skill: $missing", missing.isEmpty())
    }

    @Test fun `lists every other built-in skill`() {
        val text = skillFile.readText()
        val skills = skillFile.parentFile.parentFile.listFiles()!!.filter { it.isDirectory && it.name != "ultron-features" }.map { it.name }
        val missing = skills.filter { it !in text }
        assertTrue("skills missing from the features skill: $missing", missing.isEmpty())
    }

    @Test fun `names every whatsapp task`() {
        val text = skillFile.readText()
        val tasks = File(skillFile.parentFile.parentFile, "whatsapp/tasks").listFiles()!!.map { it.name.removeSuffix(".md") }
        val missing = tasks.filter { it !in text }
        assertTrue("whatsapp tasks missing from the features skill: $missing", missing.isEmpty())
    }
}
