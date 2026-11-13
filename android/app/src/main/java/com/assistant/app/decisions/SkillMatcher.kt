package com.assistant.app.decisions

import com.assistant.app.data.SkillGroup

/**
 * When the voice model asks for a skill that does not exist, picks the one skill that fits the user's request (or none)
 * with a single Decisions call. It only suggests a name; the voice model still decides whether to use it. Runs on the
 * error path only, so a normal skill call costs nothing extra.
 */
class SkillMatcher(
    private val client: DecisionsClient,
    private val timeoutMs: Long = 2_500,
) {
    companion object {
        const val NONE = "none"
        const val MIN_CONFIDENCE = 0.7
    }

    /** The name of the best-fitting skill in [skills], or null when none fits or the call failed. */
    suspend fun suggest(request: String, skills: List<SkillGroup>): String? {
        if (request.isBlank() || skills.isEmpty()) return null
        val options = LinkedHashMap<String, String>()
        skills.forEach { options[it.name] = it.description.take(200) }
        options[NONE] = "None of the skills fits the request"
        val set = questions { choice("skill", "Which skill should be used for this request?", options) }
        val outcome = client.decide("skill_match", listOf(DecisionInput.Text(request.take(400))), set, timeoutMs)
        val answer = (outcome as? DecisionOutcome.Answered)?.result?.choice("skill") ?: return null
        return answer.choice.takeIf { it != NONE && it in options && answer.confidence >= MIN_CONFIDENCE }
    }
}
