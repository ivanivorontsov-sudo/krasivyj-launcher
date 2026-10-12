package org.ivanivorontsov.kodekszhizni.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.ivanivorontsov.kodekszhizni.data.Article
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleEditScreen(
    articleId: Long,
    vm: ArticleViewModel,
    onBack: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var createdAt by remember { mutableStateOf(System.currentTimeMillis()) }
    var isSeed by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(articleId == 0L) }
    var confirmDelete by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("d MMM yyyy, HH:mm", Locale("ru")) }

    LaunchedEffect(articleId) {
        if (articleId != 0L) {
            vm.load(articleId)?.let { a ->
                title = a.title
                body = a.body
                tags = a.tags
                category = a.category
                createdAt = a.createdAt
                isSeed = a.isSeed
            }
        }
        loaded = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (articleId == 0L) "Новая статья" else "Статья")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                actions = {
                    if (articleId != 0L) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, "Удалить")
                        }
                    }
                    IconButton(onClick = {
                        val now = System.currentTimeMillis()
                        vm.save(
                            Article(
                                id = articleId,
                                title = title.trim().ifBlank { "Без названия" },
                                body = body.trim(),
                                tags = tags.trim(),
                                category = category.trim(),
                                createdAt = if (articleId == 0L) now else createdAt,
                                updatedAt = now,
                                isSeed = isSeed
                            )
                        ) { onBack() }
                    }) {
                        Icon(Icons.Default.Save, "Сохранить")
                    }
                }
            )
        }
    ) { padding ->
        if (!loaded) return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Заголовок") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Категория") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Теги") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("сон,фокус") }
                )
            }
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Текст (Markdown)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                minLines = 12
            )
            if (articleId != 0L) {
                Text(
                    "Создано: ${fmt.format(Date(createdAt))}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Text(
                "Подсказка: используйте ## заголовки и **жирный** для структуры.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить статью?") },
            text = { Text("Действие необратимо.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(articleId, onBack)
                }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Отмена") }
            }
        )
    }
}
