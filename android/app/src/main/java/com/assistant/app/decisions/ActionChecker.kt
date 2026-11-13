package com.assistant.app.decisions

import org.json.JSONObject

/** What to do with a screen action after it was checked. */
sealed class ActionVerdict {
    /** Go ahead; also used whenever the check could not run, so the app behaves as it did before. */
    object Proceed : ActionVerdict()

    /** Go ahead, and tell the user what was done ([note] goes into the tool result). */
    class Warn(val note: String) : ActionVerdict()

    /** Do not act: the action does not look like something the user asked for. */
    class Block(val reason: String) : ActionVerdict()
}

/**
 * Checks one tap or typing step on another app with a single Decisions call, before it runs. It never asks the user
 * for anything: it only stops a step that clearly does not match what the user said (for example one triggered by text
 * on a web page or in a message), and adds a warning line to the result of a risky one.
 */
class ActionChecker(
    private val client: DecisionsClient,
    private val timeoutMs: Long = 2_000,
) {
    companion object {
        /** Block only when the model is fairly sure the step is unrelated to the request. */
        const val BLOCK_BELOW = 0.3
        const val BLOCK_MIN_CONFIDENCE = 0.7
        const val WARN_ABOVE = 0.6
        private const val FIELD_CHARS = 200
    }

    private val set = questions {
        yesNo("matches_user_request", "Is this step something the user asked for, or a normal part of doing what they asked?")
        yesNo("is_irreversible", "Is this step hard or impossible to undo?")
        yesNo("sends_to_other_person", "Does this step send something to another person?")
        yesNo("involves_money", "Does this step pay, transfer or spend money?")
        yesNo("deletes_data", "Does this step delete or erase data?")
    }

    /**
     * [app] is the app on screen, [action] a short verb phrase such as "tap" or "type", [target] the visible text that is
     * tapped or typed, and [request] what the user said in this turn.
     */
    suspend fun check(app: String, action: String, target: String, request: String): ActionVerdict {
        if (request.isBlank() || target.isBlank()) return ActionVerdict.Proceed
        val input = JSONObject()
            .put("app_on_screen", app)
            .put("step", action)
            .put("text_of_target", target.take(FIELD_CHARS))
            .put("user_request", request.take(400))
            .toString()
        val outcome = client.decide("action_check", listOf(DecisionInput.Text(input)), set, timeoutMs)
        val result = (outcome as? DecisionOutcome.Answered)?.result ?: return ActionVerdict.Proceed

        val match = result.yesNo("matches_user_request")
        if (match != null && match.probability < BLOCK_BELOW && match.confidence >= BLOCK_MIN_CONFIDENCE) {
            return ActionVerdict.Block(
                "That step does not look like something the user asked for ($action \"${target.take(60)}\" in $app). " +
                    "Do not do it. Tell the user what you were about to do and ask what they want."
            )
        }
        val risks = listOf(
            "is_irreversible" to "cannot be undone",
            "sends_to_other_person" to "sends something to another person",
            "involves_money" to "involves money",
            "deletes_data" to "deletes data",
        ).filter { (key, _) -> (result.yesNo(key)?.probability ?: 0.0) > WARN_ABOVE }
        if (risks.isEmpty()) return ActionVerdict.Proceed
        return ActionVerdict.Warn("This step ${risks.joinToString(" and ") { it.second }}. Say plainly what you just did.")
    }
}
