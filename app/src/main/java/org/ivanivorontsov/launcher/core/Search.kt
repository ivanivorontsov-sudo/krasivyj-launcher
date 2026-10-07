package org.ivanivorontsov.launcher.core

/**
 * Fast offline app search tuned for Russian users:
 *  - case/ё-insensitive matching,
 *  - transliteration both ways (telegram ⇄ телеграм, vk ⇄ вк),
 *  - wrong keyboard layout fix (ntktuhfv → телеграм, ‘ehfepth’ → браузер),
 *  - word-prefix and initials matching (gm → Google Maps, як → Яндекс Карты),
 *  - subsequence fallback, usage boost.
 * Pure Kotlin so it is unit-tested on the JVM.
 */
object Translit {
    private val ruToLat: Map<Char, String> = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "e", 'ж' to "zh",
        'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o",
        'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "ts",
        'ч' to "ch", 'ш' to "sh", 'щ' to "sch", 'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya",
    )

    // Longest-first Latin → Cyrillic rules (common brand spellings first).
    private val latToRu: List<Pair<String, String>> = listOf(
        "shch" to "щ", "sch" to "щ", "you" to "ю", "chr" to "хр", "wh" to "в", "yo" to "ё", "zh" to "ж", "kh" to "х", "ts" to "ц", "ch" to "ч", "sh" to "ш",
        "yu" to "ю", "ya" to "я", "ia" to "ия", "ph" to "ф", "th" to "т", "ck" to "к", "qu" to "кв", "oo" to "у", "ee" to "и",
        "a" to "а", "b" to "б", "c" to "к", "d" to "д", "e" to "е", "f" to "ф", "g" to "г", "h" to "х", "i" to "и",
        "j" to "дж", "k" to "к", "l" to "л", "m" to "м", "n" to "н", "o" to "о", "p" to "п", "q" to "к", "r" to "р",
        "s" to "с", "t" to "т", "u" to "у", "v" to "в", "w" to "в", "x" to "кс", "y" to "й", "z" to "з",
    )

    private const val EN_LAYOUT = "qwertyuiop[]asdfghjkl;'zxcvbnm,.`"
    private const val RU_LAYOUT = "йцукенгшщзхъфывапролджэячсмитьбюё"

    fun toLatin(s: String): String = buildString(s.length + 4) {
        for (ch in s) append(ruToLat[ch] ?: ch.toString())
    }

    fun toCyrillic(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val rule = latToRu.firstOrNull { s.startsWith(it.first, i) }
            if (rule != null) { sb.append(rule.second); i += rule.first.length } else { sb.append(s[i]); i++ }
        }
        return sb.toString()
    }

    /** Re-types the string as if the other keyboard layout had been active. */
    fun swapLayout(s: String): String = buildString(s.length) {
        for (ch in s) {
            val e = EN_LAYOUT.indexOf(ch)
            val r = RU_LAYOUT.indexOf(ch)
            append(
                when {
                    e >= 0 -> RU_LAYOUT[e]
                    r >= 0 -> EN_LAYOUT[r]
                    else -> ch
                }
            )
        }
    }

    fun hasCyrillic(s: String) = s.any { it in 'а'..'я' || it == 'ё' }
    fun hasLatin(s: String) = s.any { it in 'a'..'z' }
}

fun normalize(s: String): String {
    val sb = StringBuilder(s.length)
    var lastSpace = true
    for (raw in s.lowercase()) {
        val ch = if (raw == 'ё') 'е' else raw
        if (ch.isLetterOrDigit()) { sb.append(ch); lastSpace = false }
        else if (!lastSpace) { sb.append(' '); lastSpace = true }
    }
    return sb.toString().trim()
}

/** Precomputed search keys for one item (computed once per app list load, not per keystroke). */
class SearchKey(label: String, extra: String = "") {
    val norm: String = normalize(label)
    val words: List<String> = norm.split(' ').filter { it.isNotEmpty() }
    val initials: String = words.joinToString("") { it.take(1) }
    val latin: String = normalize(Translit.toLatin(norm))
    val latinWords: List<String> = latin.split(' ').filter { it.isNotEmpty() }
    val cyr: String = normalize(Translit.toCyrillic(norm))
    val extra: String = normalize(extra)
}

