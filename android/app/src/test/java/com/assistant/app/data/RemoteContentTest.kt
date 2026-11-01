package com.assistant.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteContentTest {
    private val prompts = mapOf<String, Any?>("persona" to "  New persona  ", "greeting" to "Hello there", "policy" to "Call use_skill first.")

    @Test fun readsPromptFieldsAndTrimsThem() {
        val s = RemoteSnapshot.from(prompts, emptyMap(), appVersion = 10)
        assertEquals("New persona", s.persona)
        assertEquals("Hello there", s.greeting)
        assertEquals("Call use_skill first.", s.policy)
        assertNull(s.documentFormatPrompt)
    }

    @Test fun blankTooLongAndNewerBuildFieldsFallBackToBundled() {
        val tooLong = mapOf<String, Any?>("persona" to "x".repeat(20_001), "greeting" to "g".repeat(MAX_GREETING_LENGTH + 1), "documentFormatPrompt" to "  ")
        val s = RemoteSnapshot.from(tooLong, emptyMap(), 10)
        assertNull(s.persona); assertNull(s.greeting); assertNull(s.documentFormatPrompt)
        assertEquals(RemoteSnapshot(), RemoteSnapshot.from(prompts + ("minAppVersion" to 11), emptyMap(), 10))
        assertEquals("New persona", RemoteSnapshot.from(prompts + ("minAppVersion" to 10L), emptyMap(), 10).persona)
    }

    @Test fun policyWithoutUseSkillIsIgnored() {
        assertNull(RemoteSnapshot.from(mapOf("policy" to "Be brief."), emptyMap(), 10).policy)
    }

    @Test fun readsSkillsWithTasks() {
        val doc = mapOf<String, Any?>(
            "description" to "Operate WhatsApp", "body" to "Shared rules",
            "tasks" to mapOf("send_message" to mapOf("description" to "Send a text", "body" to "STEPS"), "off" to mapOf("description" to "d", "body" to "b", "published" to false))
        )
        val skill = RemoteSnapshot.from(null, mapOf("whatsapp" to doc), 10).skills.single()
        assertEquals("whatsapp", skill.name)
        assertEquals("Shared rules", skill.body)
        assertEquals(listOf("send_message"), skill.tasks.map { it.name })
        assertEquals("STEPS", skill.tasks.single().body)
    }

    @Test fun badSkillsAreDroppedAndUnpublishedOnesAreHidden() {
        val docs = mapOf<String, Any?>("description" to "d", "body" to "b").let {
            mapOf(
                "Bad Name!" to it, "no-desc" to mapOf("body" to "b"), "empty" to mapOf("description" to "d", "body" to " "),
                "future" to it + ("minAppVersion" to 99), "gone" to it + ("published" to false), "ok" to it
            )
        }
        val s = RemoteSnapshot.from(null, docs, 10)
        assertEquals(listOf("ok"), s.skills.map { it.name })
        assertEquals(setOf("gone"), s.hiddenSkills)
    }

    @Test fun remoteSkillReplacesBundledAndLockedSkillIsNeverHidden() {
        val bundled = linkedMapOf(
            "whatsapp" to SkillGroup("whatsapp", "old", "old body", emptyList()),
            "ultron-features" to SkillGroup("ultron-features", "d", "b", emptyList()),
            "other" to SkillGroup("other", "d", "b", emptyList())
        )
        val remote = RemoteSnapshot(
            skills = listOf(SkillGroup("whatsapp", "new", "new body", emptyList()), SkillGroup("fresh", "d", "b", emptyList())),
            hiddenSkills = setOf("other", "ultron-features")
        )
        SkillRegistry.mergeRemote(bundled, remote)
        assertEquals("new", bundled["whatsapp"]?.description)
        assertEquals(setOf("whatsapp", "ultron-features", "fresh"), bundled.keys)
    }
}

class RemoteSettingsTest {
    @Test fun readsAssistantNameAndRetrievalLimit() {
        val s = RemoteSnapshot.from(mapOf("assistantName" to " Ultron ", "retrievalChunkLimit" to 8L), emptyMap(), 10)
        assertEquals("Ultron", s.assistantName)
        assertEquals(8, s.retrievalChunkLimit)
    }

    @Test fun outOfRangeOrTooLongSettingsFallBackToDefaults() {
        val s = RemoteSnapshot.from(
            mapOf("assistantName" to "n".repeat(MAX_ASSISTANT_NAME_LENGTH + 1), "retrievalChunkLimit" to (MAX_RETRIEVAL_CHUNK_LIMIT + 1)), emptyMap(), 10
        )
        assertNull(s.assistantName)
        assertNull(s.retrievalChunkLimit)
        assertNull(RemoteSnapshot.from(mapOf("retrievalChunkLimit" to 0), emptyMap(), 10).retrievalChunkLimit)
    }

    @Test fun bundledPolicyIsUsedUntilThePanelSendsOne() {
        assertTrue(RemoteContent.policy().contains("Mandatory tool and answer policy"))
    }
}
