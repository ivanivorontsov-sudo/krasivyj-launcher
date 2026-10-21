package org.ivanivorontsov.mudrost.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.Topic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.ivanivorontsov.mudrost.AppViewModel
import org.ivanivorontsov.mudrost.Screen
import org.ivanivorontsov.mudrost.UiState
import org.ivanivorontsov.mudrost.engine.SearchEngine
import org.ivanivorontsov.mudrost.engine.Suggestion
import org.ivanivorontsov.mudrost.engine.Topic
import java.time.LocalDate

private data class Tab(val screen: Screen, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab(Screen.Home, "Поиск", Icons.Outlined.Search, Icons.Filled.Search),
    Tab(Screen.Topics, "Темы", Icons.Outlined.GridView, Icons.Filled.GridView),
    Tab(Screen.Favorites, "Избранное", Icons.Outlined.Bookmarks, Icons.Filled.Bookmarks),
    Tab(Screen.History, "История", Icons.Outlined.History, Icons.Filled.History),
)

@Composable
fun MudrostApp(vm: AppViewModel, onExit: () -> Unit) {
    val st by vm.state.collectAsStateWithLifecycle()
    BackHandler { if (!vm.back()) onExit() }
    FirstRunDisclaimer()
    Scaffold(bottomBar = {
        NavigationBar {
            tabs.forEach { t ->
                val sel = st.tab == t.screen
                NavigationBarItem(selected = sel, onClick = { vm.selectTab(t.screen) },
                    icon = { Icon(if (sel) t.selectedIcon else t.icon, null) }, label = { Text(t.label) })
            }
        }
    }) { pad ->
        Box(Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding())) {
            val engine = st.engine
            when {
                st.loadError != null -> EmptyState("⚠️", "Не удалось загрузить данные", st.loadError!!)
                engine == null -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(); Spacer(Modifier.size(12.dp)); Text("Загрузка…")
                }
                else -> when (val s = st.screen) {
                    Screen.Home -> HomeScreen(st, engine, vm)
                    Screen.Topics -> TopicsScreen(engine, vm)
                    Screen.Favorites -> FavoritesScreen(st, engine, vm)
                    Screen.History -> HistoryScreen(st, vm)
                    is Screen.Answer -> AnswerScreen(s, st, engine, vm)
                    is Screen.TopicDetail -> TopicScreen(s.topic, st, engine, vm)
                }
            }
        }
    }
}

@Composable
private fun SearchField(st: UiState, vm: AppViewModel) {
    val kb = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = st.query, onValueChange = vm::onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Например: зачем жить?", maxLines = 1) },
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        trailingIcon = { if (st.query.isNotEmpty()) IconButton(onClick = { vm.onQueryChange("") }) { Icon(Icons.Outlined.Close, "Очистить") } },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { kb?.hide(); vm.submit() }),
    )
}

