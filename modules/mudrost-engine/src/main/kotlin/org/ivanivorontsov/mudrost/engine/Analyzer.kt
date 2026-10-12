package org.ivanivorontsov.mudrost.engine

/** Turns raw text into weighted search terms: stems ("s:"), synonym concepts ("c:") and intents ("i:"). */
class Analyzer(synonymGroups: List<List<String>>) {
    private val conceptByWord = HashMap<String, MutableSet<String>>()
    private val conceptByStem = HashMap<String, MutableSet<String>>()
    private val phraseConcepts = ArrayList<Pair<String, String>>() // normalized phrase -> concept

    init {
        synonymGroups.forEachIndexed { gi, group ->
            val cid = "c:$gi"
            for (raw in group) {
                val n = Text.normalize(raw)
                if (n.isEmpty()) continue
                if (' ' in n) {
                    phraseConcepts.add(n to cid)
                } else {
                    conceptByWord.getOrPut(n) { HashSet() }.add(cid)
                    val st = RussianStemmer.stem(n)
                    if (st.length >= 3) conceptByStem.getOrPut(st) { HashSet() }.add(cid)
                }
            }
        }
    }

    val synonymWords: Set<String> get() = conceptByWord.keys

    fun conceptsOfWord(w: String): Set<String> {
        val out = HashSet<String>()
        conceptByWord[w]?.let(out::addAll)
        val st = RussianStemmer.stem(w)
        if (st.length >= 3) conceptByStem[st]?.let(out::addAll)
        return out
    }

    fun phraseConceptsIn(normalized: String): List<String> {
        val padded = " $normalized "
        return phraseConcepts.filter { padded.contains(" ${it.first} ") }.map { it.second }
    }

    fun phraseMatchesIn(normalized: String): List<Pair<String, String>> {
        val padded = " $normalized "
        return phraseConcepts.filter { padded.contains(" ${it.first} ") }
    }

    /** Document-side analysis: accumulates weighted term frequencies. Returns token count. */
    fun indexInto(text: String, weight: Float, tf: MutableMap<String, Float>, surfaceSink: MutableMap<String, String>?): Int {
        val norm = Text.normalize(text)
        var count = 0
        for (w in norm.split(' ')) {
            if (w.isEmpty()) continue
            Text.INTENTS[w]?.let { tf.merge("i:$it", weight * 0.5f, Float::plus) }
            if (w in Text.STOP || w.length < 2) continue
            val st = RussianStemmer.stem(w)
            tf.merge("s:$st", weight, Float::plus)
            for (c in conceptsOfWord(w)) tf.merge(c, weight * 0.9f, Float::plus)
            surfaceSink?.putIfAbsent(w, st)
            count++
        }
        for (c in phraseConceptsIn(norm)) tf.merge(c, weight * 0.9f, Float::plus)
        return count
    }
}
