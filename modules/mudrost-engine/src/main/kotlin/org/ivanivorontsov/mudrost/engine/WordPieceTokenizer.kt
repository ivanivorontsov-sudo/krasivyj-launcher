package org.ivanivorontsov.mudrost.engine

/** BERT BasicTokenizer + WordPiece, matching HF `BertTokenizer` for cointegrated/rubert-tiny2 (cased, no accent stripping). */
class WordPieceTokenizer(vocabLines: List<String>) {
    private val vocab = HashMap<String, Int>(vocabLines.size * 2)
    init { vocabLines.forEachIndexed { i, t -> vocab[t] = i } }
    private val unk = vocab["[UNK]"] ?: 1
    private val cls = vocab["[CLS]"] ?: 2
    private val sep = vocab["[SEP]"] ?: 3

    private fun isPunct(cp: Int): Boolean {
        if (cp in 33..47 || cp in 58..64 || cp in 91..96 || cp in 123..126) return true
        return when (Character.getType(cp).toByte()) {
            Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION, Character.START_PUNCTUATION,
            Character.END_PUNCTUATION, Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
            Character.OTHER_PUNCTUATION -> true
            else -> false
        }
    }
    private fun isWhitespace(cp: Int) = cp == ' '.code || cp == '\t'.code || cp == '\n'.code || cp == '\r'.code ||
        Character.getType(cp).toByte() == Character.SPACE_SEPARATOR
    private fun isControl(cp: Int): Boolean {
        if (cp == '\t'.code || cp == '\n'.code || cp == '\r'.code) return false
        val t = Character.getType(cp).toByte()
        return t == Character.CONTROL || t == Character.FORMAT
    }
    private fun isCjk(cp: Int) = cp in 0x4E00..0x9FFF || cp in 0x3400..0x4DBF || cp in 0x20000..0x2A6DF ||
        cp in 0x2A700..0x2B73F || cp in 0x2B740..0x2B81F || cp in 0x2B820..0x2CEAF || cp in 0xF900..0xFAFF || cp in 0x2F800..0x2FA1F

    fun basicTokens(text: String): List<String> {
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            if (cp == 0 || cp == 0xFFFD || isControl(cp)) continue
            when {
                isWhitespace(cp) -> sb.append(' ')
                isCjk(cp) || isPunct(cp) -> { sb.append(' '); sb.appendCodePoint(cp); sb.append(' ') }
                else -> sb.appendCodePoint(cp)
            }
        }
        return sb.split(' ').filter { it.isNotEmpty() }
    }

    fun wordPiece(token: String, out: MutableList<Int>) {
        if (token.codePointCount(0, token.length) > 100) { out.add(unk); return }
        val pieces = ArrayList<Int>()
        var start = 0
        while (start < token.length) {
            var end = token.length
            var found = -1
            while (start < end) {
                val sub = (if (start > 0) "##" else "") + token.substring(start, end)
                val id = vocab[sub]
                if (id != null) { found = id; break }
                end--
                if (end > start && Character.isLowSurrogate(token[end])) end--
            }
            if (found < 0) { out.add(unk); return }
            pieces.add(found)
            start = end
        }
        out.addAll(pieces)
    }

    fun encode(text: String, maxLen: Int = 128): IntArray {
        val ids = ArrayList<Int>()
        ids.add(cls)
        for (t in basicTokens(text)) {
            wordPiece(t, ids)
            if (ids.size >= maxLen - 1) break
        }
        while (ids.size > maxLen - 1) ids.removeAt(ids.size - 1)
        ids.add(sep)
        return ids.toIntArray()
    }
}
