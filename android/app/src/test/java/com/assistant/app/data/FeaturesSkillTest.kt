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

    @Test fun `names no tool that the code does not have`() {
        val known = DeviceTool.entries.map { it.functionName }.toSet() + setOf("save_memory", "forget_memory", "use_skill", "search_knowledge_base")
        val mentioned = Regex("\\b[a-z]+(?:_[a-z]+)+\\b").findAll(skillFile.readText()).map { it.value }.toSet()
        val stale = mentioned.filter { it !in known }
        assertTrue("tools in the features skill that no longer exist: $stale", stale.isEmpty())
    }

    @Test fun `names every device tool`() {
        val text = skillFile.readText()
        val missing = DeviceTool.entries.map { it.functionName }.filter { it !in text }
        assertTrue("tools missing from the features skill: $missing", missing.isEmpty())
    }
}
