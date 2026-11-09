package com.assistant.app.data

import java.text.Normalizer

/** One saved phone number of a contact. [type] is the Android phone type (2 = mobile). */
data class ContactEntry(val id: Long, val name: String, val number: String, val starred: Boolean = false, val type: Int = 0)

/** What the matcher decided for a spoken name. */
sealed interface ContactMatch {
    data object None : ContactMatch
    /** One contact clearly fits; [numbers] are all of its saved numbers, the best one for calling first. */
    data class One(val name: String, val numbers: List<ContactEntry>) : ContactMatch
    /** Several contacts fit about equally well; the caller must ask which one. */
    data class Many(val names: List<String>) : ContactMatch
}

/**
 * Finds a contact from a name as it was heard, not as it was saved. Voice gives "Raahul" for "Rahul", "Mummy" for "Mom",
 * "Amit bhai" for "Amit" or Devanagari for a name saved in English, and Android's own filter only matches the start of
 * the saved name exactly. This one tolerates spelling variants, family words, honorifics and small typos.
 */
object ContactMatcher {
    private const val MIN_SCORE = 55
    private const val AMBIGUOUS_GAP = 8

    /** Never a name on their own: dropped from the spoken name always, and from saved names when comparing. */
    private val POLITE = setOf("ji", "sir", "madam", "mam", "maam", "saab", "sahab", "sahib", "mr", "mrs", "ms", "shri", "shree", "smt")

    /** Relation words people add to a name; dropped from the spoken name only when another word is left. */
    private val RELATION = setOf("bhai", "bhaiya", "bhaiyya", "bhaiji", "didi", "di", "bhabhi", "bhabi", "uncle", "aunty", "auntie", "dada", "dost")

    /** Words that mean the same saved contact. Everything maps to the first word of its group. */
    private val FAMILY = listOf(
        listOf("mummy", "mom", "mother", "maa", "mumma", "mommy", "mataji", "ammi", "amma"),
        listOf("papa", "dad", "daddy", "father", "pappa", "pitaji", "abbu", "baap")
    ).flatMap { group -> group.map { it to group.first() } }.toMap()

    fun match(spoken: String, entries: List<ContactEntry>): ContactMatch {
        val query = queryTokens(spoken)
        if (query.isEmpty()) return ContactMatch.None
        val byContact = entries.groupBy { it.id to it.name }
        val scored = byContact.mapNotNull { (key, rows) ->
            val score = score(query, key.second, rows.any { it.starred })
            if (score >= MIN_SCORE) Triple(key.second, score, rows) else null
        }.sortedByDescending { it.second }
        if (scored.isEmpty()) return ContactMatch.None
        val top = scored.first()
        val close = scored.filter { it.second >= top.second - AMBIGUOUS_GAP }
        if (close.size > 1 && close.map { it.first.lowercase() }.toSet().size > 1) {
            return ContactMatch.Many(close.take(5).map { it.first })
        }
        return ContactMatch.One(top.first, top.third.distinctBy { digits(it.number) }.sortedWith(compareBy({ it.type != 2 }, { it.number })))
    }

    /** All contacts that fit, best first, as "name: number" rows for search_contact. */
    fun search(spoken: String, entries: List<ContactEntry>, limit: Int): List<ContactEntry> {
        val query = queryTokens(spoken)
        if (query.isEmpty()) return emptyList()
        val ranked = entries.groupBy { it.id to it.name }.mapNotNull { (key, rows) ->
            val score = score(query, key.second, rows.any { it.starred })
            if (score >= MIN_SCORE) score to rows else null
        }.sortedByDescending { it.first }
        return ranked.flatMap { (_, rows) -> rows.distinctBy { digits(it.number) } }.take(limit)
    }

    internal fun digits(number: String) = number.filter { it.isDigit() }.takeLast(10)

    // ---- scoring -----------------------------------------------------------------------------------------------

    private fun queryTokens(spoken: String): List<String> {
        val all = tokens(spoken).filter { it !in POLITE }
        val withoutRelation = all.filter { it !in RELATION }
        return (if (withoutRelation.isEmpty()) all else withoutRelation).map { canonical(it) }
    }

