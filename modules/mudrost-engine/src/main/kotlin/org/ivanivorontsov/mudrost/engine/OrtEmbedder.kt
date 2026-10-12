package org.ivanivorontsov.mudrost.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.LongBuffer

/** rubert-tiny2 (int8 ONNX) sentence encoder. Output is the L2-normalised [CLS] vector. */
class OrtEmbedder(modelPath: String, private val tokenizer: WordPieceTokenizer) : Embedder, AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession = env.createSession(modelPath, OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(2)
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
    })
    private val cache = object : LinkedHashMap<String, FloatArray>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?) = size > 64
    }

    @Synchronized
    override fun embed(text: String): FloatArray {
        val key = text.lowercase().trim()
        cache[key]?.let { return it }
        val ids = tokenizer.encode(key)
        val shape = longArrayOf(1, ids.size.toLong())
        val inputIds = OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(ids.size) { ids[it].toLong() }), shape)
        val mask = OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(ids.size) { 1L }), shape)
        val types = OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(ids.size) { 0L }), shape)
        try {
            session.run(mapOf("input_ids" to inputIds, "attention_mask" to mask, "token_type_ids" to types)).use { res ->
                @Suppress("UNCHECKED_CAST")
                val out = (res[0].value as Array<FloatArray>)[0]
                cache[key] = out
                return out
            }
        } finally {
            inputIds.close(); mask.close(); types.close()
        }
    }

    override fun close() { session.close() }
}
