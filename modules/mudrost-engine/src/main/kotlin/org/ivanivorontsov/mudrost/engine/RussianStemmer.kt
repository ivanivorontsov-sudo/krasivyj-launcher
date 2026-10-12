package org.ivanivorontsov.mudrost.engine

/**
 * Faithful Kotlin port of the Snowball Russian stemmer
 * (https://snowballstem.org/algorithms/russian/stemmer.html).
 * Verified against the Python `snowballstemmer` package in unit tests.
 */
object RussianStemmer {
    private const val VOWELS = "аеиоуыэюя"
    private fun isV(c: Char) = VOWELS.indexOf(c) >= 0

    private val PG1 = listOf("в", "вши", "вшись")
    private val PG2 = listOf("ив", "ивши", "ившись", "ыв", "ывши", "ывшись")
    private val ADJ = listOf(
        "ее", "ие", "ые", "ое", "ими", "ыми", "ей", "ий", "ый", "ой", "ем", "им", "ым", "ом",
        "его", "ого", "ему", "ому", "их", "ых", "ую", "юю", "ая", "яя", "ою", "ею"
    )
    private val PART1 = listOf("ем", "нн", "вш", "ющ", "щ")
    private val PART2 = listOf("ивш", "ывш", "ующ")
    private val REFL = listOf("ся", "сь")
    private val VERB1 = listOf(
        "ла", "на", "ете", "йте", "ли", "й", "л", "ем", "н", "ло", "но", "ет", "ют", "ны", "ть", "ешь", "нно"
    )
    private val VERB2 = listOf(
        "ила", "ыла", "ена", "ейте", "уйте", "ите", "или", "ыли", "ей", "уй", "ил", "ыл", "им", "ым", "ен",
        "ило", "ыло", "ено", "ят", "ует", "уют", "ит", "ыт", "ены", "ить", "ыть", "ишь", "ую", "ю"
    )
    private val NOUN = listOf(
        "а", "ев", "ов", "ие", "ье", "е", "иями", "ями", "ами", "еи", "ии", "и", "ией", "ей", "ой", "ий", "й",
        "иям", "ям", "ием", "ем", "ам", "ом", "о", "у", "ах", "иях", "ях", "ы", "ь", "ию", "ью", "ю", "ия", "ья", "я"
    )
    private val DERIV = listOf("ост", "ость")
    private val EMPTY = emptyList<String>()

    private val cache = object : LinkedHashMap<String, String>(4096, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 20000
    }

    fun stem(word: String): String {
        synchronized(cache) { cache[word]?.let { return it } }
        val r = stemUncached(word)
        synchronized(cache) { cache[word] = r }
        return r
    }

    private fun endsWithin(s: StringBuilder, suf: String, limit: Int): Boolean {
        val start = s.length - suf.length
        if (start < limit) return false
        for (i in suf.indices) if (s[start + i] != suf[i]) return false
        return true
    }

    /** Snowball `[substring] among(...)` with an optional "preceded by а/я" group. Returns true on deletion. */
    private fun among(s: StringBuilder, limit: Int, g1: List<String>, g2: List<String>): Boolean {
        var best: String? = null
        var bestIsG1 = false
        for (x in g1) if (endsWithin(s, x, limit) && (best == null || x.length > best.length)) { best = x; bestIsG1 = true }
        for (x in g2) if (endsWithin(s, x, limit) && (best == null || x.length > best.length)) { best = x; bestIsG1 = false }
        if (best == null) return false
        if (bestIsG1) {
            val pos = s.length - best.length - 1
            if (pos < limit) return false
            val c = s[pos]
            if (c != 'а' && c != 'я') return false
        }
        s.setLength(s.length - best.length)
        return true
    }

    private fun stemUncached(word: String): String {
        val w = word.lowercase().replace('ё', 'е')
        val n = w.length
        var pV = n
        var p2 = n
        run {
            var i = 0
            while (i < n && !isV(w[i])) i++
            if (i >= n) return@run
            pV = i + 1
            var j = pV
            while (j < n && isV(w[j])) j++
            if (j >= n) return@run
            j++ // past non-vowel -> R1
            while (j < n && !isV(w[j])) j++
            if (j >= n) return@run
            j++
            while (j < n && isV(w[j])) j++
            if (j >= n) return@run
            p2 = j + 1
        }
        val s = StringBuilder(w)
        if (!among(s, pV, PG1, PG2)) {
            among(s, pV, EMPTY, REFL)
            if (among(s, pV, EMPTY, ADJ)) {
                among(s, pV, PART1, PART2)
            } else if (!among(s, pV, VERB1, VERB2)) {
                among(s, pV, EMPTY, NOUN)
            }
        }
        if (s.length - 1 >= pV && s.isNotEmpty() && s[s.length - 1] == 'и') s.setLength(s.length - 1)
        // derivational (R2)
        run {
            var best: String? = null
            for (x in DERIV) if (endsWithin(s, x, pV) && (best == null || x.length > best!!.length)) best = x
            val b = best
            if (b != null && s.length - b.length >= p2) s.setLength(s.length - b.length)
        }
        // tidy_up
        run {
            val cands = listOf("ейш", "ейше", "н", "ь")
            var best: String? = null
            for (x in cands) if (endsWithin(s, x, pV) && (best == null || x.length > best!!.length)) best = x
            when (best) {
                "ейш", "ейше" -> {
                    s.setLength(s.length - best.length)
                    if (endsWithin(s, "нн", pV)) s.setLength(s.length - 1)
                }
                "н" -> if (endsWithin(s, "нн", pV)) s.setLength(s.length - 1)
                "ь" -> s.setLength(s.length - 1)
            }
        }
        return s.toString()
    }
}