private val examples = listOf("В чём смысл жизни?", "как перестать бояться", "что делать если одиноко", "как простить", "деньги и счастье", "как найти себя")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeScreen(st: UiState, engine: SearchEngine, vm: AppViewModel) {
    val kb = LocalSoftwareKeyboardController.current
    var about by rememberSaveable { mutableStateOf(false) }
    if (about) AboutScreen { about = false }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 48.dp, bottom = 24.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Мудрость", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { about = true }) { Text("О приложении") }
            }
            Text("Задайте вопрос — получите ответ и цитату", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, bottom = 16.dp))
            SearchField(st, vm)
            if (st.searching) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
        }
        if (st.query.isNotBlank() && st.suggestions.isNotEmpty()) {
            item { Spacer(Modifier.size(8.dp)) }
            items(st.suggestions) { s ->
                when (s) {
                    is Suggestion.TopicSuggestion -> QuestionRow("${s.topic.emoji}  ${s.topic.title}", "тема") { kb?.hide(); vm.openSuggestion(s) }
                    is Suggestion.QuestionSuggestion -> QuestionRow(s.text, if (s.text != s.entry.title) s.entry.title else null) { kb?.hide(); vm.openSuggestion(s) }
                }
            }
            item {
                TextButton(onClick = { kb?.hide(); vm.submit() }, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(Icons.Outlined.Search, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp))
                    Text("Искать «${st.query.trim()}»", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        } else {
            item {
                SectionTitle("Цитата дня")
                val q = remember(engine) { engine.quoteOfTheDay(LocalDate.now().toEpochDay()) }
                QuoteCard(q, q.id in st.favQuotes, { vm.toggleQuote(q.id) })
            }
            item {
                SectionTitle("Попробуйте спросить")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    examples.forEach { e -> SuggestionChip(onClick = { vm.onQueryChange(e); vm.submit(e) }, label = { Text(e) }) }
                }
            }
            item {
                SectionTitle("Темы")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(engine.data.topics.take(14)) { t -> SuggestionChip(onClick = { vm.openTopic(t) }, label = { Text("${t.emoji} ${t.title}") }) }
                    item { SuggestionChip(onClick = { vm.selectTab(Screen.Topics) }, label = { Text("Все темы →") }) }
                }
            }
            item {
                Text(Disclaimer.TEXT + " " + Disclaimer.CRISIS, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
            }
            if (!st.semanticReady) item {
                Text("Подготовка смыслового поиска…", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackBar(title: String, vm: AppViewModel) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
private fun AnswerScreen(s: Screen.Answer, st: UiState, engine: SearchEngine, vm: AppViewModel) {
    val entry = s.entry
    val quote = s.quotes.getOrNull(s.quoteIndex)
    Column(Modifier.fillMaxSize()) {
        BackBar(s.query, vm)
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val res = s.result
            if (res?.corrected != null) item {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Spellcheck, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text("Показаны результаты для «${res.corrected}»", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(start = 10.dp))
                    }
                }
            }
            if (entry == null) {
                item { EmptyState("🤔", "Ничего не нашлось", "Попробуйте сформулировать иначе или выберите тему") }
                val topics = res?.topics.orEmpty().ifEmpty { engine.data.topics.take(6) }
                items(topics) { t -> QuestionRow("${t.emoji}  ${t.title}", "тема") { vm.openTopic(t) } }
                return@LazyColumn
            }
            if (res != null && !res.confident) item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Точного совпадения нет — вот самое близкое по смыслу", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
                }
            }
            item {
                AnswerCard(entry, entry.topics.mapNotNull { engine.data.topicById[it] }, entry.id in st.favEntries,
                    { vm.toggleEntry(entry.id) }, vm::openTopic, entryText(entry, quote))
            }
            if (quote != null) item {
                QuoteCard(quote, quote.id in st.favQuotes, { vm.toggleQuote(quote.id) },
                    counter = "${s.quoteIndex + 1}/${s.quotes.size}", onNext = if (s.quotes.size > 1) vm::nextQuote else null)
            }
            val others = res?.hits.orEmpty().map { it.entry }.filter { it.id != entry.id }.take(3)
            if (others.isNotEmpty()) {
                item { SectionTitle("Другие ответы", Modifier.padding(bottom = 0.dp)) }
                items(others, key = { "o" + it.id }) { e -> QuestionRow(e.title) { vm.chooseHit(e) } }
            }
            val related = engine.related(entry).filter { r -> others.none { it.id == r.id } }
            if (related.isNotEmpty()) {
                item { SectionTitle("Похожие вопросы", Modifier.padding(bottom = 0.dp)) }
                items(related, key = { "r" + it.id }) { e -> QuestionRow(e.title) { vm.openEntry(e) } }
            }
        }
    }
}

