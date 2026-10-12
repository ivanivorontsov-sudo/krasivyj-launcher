package org.ivanivorontsov.mudrost.engine

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/** Something that turns a sentence into an L2-normalised vector (on-device rubert-tiny2). */
fun interface Embedder {
    fun embed(text: String): FloatArray
}

data class Hit(
    val entry: Entry,
    val score: Double,
    val lexical: Double,
    val semantic: Double,
)

data class SearchResult(
    val query: String,
    /** Query after typo / keyboard-layout correction, when it differs from the input. */
    val corrected: String?,
    val hits: List<Hit>,
    val topics: List<Topic>,
    val confident: Boolean,
)

sealed class Suggestion {
    abstract val text: String
    data class TopicSuggestion(val topic: Topic) : Suggestion() { override val text get() = topic.title }
    data class QuestionSuggestion(val entry: Entry, override val text: String) : Suggestion()
}

private class Bm25Index(val docs: List<Map<String, Float>>, val lengths: FloatArray) {
    val df = HashMap<String, Int>()
    val avgLen: Float
    init {
        for (d in docs) for (t in d.keys) df.merge(t, 1, Int::plus)
        avgLen = if (lengths.isEmpty()) 1f else max(1f, lengths.average().toFloat())
    }
    fun idf(t: String): Double {
        val n = docs.size.toDouble()
        val f = (df[t] ?: 0).toDouble()
        return ln(1.0 + (n - f + 0.5) / (f + 0.5))
    }
    fun termScore(doc: Int, t: String, k1: Double = 1.2, b: Double = 0.5): Double {
        val tf = docs[doc][t] ?: return 0.0
        val norm = k1 * (1 - b + b * lengths[doc] / avgLen)
        return idf(t) * tf * (k1 + 1) / (tf + norm)
    }
}

/** A group of alternative terms produced from one query word. */
private data class QueryGroup(val word: String, val terms: List<Pair<String, Double>>, val correction: String?)

