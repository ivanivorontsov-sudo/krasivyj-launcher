package org.ivanivorontsov.kodekszhizni.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.ivanivorontsov.kodekszhizni.data.Article
import org.ivanivorontsov.kodekszhizni.data.Repository

@OptIn(ExperimentalCoroutinesApi::class)
class ArticleViewModel(private val repo: Repository) : ViewModel() {
    private val query = MutableStateFlow("")
    val searchQuery: StateFlow<String> = query

    val articles = query
        .flatMapLatest { repo.articles(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(q: String) {
        query.value = q
    }

    suspend fun load(id: Long): Article? =
        if (id == 0L) null else repo.getArticle(id)

    fun save(article: Article, onDone: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repo.saveArticle(article)
            onDone(id)
        }
    }

    fun delete(id: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.deleteArticle(id)
            onDone()
        }
    }

    companion object {
        fun factory(repo: Repository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ArticleViewModel(repo) as T
        }
    }
}