    private fun score(query: List<String>, savedName: String, starred: Boolean): Int {
        val saved = tokens(savedName).filter { it !in POLITE }.map { canonical(it) }
        if (saved.isEmpty()) return 0
        var total = 0
        for (word in query) {
            val best = saved.maxOf { wordScore(word, it) }
            if (best == 0) return 0
            total += best
        }
        var result = total / query.size
        // A saved name with exactly the spoken words ("Amit") beats a longer one ("Amit Sharma").
        if (saved.size == query.size) result += 12
        if (starred) result += 3
        return result
    }

    private fun wordScore(q: String, c: String): Int {
        if (q == c) return 100
        val qk = phonetic(q)
        val ck = phonetic(c)
        if (qk == ck) return 90
        if (qk.length >= 2 && ck.startsWith(qk)) return 75
        if (qk.length >= 3 && ck.contains(qk)) return 60
        if (qk.length >= 3 && ck.length >= 3) {
            val distance = FileSearch.distance(qk, ck)
            if (distance <= 1) return 70
            if (distance <= 2 && minOf(qk.length, ck.length) >= 6) return 58
        }
        return 0
    }

    private fun canonical(word: String) = FAMILY[word] ?: word

    /** A spelling-neutral key: Devanagari already turned into letters, vowels and look-alike consonants folded together. */
    internal fun phonetic(word: String): String {
        var s = word
        s = s.replace("chh", "C").replace("ch", "C").replace("sh", "S").replace("kh", "k").replace("gh", "g")
            .replace("ph", "f").replace("bh", "b").replace("dh", "d").replace("th", "t").replace("jh", "j")
        s = s.replace("w", "v").replace("z", "j").replace("q", "k").replace("x", "ks").replace("y", "i")
            .replace("ee", "i").replace("oo", "u").replace("ou", "u").replace("ai", "e").replace("au", "o")
        s = s.replace("c", "k")
        val out = StringBuilder()
        for (ch in s) if (out.isEmpty() || out.last() != ch) out.append(ch)
        // A final "h" after a vowel is only a spelling habit (Neha, Nehaa, Nea).
        return out.toString().trimEnd('h').ifEmpty { out.toString() }
    }

    /** Lower-case Latin words with accents and symbols removed; Devanagari is turned into Latin letters first. */
    internal fun tokens(text: String): List<String> {
        val latin = transliterate(text)
        val plain = Normalizer.normalize(latin, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return plain.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
    }

    // ---- Devanagari -> Latin -----------------------------------------------------------------------------------

    private val CONSONANTS = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "n", 'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "n",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n", 'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "f", 'ब' to "b", 'भ' to "bh", 'म' to "m", 'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v",
        'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h"
    )
    private val VOWELS = mapOf(
        'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee", 'उ' to "u", 'ऊ' to "oo", 'ऋ' to "ri",
        'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au", 'ऑ' to "o"
    )
    private val MATRAS = mapOf(
        'ा' to "aa", 'ि' to "i", 'ी' to "ee", 'ु' to "u", 'ू' to "oo", 'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au",
        'ृ' to "ri", 'ॉ' to "o", 'ं' to "n", 'ँ' to "n", 'ः' to "h"
    )
    private const val VIRAMA = '्'
    private const val NUKTA = '़'

    internal fun transliterate(text: String): String {
        if (text.none { it in 'ऀ'..'ॿ' }) return text
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            val consonant = CONSONANTS[ch]
            when {
                consonant != null -> {
                    out.append(consonant)
                    var next = i + 1
                    if (next < text.length && text[next] == NUKTA) next++
                    val following = text.getOrNull(next)
                    val atWordEnd = following == null || !(following in 'ऀ'..'ॿ')
                    // The inherent "a" is kept inside a word but not at its end (राहुल is "rahul", not "rahula").
                    if (following != null && following != VIRAMA && MATRAS[following] == null && !atWordEnd) out.append('a')
                    i = next
                }
                VOWELS[ch] != null -> { out.append(VOWELS[ch]); i++ }
                MATRAS[ch] != null -> { out.append(MATRAS[ch]); i++ }
                ch == VIRAMA || ch == NUKTA -> i++
                else -> { out.append(ch); i++ }
            }
        }
        return out.toString()
    }
}