object SearchEngine {
    /** Score of how well [query] (already normalized) matches [key]; 0 = no match. */
    fun score(key: SearchKey, query: String): Int {
        if (query.isEmpty()) return 1
        var best = scoreVariant(key, query)
        if (best >= 900) return best
        val variants = buildList {
            if (Translit.hasLatin(query)) { add(normalize(Translit.toCyrillic(query))); add(normalize(Translit.swapLayout(query))) }
            if (Translit.hasCyrillic(query)) { add(normalize(Translit.toLatin(query))); add(normalize(Translit.swapLayout(query))) }
        }
        for (v in variants) {
            if (v.isEmpty() || v == query) continue
            val s = scoreVariant(key, v) - 40 // small penalty for converted queries
            if (s > best) best = s
        }
        return best.coerceAtLeast(0)
    }

    private fun scoreVariant(k: SearchKey, q: String): Int {
        if (k.norm == q) return 1000
        if (k.norm.startsWith(q)) return 900 - (k.norm.length - q.length).coerceAtMost(60)
        if (k.words.any { it.startsWith(q) }) return 800
        if (k.latin.startsWith(q) || k.cyr.startsWith(q)) return 760
        if (k.latinWords.any { it.startsWith(q) }) return 720
        if (q.length >= 2 && k.initials.startsWith(q)) return 700
        if (k.norm.contains(q)) return 600
        if (k.latin.contains(q) || k.cyr.contains(q)) return 560
        if (k.extra.isNotEmpty() && k.extra.contains(q)) return 450
        if (q.length >= 3 && isSubsequence(q, k.norm.replace(" ", ""))) return 300
        if (q.length >= 3 && isSubsequence(q, k.latin.replace(" ", ""))) return 260
        return 0
    }

    private fun isSubsequence(q: String, s: String): Boolean {
        var i = 0
        for (ch in s) if (i < q.length && q[i] == ch) i++
        return i == q.length
    }

    /** Filters and ranks items. [usage] boosts frequently launched apps among similar matches. */
    fun <T> search(items: List<T>, rawQuery: String, keyOf: (T) -> SearchKey, usage: (T) -> Int = { 0 }, limit: Int = 60): List<T> {
        val q = normalize(rawQuery)
        if (q.isEmpty()) return items
        val scored = ArrayList<Pair<T, Int>>()
        for (it in items) {
            val s = score(keyOf(it), q)
            // Frequently launched apps get up to +60 points: enough to beat a shorter name in the same tier, not a better match type.
            if (s > 0) scored += it to (s * 100 + (kotlin.math.ln(1.0 + usage(it).coerceAtLeast(0)) * 1200).toInt().coerceAtMost(6000))
        }
        scored.sortByDescending { it.second }
        return scored.take(limit).map { it.first }
    }
}

/** Groups labels by first letter: Cyrillic first (Russian order), then Latin, then '#'. */
object AlphabetIndex {
    const val CYRILLIC = "АБВГДЕЖЗИЙКЛМНОПРСТУФХЦЧШЩЭЮЯ"
    const val LATIN = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"

    fun letterOf(label: String): String {
        val c = label.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: return "#"
        return when {
            c == 'Ё' -> "Е"
            c in 'А'..'Я' -> c.toString()
            c in 'A'..'Z' -> c.toString()
            c.isLetter() -> c.toString()
            else -> "#"
        }
    }

    fun order(letter: String): Int {
        val c = letter.firstOrNull() ?: return 10_000
        val cy = CYRILLIC.indexOf(c)
        if (cy >= 0) return cy
        val la = LATIN.indexOf(c)
        if (la >= 0) return 100 + la
        return if (letter == "#") 1000 else 500 + c.code % 400
    }

    fun <T> sortAndGroup(items: List<T>, labelOf: (T) -> String): List<Pair<String, List<T>>> =
        items.groupBy { letterOf(labelOf(it)) }
            .toSortedMap(compareBy<String> { order(it) }.thenBy { it })
            .map { (k, v) -> k to v.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { labelOf(it) }) }

    /** Full jump-grid alphabet (letters shown even if empty, like the classic tiles phone). */
    fun jumpGrid(present: Set<String>): List<Pair<String, Boolean>> =
        (listOf("#") + CYRILLIC.map { it.toString() } + LATIN.map { it.toString() }).map { it to (it in present) }
}