@Composable
private fun TopicsScreen(engine: SearchEngine, vm: AppViewModel) {
    val counts = remember(engine) { engine.data.topics.associate { it.id to (engine.entriesOf(it.id).size to engine.quotesOf(it.id).size) } }
    Column(Modifier.fillMaxSize()) {
        Text("Темы", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 16.dp, top = 48.dp, bottom = 12.dp))
        LazyVerticalGrid(GridCells.Adaptive(160.dp), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(engine.data.topics, key = { it.id }) { t ->
                val (e, q) = counts[t.id] ?: (0 to 0)
                Card(onClick = { vm.openTopic(t) }, shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(t.emoji, style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.size(6.dp))
                        Text(t.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${plural(e, "вопрос", "вопроса", "вопросов")} · ${plural(q, "цитата", "цитаты", "цитат")}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicScreen(t: Topic, st: UiState, engine: SearchEngine, vm: AppViewModel) {
    val entries = remember(t) { engine.entriesOf(t.id) }
    // Famous quotes first; own ones follow, so the topic page is not dominated by them.
    val quotes = remember(t) { engine.quotesOf(t.id).sortedBy { it.own } }
    Column(Modifier.fillMaxSize()) {
        BackBar("${t.emoji} ${t.title}", vm)
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            item { SectionTitle("Вопросы", Modifier.padding(top = 0.dp)) }
            items(entries, key = { it.id }) { e -> QuestionRow(e.title) { vm.openEntry(e) } }
            if (quotes.isNotEmpty()) item { SectionTitle("Цитаты") }
            items(quotes, key = { it.id }) { q ->
                Box(Modifier.padding(vertical = 6.dp)) { QuoteCard(q, q.id in st.favQuotes, { vm.toggleQuote(q.id) }) }
            }
        }
    }
}

@Composable
private fun FavoritesScreen(st: UiState, engine: SearchEngine, vm: AppViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val entries = st.favEntries.mapNotNull { engine.data.entryById[it] }
    val quotes = st.favQuotes.mapNotNull { engine.data.quoteById[it] }
    Column(Modifier.fillMaxSize()) {
        Text("Избранное", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 16.dp, top = 48.dp, bottom = 8.dp))
        TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
            Tab(tab == 0, { tab = 0 }, text = { Text("Ответы (${entries.size})") })
            Tab(tab == 1, { tab = 1 }, text = { Text("Цитаты (${quotes.size})") })
        }
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            if (tab == 0) {
                if (entries.isEmpty()) item { EmptyState("🔖", "Пока пусто", "Нажмите на закладку у ответа, чтобы сохранить его здесь") }
                items(entries, key = { it.id }) { e ->
                    QuestionRow(e.title, e.topics.mapNotNull { engine.data.topicById[it]?.title }.joinToString(" · ")) { vm.openEntry(e) }
                }
            } else {
                if (quotes.isEmpty()) item { EmptyState("💬", "Пока пусто", "Сохраняйте понравившиеся цитаты — они появятся здесь") }
                items(quotes, key = { it.id }) { q ->
                    Box(Modifier.padding(vertical = 6.dp)) { QuoteCard(q, true, { vm.toggleQuote(q.id) }) }
                }
            }
        }
    }
}

@Composable
private fun HistoryScreen(st: UiState, vm: AppViewModel) {
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 48.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("История", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            if (st.history.isNotEmpty()) TextButton(onClick = { confirm = true }) { Text("Очистить") }
        }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            if (st.history.isEmpty()) item { EmptyState("🕰️", "История пуста", "Здесь появятся ваши вопросы") }
            items(st.history, key = { "${it.time}|${it.query}" }) { h ->
                Surface(onClick = { vm.onQueryChange(h.query); vm.submit(h.query) }, shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(h.query, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(formatTime(h.time), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { vm.removeHistory(h) }) { Icon(Icons.Outlined.DeleteOutline, "Удалить") }
                    }
                }
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Очистить историю?") },
        text = { Text("Все сохранённые запросы будут удалены с устройства.") },
        confirmButton = { TextButton(onClick = { vm.clearHistory(); confirm = false }) { Text("Очистить") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Отмена") } },
    )
}
