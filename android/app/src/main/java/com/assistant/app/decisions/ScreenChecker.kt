package com.assistant.app.decisions

/** What the screen looked like after a step, as judged by Decisions. */
class ScreenVerdict(
    val succeeded: Boolean,
    /** One of [ScreenChecker.BLOCKERS]; "none" when nothing is in the way. */
    val blocker: String,
    /** One of [ScreenChecker.NEXT_STEPS]. */
    val next: String,
) {
    /** The line added to the tool result for the voice model to act on. */
    fun toResultLine(): String =
        "screen_check: step_succeeded=${if (succeeded) "yes" else "no"}; blocker=$blocker; suggested_next=${next.replace('_', ' ')}"
}

/**
 * After a tap or typing step, looks at the screen (a low-resolution picture) with one Decisions call and says whether the step worked, what blocks it, and what to do next. The voice model
 * stays in charge: this only adds a line to the tool result. Returns null when the call fails.
 */
class ScreenChecker(
    private val client: DecisionsClient,
    private val timeoutMs: Long = 3_000,
) {
    companion object {
        val BLOCKERS = mapOf(
            "none" to "Nothing is in the way",
            "permission" to "A permission prompt is showing",
            "login" to "A login or sign-in screen is showing",
            "error" to "An error message is showing",
            "ad" to "An advertisement or promotion covers the screen",
            "update" to "An update or rating prompt is showing",
        )
        val NEXT_STEPS = mapOf(
            "continue" to "The step worked; carry on with the next step",
            "retry" to "The step did not work; try it again or another way",
            "ask_user" to "The user has to do something, such as sign in or allow a permission",
            "stop" to "The task cannot continue",
        )
        /** Most consecutive failed steps before the advice becomes "stop and tell the user". */
        const val MAX_FAILURES = 3
    }

    private val set = questions {
        yesNo("step_succeeded", "Does the screen show that the step just worked?")
        choice("blocker", "What, if anything, is in the way of the task?", BLOCKERS)
        choice("next", "What should happen next?", NEXT_STEPS)
    }

    /** [step] says what was just done, [screen] is a picture or the text of the screen. */
    suspend fun check(step: String, goal: String, screen: DecisionInput): ScreenVerdict? {
        val context = DecisionInput.Text("Step just done: $step\nWhat the user asked for: ${goal.take(300)}")
        val outcome = client.decide("screen_check", listOf(screen, context), set, timeoutMs, useCache = false)
        val result = (outcome as? DecisionOutcome.Answered)?.result ?: return null
        val succeeded = result.yesNo("step_succeeded")?.isYes ?: return null
        val blocker = result.choice("blocker")?.choice?.takeIf { it in BLOCKERS } ?: "none"
        val next = result.choice("next")?.choice?.takeIf { it in NEXT_STEPS } ?: if (succeeded) "continue" else "retry"
        return ScreenVerdict(succeeded, blocker, next)
    }

    /** Applies the retry limit: after [MAX_FAILURES] failed steps in a row, advice becomes "stop". */
    fun limited(verdict: ScreenVerdict, consecutiveFailures: Int): ScreenVerdict =
        if (!verdict.succeeded && consecutiveFailures >= MAX_FAILURES) ScreenVerdict(false, verdict.blocker, "stop") else verdict
}
