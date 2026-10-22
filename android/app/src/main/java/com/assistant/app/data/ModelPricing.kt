package com.assistant.app.data

/** USD per one million tokens. [estimated] marks a rate copied from the nearest published tier of the same family. */
data class ModelRate(val inputUsd: Double, val outputUsd: Double, val estimated: Boolean = false)

/** Rough rupee conversion for the Usage page; adjust when the exchange rate moves. */
const val USD_TO_INR = 97.0

/**
 * What each model costs. Rates are list prices from public trackers, not read from a bill; models missing
 * here show no cost on the Usage page rather than a made-up one.
 */
private val MODEL_RATES = mapOf(
    "gpt-5.4-mini" to ModelRate(0.75, 4.50),
    "gemini-3.5-flash-lite" to ModelRate(0.30, 2.50),
    // Introductory rate; Google lists $1.50 / $7.50 from 2027-01-01.
    "gemini-3.8-flash" to ModelRate(0.75, 3.75),
    "gpt-6-luna" to ModelRate(0.10, 0.50)
)

fun modelRate(model: String): ModelRate? = MODEL_RATES[model]

/** Cost in rupees of [input] and [output] tokens on [model], or null when its price is not known. */
fun modelCostInr(model: String, input: Long, output: Long): Double? {
    val rate = modelRate(model) ?: return null
    return (input * rate.inputUsd + output * rate.outputUsd) / 1_000_000.0 * USD_TO_INR
}
