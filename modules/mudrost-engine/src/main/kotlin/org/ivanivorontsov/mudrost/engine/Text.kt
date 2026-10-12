package org.ivanivorontsov.mudrost.engine

object Text {
    private const val EN = "qwertyuiop[]asdfghjkl;'zxcvbnm,.`"
    private const val RU = "йцукенгшщзхъфывапролджэячсмитьбюё"

    /** Words that carry no search meaning. Intent words (зачем, почему, как быть) are handled separately. */
    val STOP: Set<String> = """
        и в во не что он на я с со как а то все всё она так его но да ты к у же вы за бы по только ее её мне было вот от меня
        еще ещё нет о об из ему теперь когда даже ну вдруг ли если уже или ни быть был него до вас нибудь опять уж вам ведь там
        потом себя ничего ей может они тут где есть надо ней для мы тебя их чем была сам чтоб без будто чего раз тоже себе под
        будет ж тогда кто этот того потому этого какой совсем ним здесь этом один почти мой тем чтобы нее неё сейчас были куда
        всех никогда можно при наконец два об другой хоть после над больше тот через эти нас про всего них какая много разве
        три эту моя впрочем хорошо свою этой перед иногда лучше чуть том нельзя такой им более всегда конечно всю между это
        эта мне мной мною твой твоя твоё твои мои моё свой своя своё свои который которая которые которое какие каким каких
        очень просто вообще либо нибудь кто-то что-то как-то ли бы же ка пожалуйста скажи подскажи расскажи объясни хочу
        хочется знать понять делать сделать такое такая такие также тоже ещё вот всегда который котором будто весь вся
        могу можешь может смогу сможет мочь стал стала стало вроде типа вообще-то наверное
        """.trim().split(Regex("\\s+")).toSet()

    /** Intent markers — kept as special tokens (boost matching question types). */
    val INTENTS: Map<String, String> = mapOf(
        "зачем" to "@why", "почему" to "@why", "для" to "@why", "смысл" to "@why",
        "как" to "@how", "каким" to "@how",
        "что" to "@what", "можно" to "@can", "стоит" to "@can", "нужно" to "@can", "надо" to "@can",
        "перестать" to "@stop", "избавиться" to "@stop", "справиться" to "@stop", "пережить" to "@stop",
        "преодолеть" to "@stop", "побороть" to "@stop"
    )

    fun normalize(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s.lowercase()) {
            val c = if (ch == 'ё') 'е' else ch
            sb.append(if (c.isLetterOrDigit() || c == '-') c else ' ')
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    fun words(s: String): List<String> =
        normalize(s).split(' ').map { it.trim('-') }.filter { it.isNotEmpty() }

    /** "cvsck ;bpyb" → "смысл жизни" when the user forgot to switch keyboard layout. */
    fun fixLayout(s: String): String? {
        val lower = s.lowercase()
        val latin = lower.count { it in 'a'..'z' }
        val cyr = lower.count { it in 'а'..'я' || it == 'ё' }
        if (latin < 3 || cyr > 0) return null
        val sb = StringBuilder()
        for (c in lower) {
            val i = EN.indexOf(c)
            sb.append(if (i >= 0) RU[i] else c)
        }
        return sb.toString()
    }

    fun isCyrillicWord(w: String) = w.any { it in 'а'..'я' }

    /** Character trigrams with word padding, used for phrase-level fuzzy similarity. */
    fun trigrams(s: String): Set<String> {
        val n = normalize(s)
        if (n.isEmpty()) return emptySet()
        val p = " $n "
        val out = HashSet<String>()
        for (i in 0 until p.length - 2) out.add(p.substring(i, i + 3))
        return out
    }

    fun dice(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        var inter = 0
        val (small, big) = if (a.size < b.size) a to b else b to a
        for (x in small) if (x in big) inter++
        return 2.0 * inter / (a.size + b.size)
    }

    private val PHON = listOf(
        "сч" to "щ", "зч" to "щ", "жч" to "щ", "шч" to "щ", "тьс" to "ц", "тс" to "ц", "дс" to "ц",
        "жы" to "жи", "шы" to "ши", "чя" to "ча", "щя" to "ща", "чю" to "чу", "щю" to "щу",
        "ь" to "", "ъ" to "", "й" to "и", "ы" to "и", "э" to "е", "о" to "а", "я" to "а", "ю" to "у", "е" to "и",
    )

    /** Rough phonetic key for Russian spelling mistakes (щастье → счастье, пережыть → пережить, малако → молоко). */
    fun phoneticKey(w: String): String {
        var s = w.replace('ё', 'е')
        for ((a, b) in PHON) s = s.replace(a, b)
        val sb = StringBuilder()
        for (c in s) if (sb.isEmpty() || sb[sb.length - 1] != c) sb.append(c) // collapse doubled letters
        return sb.toString()
    }

    /** Optimal-string-alignment (Damerau–Levenshtein) distance with early cut-off. */
    fun editDistance(a: String, b: String, max: Int): Int {
        val la = a.length
        val lb = b.length
        if (kotlin.math.abs(la - lb) > max) return max + 1
        var prev2 = IntArray(lb + 1)
        var prev = IntArray(lb + 1) { it }
        var cur = IntArray(lb + 1)
        for (i in 1..la) {
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..lb) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, prev2[j - 2] + 1)
                cur[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > max) return max + 1
            val t = prev2; prev2 = prev; prev = cur; cur = t
        }
        return prev[lb]
    }
}
