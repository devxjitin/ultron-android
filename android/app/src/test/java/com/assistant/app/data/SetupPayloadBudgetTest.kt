package com.assistant.app.data

import com.assistant.app.network.liveFunctionDeclarations
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Live setup message is re-sent on every cold connect, so growth in it is felt as a slower first reply. */
class SetupPayloadBudgetTest {
    @Test fun defaultToolDeclarationsStayWithinBudget() {
        val settings = AssistantSettings(enabledDeviceTools = effectiveDeviceTools(null))
        val chars = liveFunctionDeclarations(settings).toString().length
        assertTrue("tool declarations are $chars chars", chars < 25_000)
    }

    @Test fun systemInstructionStaysWithinBudget() {
        val chars = KnowledgeBaseProvider.buildSystemInstruction(DEFAULT_SYSTEM_PROMPT, "Hindi", "Ultron", ownerName = "Asha").length
        assertTrue("system instruction is $chars chars", chars < 9_000)
    }
}
