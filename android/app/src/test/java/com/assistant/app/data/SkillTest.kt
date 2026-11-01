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

    @Test fun disallowedTaskIsNeitherListedNorLoadable() {
        val visible = SkillRegistry.visible(listOf(SkillEntry(whatsapp, enabled = true, disabledTasks = setOf("send_message"))))
        assertEquals(listOf("create_contact"), visible.single().tasks.map { it.name })
        assertTrue("send_message" !in SkillRegistry.use("whatsapp", "", visible))
        assertTrue(SkillRegistry.use("whatsapp", "send_message", visible).startsWith("status=not_found"))
    }

    @Test fun switchedOffSkillOrOneWithEveryTaskOffIsHidden() {
        assertTrue(SkillRegistry.visible(listOf(SkillEntry(whatsapp, enabled = false, disabledTasks = emptySet()))).isEmpty())
        val allOff = SkillEntry(whatsapp, enabled = true, disabledTasks = setOf("send_message", "create_contact"))
        assertTrue(SkillRegistry.visible(listOf(allOff)).isEmpty())
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

    @Test fun whatsappShipsItsTasks() {
        val names = File(root, "whatsapp/tasks").listFiles()!!.map { it.name.removeSuffix(".md") }.toSet()
        assertTrue(names.containsAll(setOf("send_message", "create_contact")))
    }
}