class SearchEngine(
    val data: Dataset,
    private val vectors: Vectors? = null,
    @Volatile var embedder: Embedder? = null,
) {
    private val analyzer = Analyzer(data.synonyms)
    private val vocab = HashMap<String, String>() // surface word -> stem
    private val vocabByLen = HashMap<Int, MutableList<String>>()
    private val entryIndex: Bm25Index
    private val quoteIndex: Bm25Index
    private val entryTrigrams: List<List<Set<String>>>
    private val entryPos = data.entries.withIndex().associate { it.value.id to it.index }

    init {
        val docs = ArrayList<Map<String, Float>>()
        val lens = ArrayList<Float>()
        for (e in data.entries) {
            val tf = HashMap<String, Float>()
            var len = 0f
            e.questions.forEachIndexed { i, q -> len += 3f * analyzer.indexInto(q, if (i == 0) 3.2f else 2.6f, tf, vocab) }
            len += 1.5f * analyzer.indexInto(e.keywords.joinToString(" "), 2.0f, tf, vocab)
            for (tid in e.topics) {
                val t = data.topicById[tid] ?: continue
                val w = if (tid == e.topics.first()) 1.4f else 0.8f
                len += w * analyzer.indexInto(t.title + " " + t.keywords.joinToString(" "), w, tf, vocab)
            }
            len += 0.5f * analyzer.indexInto(e.answer, 0.5f, tf, vocab)
            docs.add(tf); lens.add(len)
        }
        entryIndex = Bm25Index(docs, lens.toFloatArray())

        val qdocs = ArrayList<Map<String, Float>>()
        val qlens = ArrayList<Float>()
        for (q in data.quotes) {
            val tf = HashMap<String, Float>()
            var len = analyzer.indexInto(q.text, 1f, tf, vocab).toFloat()
            for (tid in q.topics) data.topicById[tid]?.let { t ->
                len += 0.4f * analyzer.indexInto(t.title + " " + t.keywords.joinToString(" "), 0.4f, tf, vocab)
            }
            qdocs.add(tf); qlens.add(len)
        }
        quoteIndex = Bm25Index(qdocs, qlens.toFloatArray())
        for (t in data.topics) analyzer.indexInto(t.title + " " + t.keywords.joinToString(" "), 1f, HashMap(), vocab)
        for (w in analyzer.synonymWords) vocab.putIfAbsent(w, RussianStemmer.stem(w))
        for (w in vocab.keys) vocabByLen.getOrPut(w.length) { ArrayList() }.add(w)
        entryTrigrams = data.entries.map { e -> e.questions.map { Text.trigrams(it) } }
    }

    private fun known(term: String) = (entryIndex.df[term] ?: 0) > 0 || (quoteIndex.df[term] ?: 0) > 0

    private val phoneticIndex: Map<String, List<String>> by lazy { vocab.keys.groupBy { Text.phoneticKey(it) } }

    /** Typo candidates: exact phonetic match first, then Damerau–Levenshtein on raw and phonetic forms. */
    private fun fuzzyCandidates(w: String): Pair<Int, List<String>> {
        if (w.length < 4 || !Text.isCyrillicWord(w)) return Int.MAX_VALUE to emptyList()
        val key = Text.phoneticKey(w)
        phoneticIndex[key]?.let { same ->
            return 1 to same.sortedBy { -(entryIndex.df["s:" + vocab[it]] ?: 0) }.take(3)
        }
        val maxD = if (w.length <= 5) 1 else 2
        var best = Double.MAX_VALUE
        val out = ArrayList<Pair<String, Double>>()
        for (len in (w.length - maxD - 1)..(w.length + maxD + 1)) {
            val list = vocabByLen[len] ?: continue
            for (v in list) {
                val dRaw = Text.editDistance(w, v, maxD)
                val dPh = Text.editDistance(key, Text.phoneticKey(v), maxD) + 0.5
                var d = minOf(dRaw.toDouble(), dPh)
                if (v[0] != w[0]) d += 0.3
                if (d <= maxD + 0.3) out.add(v to d)
                if (d < best) best = d
            }
        }
        if (out.isEmpty()) return Int.MAX_VALUE to emptyList()
        val top = out.filter { it.second <= best + 1e-9 }.map { it.first }
            .sortedBy { -(entryIndex.df["s:" + vocab[it]] ?: 0) }
        return (if (best <= 1.3) 1 else 2) to top.take(3)
    }

    private fun buildGroups(words: List<String>, prefixLast: Boolean): List<QueryGroup> {
        val groups = ArrayList<QueryGroup>()
        words.forEachIndexed { idx, w ->
            if (w in Text.STOP || w.length < 2) return@forEachIndexed
            val terms = LinkedHashMap<String, Double>()
            fun add(t: String, weight: Double) { if (weight > (terms[t] ?: 0.0)) terms[t] = weight }
            val st = RussianStemmer.stem(w)
            var correction: String? = null
            if (known("s:$st")) add("s:$st", 1.0)
            val concepts = analyzer.conceptsOfWord(w)
            for (c in concepts) add(c, 0.8)
            val isLast = idx == words.lastIndex
            if (terms.isEmpty() && st.length >= 4) {
                // Stemmer inconsistencies across word forms (экзамены → экзам, экзаменам → экзамен): same family, no "correction".
                val family = vocab.values.asSequence().distinct().filter { v ->
                    (v.startsWith(st) && v.length - st.length <= 3) || (st.startsWith(v) && v.length >= 4 && st.length - v.length <= 2)
                }.filter { known("s:$it") }.take(4).toList()
                for (v in family) add("s:$v", 0.95)
            }
            if (terms.isEmpty()) {
                val (d, cands) = fuzzyCandidates(w)
                if (cands.isNotEmpty()) {
                    correction = cands.first()
                    val ws = if (d <= 1) 0.85 else 0.65
                    for (c in cands) {
                        add("s:" + vocab[c]!!, ws)
                        for (cc in analyzer.conceptsOfWord(c)) add(cc, ws * 0.8)
                    }
                }
            }
            if (prefixLast && isLast && w.length >= 3) {
                val pre = vocab.keys.asSequence().filter { it.length > w.length && it.startsWith(w) }
                    .sortedBy { -(entryIndex.df["s:" + vocab[it]] ?: 0) }.take(8).toList()
                for (p in pre) {
                    add("s:" + vocab[p]!!, 0.6)
                    for (cc in analyzer.conceptsOfWord(p)) add(cc, 0.5)
                }
            }
            if (terms.isNotEmpty()) groups.add(QueryGroup(w, terms.toList(), correction))
        }
        return groups
    }

    private fun prepare(raw: String): Pair<String, String?> {
        val fixed = Text.fixLayout(raw)
        return if (fixed != null) fixed to fixed else raw to null
    }

    /** Debug helper: how the query was understood. */
    fun explain(raw: String): String {
        val norm = Text.normalize(prepare(raw).first)
        val (words, phrases) = splitPhrases(norm)
        return (buildGroups(words, false).map { g -> g.word + "=" + g.terms.joinToString(",") { "${it.first}:${"%.2f".format(it.second)}" } } +
            phrases.map { "phrase:$it" }).joinToString("  ")
    }

    /** Multi-word synonyms ("никому не нужен", "верю в себя") replace the single words they consist of. */
    private fun splitPhrases(norm: String): Pair<List<String>, List<String>> {
        val words = norm.split(' ').filter { it.isNotEmpty() }.toMutableList()
        val found = analyzer.phraseMatchesIn(norm)
        val consumed = HashSet<String>()
        for ((phrase, _) in found) consumed.addAll(phrase.split(' '))
        val rest = if (found.isEmpty()) words else words.filter { it !in consumed }
        return rest to found.map { it.second }.distinct()
    }

    fun search(raw: String, limit: Int = 10, prefixLast: Boolean = false, useSemantic: Boolean = true): SearchResult {
        val (text, layoutFix) = prepare(raw)
        val norm = Text.normalize(text)
        val words = norm.split(' ').filter { it.isNotEmpty() }
        val (singleWords, phraseConcepts) = splitPhrases(norm)
        val groups = buildGroups(singleWords, prefixLast).toMutableList()
        for (pc in phraseConcepts) groups.add(QueryGroup(pc, listOf(pc to 1.0), null))
        val intents = words.mapNotNull { Text.INTENTS[it] }.distinct().map { "i:$it" }

        val n = data.entries.size
        val lex = DoubleArray(n)
        var maxCoverage = 0.0
        for (d in 0 until n) {
            var s = 0.0
            var covered = 0
            for (g in groups) {
                var best = 0.0
                for ((t, w) in g.terms) best = max(best, w * entryIndex.termScore(d, t))
                // also sum a little of the other alternatives (synonyms reinforce)
                var extra = 0.0
                for ((t, w) in g.terms) extra += w * entryIndex.termScore(d, t)
                s += best + 0.25 * (extra - best)
                if (best > 0) covered++
            }
            for (t in intents) s += 0.3 * entryIndex.termScore(d, t)
            val coverage = if (groups.isEmpty()) 0.0 else covered.toDouble() / groups.size
            lex[d] = s * (0.35 + 0.65 * coverage * coverage)
            if (coverage > maxCoverage) maxCoverage = coverage
        }
        val maxLex = lex.maxOrNull() ?: 0.0
        val qTri = Text.trigrams(norm)
        val dice = DoubleArray(n) { d -> if (norm.length < 4) 0.0 else entryTrigrams[d].maxOf { Text.dice(qTri, it) } }

        val emb = if (useSemantic) embedder else null
        val qv: FloatArray? = if (emb != null && vectors != null && norm.isNotBlank()) runCatching { emb.embed(norm) }.getOrNull() else null
        val sem = DoubleArray(n)
        if (qv != null) for (d in 0 until n) {
            val cos = vectors!!.entryVecs[d].maxOf { dot(qv, it).toDouble() }
            sem[d] = ((cos - 0.55) / 0.35).coerceIn(0.0, 1.0)
        }
        val hits = (0 until n).map { d ->
            val l = if (maxLex > 0) lex[d] / maxLex else 0.0
            // The less of the query the lexical index understood, the more we trust the sentence embedding.
            val ws = when {
                qv == null -> 0.0
                maxCoverage >= 0.99 -> 0.35
                maxCoverage >= 0.5 -> 0.5
                else -> 0.7
            }
            val score = (1 - ws) * (0.8 * l + 0.2 * dice[d]) + ws * sem[d]
            Hit(data.entries[d], score, l, sem[d])
        }.filter { it.score > 0.05 }.sortedByDescending { it.score }.take(limit)

        val corrections = groups.filter { it.correction != null }
        val corrected = when {
            // Layout fix and typo fix combine: "cvscjk ;bpyb" → "смысол жизни" → "смысл жизни".
            corrections.isNotEmpty() -> words.joinToString(" ") { w -> corrections.firstOrNull { it.word == w }?.correction ?: w }
            layoutFix != null -> layoutFix
            else -> null
        }
        val confident = hits.isNotEmpty() && ((maxLex > 1.0 && maxCoverage >= 0.5) || hits.first().semantic > 0.55)
        return SearchResult(raw, corrected, hits, matchTopics(words), confident)
    }

    private fun matchTopics(words: List<String>): List<Topic> {
        if (words.isEmpty()) return emptyList()
        val stems = words.filter { it !in Text.STOP && it.length >= 3 }.map { RussianStemmer.stem(it) }.toSet()
        val concepts = words.flatMap { analyzer.conceptsOfWord(it) }.toSet()
        return data.topics.filter { t ->
            val tw = Text.words(t.title + " " + t.keywords.joinToString(" "))
            tw.any { RussianStemmer.stem(it) in stems } || tw.any { w -> analyzer.conceptsOfWord(w).any { it in concepts } }
        }.take(4)
    }

    /** Live suggestions while typing (topics + questions), typo- and prefix-tolerant. */
    fun suggest(raw: String, limit: Int = 7): List<Suggestion> {
        val (text, _) = prepare(raw)
        val norm = Text.normalize(text)
        if (norm.length < 2) return emptyList()
        val words = norm.split(' ')
        val last = words.last()
        val topicSug = data.topics.filter { t ->
            val tn = Text.normalize(t.title)
            tn.startsWith(norm) || tn.split(' ').any { it.startsWith(last) && last.length >= 2 } ||
                t.keywords.any { k -> Text.normalize(k).startsWith(last) && last.length >= 3 }
        }.take(3).map { Suggestion.TopicSuggestion(it) }
        val res = search(text, limit = limit, prefixLast = true, useSemantic = false)
        val qTri = Text.trigrams(norm)
        val qSug = res.hits.filter { it.lexical > 0.15 }.map { h ->
            val best = h.entry.questions.maxBy { Text.dice(qTri, Text.trigrams(it)) }
            Suggestion.QuestionSuggestion(h.entry, if (Text.dice(qTri, Text.trigrams(best)) > 0.45) best else h.entry.title)
        }.distinctBy { it.entry.id }
        return (topicSug + qSug).take(limit)
    }

    /** Quotes ranked for the answer shown (topic overlap + lexical + semantic), own quotes prioritised. */
    fun quotesFor(entry: Entry?, query: String, useSemantic: Boolean = true): List<Quote> {
        val norm = Text.normalize(Text.fixLayout(query) ?: query)
        val words = norm.split(' ').filter { it.isNotEmpty() }
        val groups = buildGroups(words, false)
        val n = data.quotes.size
        val lex = DoubleArray(n) { d ->
            groups.sumOf { g -> g.terms.maxOf { (t, w) -> w * quoteIndex.termScore(d, t) } }
        }
        val maxLex = max(lex.maxOrNull() ?: 0.0, 1e-9)
        val emb = if (useSemantic) embedder else null
        val qv = if (emb != null && vectors != null && norm.isNotBlank()) runCatching { emb.embed(norm) }.getOrNull() else null
        val primary = entry?.topics?.firstOrNull()
        val scored = data.quotes.mapIndexed { d, q ->
            val topic = when {
                entry == null -> 0.0
                primary != null && primary in q.topics -> 1.0
                q.topics.any { it in entry.topics } -> 0.6
                else -> 0.0
            }
            val s = if (qv != null) ((dot(qv, vectors!!.quoteVecs[d]) - 0.5) / 0.4).coerceIn(0.0, 1.0) else 0.0
            var score = topic + 0.6 * (lex[d] / maxLex) + 0.5 * s
            if (q.own && (topic > 0 || lex[d] / maxLex > 0.5)) score *= 1.15
            q to score
        }
        val ranked = scored.filter { it.second > 0.45 }.sortedByDescending { it.second }.map { it.first }
            .ifEmpty { scored.sortedByDescending { it.second }.take(10).map { it.first } }
        return balanceOwn(ranked)
    }

    /**
     * Own quotes get priority when relevant, but stay a minority: at most one of them in the first
     * three positions and about one in four overall; famous authors fill the rest.
     */
    private fun balanceOwn(ranked: List<Quote>): List<Quote> {
        val own = ArrayDeque(ranked.filter { it.own })
        val famous = ArrayDeque(ranked.filter { !it.own })
        val out = ArrayList<Quote>(ranked.size)
        val firstOwnPos = ranked.indexOfFirst { it.own }
        while (own.isNotEmpty() || famous.isNotEmpty()) {
            val i = out.size
            val ownSlot = if (i < 3) (i == minOf(firstOwnPos.coerceAtLeast(0), 1) && out.none { it.own }) else i % 4 == 1
            val next = when {
                ownSlot && own.isNotEmpty() -> own.removeFirst()
                famous.isNotEmpty() -> famous.removeFirst()
                else -> own.removeFirst()
            }
            out.add(next)
        }
        return out
    }

    fun entriesOf(topicId: String) = data.entries.filter { topicId in it.topics }
    fun quotesOf(topicId: String) = data.quotes.filter { topicId in it.topics }

    fun related(entry: Entry, limit: Int = 4): List<Entry> {
        val p = entryPos[entry.id] ?: return emptyList()
        val v = vectors
        return if (v != null) {
            val me = v.entryVecs[p].first()
            data.entries.indices.filter { it != p }
                .sortedByDescending { i -> dot(me, v.entryVecs[i].first()) + if (data.entries[i].topics.first() == entry.topics.first()) 0.08f else 0f }
                .take(limit).map { data.entries[it] }
        } else data.entries.filter { it.id != entry.id && it.topics.any { t -> t in entry.topics } }.take(limit)
    }

    fun quoteOfTheDay(dayNumber: Long): Quote = data.quotes[Math.floorMod(dayNumber * 2654435761L, data.quotes.size.toLong()).toInt()]
}
