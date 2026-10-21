package org.ivanivorontsov.mudrost.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Topic(
    val id: String,
    val title: String,
    val emoji: String,
    val keywords: List<String> = emptyList(),
)

@Serializable
data class Entry(
    val id: String,
    val topics: List<String>,
    /** First item is the canonical question; the rest are paraphrases used for matching. */
    val questions: List<String>,
    val answer: String,
    val keywords: List<String> = emptyList(),
) {
    val title: String get() = questions.first()
}

@Serializable
data class Quote(
    val id: String,
    val text: String,
    val author: String,
    val source: String = "",
    val topics: List<String>,
    /** Quote from the app owner's literary works (Иван Воронцов). */
    val own: Boolean = false,
)

@Serializable
data class Dataset(
    val version: Int,
    val topics: List<Topic>,
    val entries: List<Entry>,
    val quotes: List<Quote>,
    val synonyms: List<List<String>>,
    val embDim: Int = 0,
) {
    val topicById: Map<String, Topic> by lazy { topics.associateBy { it.id } }
    val entryById: Map<String, Entry> by lazy { entries.associateBy { it.id } }
    val quoteById: Map<String, Quote> by lazy { quotes.associateBy { it.id } }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun parse(text: String): Dataset = json.decodeFromString(serializer(), text)
    }
}

/** Precomputed sentence vectors (L2-normalised), laid out as entries' questions then quotes. */
class Vectors(val dim: Int, val entryVecs: List<List<FloatArray>>, val quoteVecs: List<FloatArray>) {
    companion object {
        fun read(bytes: ByteArray, ds: Dataset): Vectors {
            val bb = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            val dim = bb.int
            fun next(): FloatArray = FloatArray(dim) { bb.float }
            val e = ds.entries.map { en -> en.questions.map { next() } }
            val q = ds.quotes.map { next() }
            return Vectors(dim, e, q)
        }
    }
}

fun dot(a: FloatArray, b: FloatArray): Float {
    var s = 0f
    for (i in a.indices) s += a[i] * b[i]
    return s
}
