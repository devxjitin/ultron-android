package com.assistant.app.data

import java.io.File
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
        assertNotNull(SkillParser.parse("---\nname: a\ndescription: d\n---\n", allowEmptyBody = true))
    }
}

class SkillRegistryTest {
    private val send = Skill("send_message", "Send a message", "SEND STEPS")
    private val contact = Skill("create_contact", "Save a contact", "CONTACT STEPS")
    private val whatsapp = SkillGroup("whatsapp", "WhatsApp tasks", "SHARED RULES", listOf(send, contact))
    private val notes = SkillGroup("notes", "Plain skill", "NOTE STEPS", emptyList())

    @Test fun promptListsOnlySkillNamesAndDescriptions() {
        val prompt = SkillRegistry.promptSection(listOf(whatsapp, notes))
        assertTrue("whatsapp: WhatsApp tasks" in prompt)
        assertTrue("send_message" !in prompt)
        assertTrue("SEND STEPS" !in prompt)
        assertEquals("", SkillRegistry.promptSection(emptyList()))
    }

    @Test fun openingASkillListsTasksWithoutTheirInstructions() {
        val opened = SkillRegistry.use("whatsapp", "", listOf(whatsapp))
        assertTrue(opened.startsWith("status=ok"))
        assertTrue("send_message: Send a message" in opened)
        assertTrue("SEND STEPS" !in opened && "CONTACT STEPS" !in opened)
    }

    @Test fun loadingATaskReturnsOnlyThatTask() {
        val loaded = SkillRegistry.use("whatsapp", "send_message", listOf(whatsapp, notes))
        assertTrue("SHARED RULES" in loaded && "SEND STEPS" in loaded)
        assertTrue("CONTACT STEPS" !in loaded && "NOTE STEPS" !in loaded)
    }

    @Test fun skillWithoutTasksReturnsItsBody() {
        assertTrue("NOTE STEPS" in SkillRegistry.use("notes", "", listOf(notes)))
        assertTrue(SkillRegistry.use("nope", "", listOf(notes)).startsWith("status=not_found"))
    }

    @Test fun taskThatDoesNotExistIsNotLoadable() {
        assertTrue(SkillRegistry.use("whatsapp", "create_contact", listOf(whatsapp.copy(tasks = listOf(send)))).startsWith("status=not_found"))
    }

    @Test fun onlySwitchedOnSkillsAreVisible() {
        val entries = listOf(SkillEntry(whatsapp, builtIn = true, enabled = false), SkillEntry(notes, builtIn = false, enabled = true))
        assertEquals(listOf("notes"), SkillRegistry.visible(entries).map { it.name })
    }

    @Test fun onlyTheFeaturesSkillIsLocked() {
        assertTrue(SkillEntry(SkillGroup("ultron-features", "d", "b", emptyList()), builtIn = true).locked)
        assertTrue(!SkillEntry(whatsapp, builtIn = true).locked)
        assertTrue(!SkillEntry(SkillGroup("ultron-features", "d", "b", emptyList()), builtIn = false).locked)
    }
}

class SkillDraftTest {
    @Test fun frontmatterFillsNameDescriptionAndInstruction() {
        val d = SkillParser.draftFromMarkdown("---\nname: Trip-Planner\ndescription: Plan a trip\n---\n# Steps\n1. Ask dates", "x.md")
        assertEquals(SkillDraft("trip-planner", "Plan a trip", "# Steps\n1. Ask dates"), d)
    }

    @Test fun plainMarkdownUsesFileNameAndFirstLine() {
        val d = SkillParser.draftFromMarkdown("# Write emails\nKeep them short.\n", "My Email Skill.md")
        assertEquals("my-email-skill", d.name)
        assertEquals("Write emails", d.description)
    }
}

/** Every skill shipped in assets must parse, or it silently disappears from the app. */
class BuiltInSkillAssetsTest {
    private val root = listOf("src/main/assets/skills", "app/src/main/assets/skills").map { File(it) }.first { it.isDirectory }

    @Test fun everySkillAndTaskParses() {
        root.listFiles()!!.filter { it.isDirectory }.forEach { dir ->
            val tasks = File(dir, "tasks").listFiles()?.filter { it.name.endsWith(".md") }.orEmpty()
            tasks.forEach { assertNotNull("bad task ${it.path}", SkillParser.parse(it.readText(), it.name.removeSuffix(".md"))) }
            assertNotNull("bad skill ${dir.name}", SkillParser.parse(File(dir, "SKILL.md").readText(), dir.name, allowEmptyBody = tasks.isNotEmpty()))
        }
    }

    @Test fun whatsappShipsOnlySendMessage() {
        val names = File(root, "whatsapp/tasks").listFiles()!!.map { it.name.removeSuffix(".md") }
        assertEquals(listOf("send_message"), names)
        val body = SkillParser.parse(File(root, "whatsapp/tasks/send_message.md").readText())!!.body
        assertTrue(body.length < MAX_SKILL_BODY_CHARS)
    }

    @Test fun skillsAreFiledUnderCategories() {
        val parsed = SkillParser.parse("---\nname: demo\ncategory: Messaging\ndescription: d\n---\nbody")!!
        assertEquals("Messaging", parsed.category)
        assertEquals("Messaging", SkillEntry(SkillGroup("demo", "d", "b", emptyList(), parsed.category), builtIn = true).category)
        assertEquals("Other", SkillEntry(SkillGroup("demo", "d", "b", emptyList()), builtIn = true).category)
        assertEquals(OWN_SKILLS_CATEGORY, SkillEntry(SkillGroup("demo", "d", "b", emptyList(), "Messaging"), builtIn = false).category)
    }
}
