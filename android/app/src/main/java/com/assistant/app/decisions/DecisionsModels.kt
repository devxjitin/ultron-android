package com.assistant.app.decisions

import org.json.JSONArray
import org.json.JSONObject

/** Limits of the Gemini Decisions API (v1beta, gemini-decision-1). */
object DecisionsLimits {
    const val MAX_QUESTIONS = 1_000
    const val MIN_OPTIONS = 2
    const val MAX_OPTIONS = 255
    const val MAX_KEY_LENGTH = 64
    val KEY_PATTERN = Regex("[A-Za-z0-9_-]+")
}

/** One question to ask about the input. */
sealed class Question(val instructions: String) {
    /** Yes/no; [trueDescription] and [falseDescription] sharpen what each answer means. */
    class YesNo(instructions: String, val trueDescription: String? = null, val falseDescription: String? = null) :
        Question(instructions)

    /** Pick one of [options] (id to description). */
    class Choice(instructions: String, val options: Map<String, String>) : Question(instructions)

    /** Rate on [levels], listed from lowest to highest. */
    class Score(instructions: String, val levels: List<String>) : Question(instructions)

    internal fun toJson(): JSONObject = when (this) {
        is YesNo -> JSONObject().put("type", "noul").put("instructions", instructions).apply {
            if (trueDescription != null || falseDescription != null) {
                put("criteria", JSONObject().apply {
                    trueDescription?.let { put("true_description", it) }
                    falseDescription?.let { put("false_description", it) }
                })
            }
        }
        is Choice -> JSONObject().put("type", "choice").put("instructions", instructions)
            .put("criteria", JSONObject().apply { options.forEach { (id, text) -> put(id, text) } })
        is Score -> JSONObject().put("type", "score").put("instructions", instructions)
            .put("criteria", JSONArray().apply { levels.forEach { put(it) } })
    }
}

/** What to look at. Images carry a [resolution] (low, medium, high) that sets their token cost. */
sealed class DecisionInput {
    class Text(val text: String) : DecisionInput()
    class Image(val bytes: ByteArray, val mimeType: String = "image/jpeg", val resolution: String = "low") : DecisionInput()

    internal fun toJson(): JSONObject = when (this) {
        is Text -> JSONObject().put("type", "text").put("text", text)
        is Image -> JSONObject().put("type", "image")
            .put("data", java.util.Base64.getEncoder().encodeToString(bytes))
            .put("mime_type", mimeType).put("resolution", resolution)
    }
}

sealed class Answer {
    abstract val confidence: Double

    /** [probability] is the chance the answer is yes. */
    class YesNo(val probability: Double, override val confidence: Double) : Answer() {
        val isYes: Boolean get() = probability >= 0.5
    }

    class Choice(val choice: String, val probabilities: Map<String, Double>, override val confidence: Double) : Answer()

    /** [score] is the probability-weighted average level index. */
    class Score(val score: Double, override val confidence: Double) : Answer()
}

class DecisionResult(val answers: Map<String, Answer>, val inputTokens: Int) {
    fun yesNo(key: String): Answer.YesNo? = answers[key] as? Answer.YesNo
    fun choice(key: String): Answer.Choice? = answers[key] as? Answer.Choice
    fun score(key: String): Answer.Score? = answers[key] as? Answer.Score
}

/** Builds the question map and checks the API limits before any request is sent. */
class QuestionSet private constructor(val questions: Map<String, Question>) {
    class Builder {
        private val map = LinkedHashMap<String, Question>()

        fun yesNo(key: String, instructions: String, trueDescription: String? = null, falseDescription: String? = null) =
            add(key, Question.YesNo(instructions, trueDescription, falseDescription))

        fun choice(key: String, instructions: String, options: Map<String, String>) =
            add(key, Question.Choice(instructions, options))

        fun score(key: String, instructions: String, levels: List<String>) =
            add(key, Question.Score(instructions, levels))

        private fun add(key: String, question: Question) = apply {
            require(key.length in 1..DecisionsLimits.MAX_KEY_LENGTH && DecisionsLimits.KEY_PATTERN.matches(key)) {
                "Question key must be 1-${DecisionsLimits.MAX_KEY_LENGTH} characters of A-Z a-z 0-9 _ -: $key"
            }
            require(key !in map) { "Duplicate question key: $key" }
            require(question.instructions.isNotBlank()) { "Question $key has no instructions" }
            val optionCount = when (question) {
                is Question.Choice -> question.options.size
                is Question.Score -> question.levels.size
                is Question.YesNo -> null
            }
            if (optionCount != null) {
                require(optionCount in DecisionsLimits.MIN_OPTIONS..DecisionsLimits.MAX_OPTIONS) {
                    "Question $key needs ${DecisionsLimits.MIN_OPTIONS}-${DecisionsLimits.MAX_OPTIONS} options, has $optionCount"
                }
            }
            map[key] = question
        }

        fun build(): QuestionSet {
            require(map.size in 1..DecisionsLimits.MAX_QUESTIONS) {
                "A request needs 1-${DecisionsLimits.MAX_QUESTIONS} questions, has ${map.size}"
            }
            return QuestionSet(map.toMap())
        }
    }

    internal fun toJson(): JSONObject = JSONObject().apply { questions.forEach { (key, q) -> put(key, q.toJson()) } }
}

fun questions(block: QuestionSet.Builder.() -> Unit): QuestionSet = QuestionSet.Builder().apply(block).build()
