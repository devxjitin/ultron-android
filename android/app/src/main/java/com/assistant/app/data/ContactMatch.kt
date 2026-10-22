package com.assistant.app.data

/** Picks the number to call or text from the contacts that matched a spoken or typed name. */
object ContactMatch {
    /**
     * The distinct numbers among [found] (name and number pairs), each with its contact name. A contact named exactly
     * [name] wins over ones that only contain it ("Mom" over "Mom office"), and one number saved twice in different forms
     * ("+91 98765 43210", "098765-43210") counts once, so it is not mistaken for a choice the user has to make.
     */
    fun numbers(name: String, found: List<Pair<String, String>>): List<Pair<String, String>> {
        val exact = found.filter { it.first.trim().equals(name.trim(), ignoreCase = true) }
        return exact.ifEmpty { found }
            .map { it.first to it.second.filter { c -> c.isDigit() || c == '+' } }
            .filter { it.second.any(Char::isDigit) }
            .groupBy { sameNumberKey(it.second) }
            .map { (_, same) -> same.firstOrNull { it.second.startsWith("+") } ?: same.first() }
    }

    /** The last ten digits identify a number whether it was saved with a country code, a leading 0 or neither. */
    internal fun sameNumberKey(number: String): String = number.filter { it.isDigit() }.takeLast(10)
}
