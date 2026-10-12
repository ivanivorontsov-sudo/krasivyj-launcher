package org.ivanivorontsov.mudrost

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ivanivorontsov.mudrost.engine.Dataset
import org.ivanivorontsov.mudrost.engine.Entry
import org.ivanivorontsov.mudrost.engine.OrtEmbedder
import org.ivanivorontsov.mudrost.engine.Quote
import org.ivanivorontsov.mudrost.engine.SearchEngine
import org.ivanivorontsov.mudrost.engine.SearchResult
import org.ivanivorontsov.mudrost.engine.Suggestion
import org.ivanivorontsov.mudrost.engine.Topic
import org.ivanivorontsov.mudrost.engine.Vectors
import org.ivanivorontsov.mudrost.engine.WordPieceTokenizer
import java.io.File

sealed interface Screen {
    data object Home : Screen
    data object Topics : Screen
    data object Favorites : Screen
    data object History : Screen
    data class Answer(val query: String, val result: SearchResult?, val entry: Entry?, val quotes: List<Quote>, val quoteIndex: Int = 0) : Screen
    data class TopicDetail(val topic: Topic) : Screen
}

data class UiState(
    val engine: SearchEngine? = null,
    val semanticReady: Boolean = false,
    val loadError: String? = null,
    val query: String = "",
    val suggestions: List<Suggestion> = emptyList(),
    val stack: List<Screen> = listOf(Screen.Home),
    val favEntries: List<String> = emptyList(),
    val favQuotes: List<String> = emptyList(),
    val history: List<HistoryItem> = emptyList(),
    val searching: Boolean = false,
) {
    val screen: Screen get() = stack.last()
    val tab: Screen get() = stack.first()
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    private val _state = MutableStateFlow(UiState(favEntries = store.favEntries(), favQuotes = store.favQuotes(), history = store.history()))
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var suggestJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val assets = app.assets
                val ds = Dataset.parse(assets.open("data.json").bufferedReader().use { it.readText() })
                val vectors = runCatching { Vectors.read(assets.open("emb.bin").use { it.readBytes() }, ds) }.getOrNull()
                val engine = SearchEngine(ds, vectors)
                _state.update { it.copy(engine = engine) }
                // The neural encoder is optional: lexical search already works; semantic ranking is added when ready.
                try {
                    val model = File(app.filesDir, "rubert_tiny2_int8.onnx")
                    val size = assets.openFd("model/rubert_tiny2_int8.onnx").use { it.length }
                    if (!model.exists() || model.length() != size) {
                        val tmp = File(app.filesDir, "model.tmp")
                        assets.open("model/rubert_tiny2_int8.onnx").use { i -> tmp.outputStream().use { i.copyTo(it, 1 shl 16) } }
                        tmp.renameTo(model)
                    }
                    val vocab = assets.open("model/vocab.txt").bufferedReader().readLines()
                    val emb = OrtEmbedder(model.absolutePath, WordPieceTokenizer(vocab))
                    emb.embed("привет") // warm-up
                    engine.embedder = emb
                    _state.update { it.copy(semanticReady = true) }
                } catch (t: Throwable) {
                    Log.w("Mudrost", "semantic model unavailable, lexical search only", t)
                }
            } catch (t: Throwable) {
                Log.e("Mudrost", "failed to load data", t)
                _state.update { it.copy(loadError = t.message ?: t.toString()) }
            }
        }
    }

    fun onQueryChange(q: String) {
        _state.update { it.copy(query = q) }
        suggestJob?.cancel()
        val engine = _state.value.engine ?: return
        if (q.isBlank()) { _state.update { it.copy(suggestions = emptyList()) }; return }
        suggestJob = viewModelScope.launch(Dispatchers.Default) {
            delay(90)
            val s = runCatching { engine.suggest(q) }.getOrDefault(emptyList())
            _state.update { if (it.query == q) it.copy(suggestions = s) else it }
        }
    }

    fun submit(q: String = _state.value.query) {
        val query = q.trim()
        val engine = _state.value.engine ?: return
        if (query.isEmpty()) return
        suggestJob?.cancel()
        _state.update { it.copy(searching = true, suggestions = emptyList(), query = query, history = store.addHistory(query)) }
        viewModelScope.launch {
            val screen = withContext(Dispatchers.Default) {
                val res = engine.search(query, limit = 8)
                val entry = res.hits.firstOrNull()?.entry
                Screen.Answer(query, res, entry, engine.quotesFor(entry, query))
            }
            _state.update { it.copy(searching = false, stack = it.stack + screen) }
        }
    }

    fun openSuggestion(s: Suggestion) {
        when (s) {
            is Suggestion.TopicSuggestion -> openTopic(s.topic)
            is Suggestion.QuestionSuggestion -> openEntry(s.entry, s.text)
        }
    }

    fun openEntry(entry: Entry, query: String = entry.title) {
        val engine = _state.value.engine ?: return
        suggestJob?.cancel()
        _state.update { it.copy(suggestions = emptyList(), history = store.addHistory(query)) }
        viewModelScope.launch {
            val screen = withContext(Dispatchers.Default) { Screen.Answer(query, null, entry, engine.quotesFor(entry, query)) }
            _state.update { it.copy(stack = it.stack + screen) }
        }
    }

    /** Switch to another of the found answers on the same result screen. */
    fun chooseHit(entry: Entry) {
        val cur = _state.value.screen as? Screen.Answer ?: return
        val engine = _state.value.engine ?: return
        viewModelScope.launch {
            val quotes = withContext(Dispatchers.Default) { engine.quotesFor(entry, cur.query) }
            replaceTop(cur.copy(entry = entry, quotes = quotes, quoteIndex = 0))
        }
    }

    fun nextQuote() {
        val cur = _state.value.screen as? Screen.Answer ?: return
        if (cur.quotes.isEmpty()) return
        replaceTop(cur.copy(quoteIndex = (cur.quoteIndex + 1) % cur.quotes.size))
    }

    fun openQuoteAsAnswer(q: Quote) {
        val engine = _state.value.engine ?: return
        val topic = q.topics.firstOrNull()?.let { engine.data.topicById[it] } ?: return
        openTopic(topic)
    }

    private fun replaceTop(s: Screen) = _state.update { it.copy(stack = it.stack.dropLast(1) + s) }

    fun openTopic(t: Topic) {
        suggestJob?.cancel()
        _state.update { it.copy(suggestions = emptyList(), stack = it.stack + Screen.TopicDetail(t)) }
    }

    fun selectTab(tab: Screen) {
        _state.update { it.copy(stack = listOf(tab), suggestions = if (tab == Screen.Home) it.suggestions else emptyList()) }
    }

    /** @return false when there is nothing to go back to (let the system close the app). */
    fun back(): Boolean {
        val st = _state.value
        return when {
            st.stack.size > 1 -> { _state.update { it.copy(stack = it.stack.dropLast(1)) }; true }
            st.stack.first() != Screen.Home -> { _state.update { it.copy(stack = listOf(Screen.Home)) }; true }
            st.query.isNotEmpty() -> { onQueryChange(""); true }
            else -> false
        }
    }

    fun toggleEntry(id: String) { store.toggleEntry(id); _state.update { it.copy(favEntries = store.favEntries()) } }
    fun toggleQuote(id: String) { store.toggleQuote(id); _state.update { it.copy(favQuotes = store.favQuotes()) } }
    fun removeHistory(item: HistoryItem) = _state.update { it.copy(history = store.removeHistory(item)) }
    fun clearHistory() { store.clearHistory(); _state.update { it.copy(history = emptyList()) } }
}
