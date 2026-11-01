package com.assistant.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillPolicyTest {
    private fun settings(vararg disallowed: String) = AssistantSettings(
        enabledDeviceTools = effectiveDeviceTools(setOf("send_sms=1"), disallowed.toSet()),
        disallowedSkillActions = disallowed.toSet()
    )

    @Test fun everyDeviceToolBelongsToExactlyOneSkill() {
        val names = BuiltInSkills.phone.flatMap { it.actions }.map { it.name }
        assertEquals(DeviceTool.entries.size, names.size)
        assertEquals(names.size, names.toSet().size)
        assertEquals(BuiltInSkills.phone.size, BuiltInSkills.phone.map { it.id }.toSet().size)
    }

    @Test fun skillsShowOnlyAllowedActions() {
        val skills = SkillPolicy.active(settings("send_sms"))
        val messages = skills.single { it.id == "messages" }
        assertEquals(listOf("send_sms_to_contact", "read_sms"), messages.actions.map { it.name })
        val listed = SkillPolicy.open(settings("send_sms"), "messages").lines().filter { it.startsWith("- ") }.map { it.removePrefix("- ").substringBefore(":") }
        assertEquals(listOf("send_sms_to_contact", "read_sms"), listed)
    }

    @Test fun skillWithNoAllowedActionIsHidden() {
        assertTrue(SkillPolicy.active(AssistantSettings(enabledDeviceTools = emptySet())).none { it.id == "messages" })
    }

    @Test fun disallowedActionCannotRun() {
        val s = settings("send_sms")
        val refused = SkillPolicy.resolve(s, setOf("messages"), "messages", "send_sms")
        assertTrue(refused is SkillResolution.Refused)
        val allowed = (refused as SkillResolution.Refused).message.substringAfter("Allowed:").trim().trimEnd('.').split(", ")
        assertFalse("send_sms" in allowed)
    }

    @Test fun actionNeedsItsSkillOpenedFirst() {
        val s = settings()
        assertTrue(SkillPolicy.resolve(s, emptySet(), "messages", "send_sms") is SkillResolution.Refused)
        assertEquals(SkillResolution.Device(DeviceTool.SEND_SMS), SkillPolicy.resolve(s, setOf("messages"), "messages", "send_sms"))
    }

    @Test fun otherSkillsAreNotReachableThroughAnOpenedOne() {
        val s = AssistantSettings(enabledDeviceTools = effectiveDeviceTools(setOf("send_sms=1", "dial_number=1")))
        assertTrue(SkillPolicy.resolve(s, setOf("messages"), "messages", "dial_number") is SkillResolution.Refused)
        assertTrue(SkillPolicy.resolve(s, setOf("messages"), "calls", "dial_number") is SkillResolution.Refused)
    }

    @Test fun promptListsSkillsButNotActions() {
        val prompt = SkillPolicy.promptSection(SkillPolicy.active(settings()))
        assertTrue("messages:" in prompt)
        assertTrue("send_sms" !in prompt)
        assertEquals("", SkillPolicy.promptSection(emptyList()))
    }

    @Test fun argumentsAreParsedFromJsonText() {
        assertEquals("x", SkillPolicy.parseArguments("{\"a\":\"x\"}")!!.getString("a"))
        assertEquals(0, SkillPolicy.parseArguments("")!!.length())
        assertEquals(null, SkillPolicy.parseArguments("not json"))
    }
}
