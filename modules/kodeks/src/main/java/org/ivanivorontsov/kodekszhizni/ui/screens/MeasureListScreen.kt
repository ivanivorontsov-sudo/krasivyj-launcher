package org.ivanivorontsov.kodekszhizni.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.ivanivorontsov.kodekszhizni.data.Measure
import org.ivanivorontsov.kodekszhizni.data.MeasureStatus
import org.ivanivorontsov.kodekszhizni.data.MeasureType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasureListScreen(vm: MeasureViewModel) {
    val all by vm.measures.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var filter by remember { mutableIntStateOf(0) } // 0 all, 1 active, 2 done

    val filtered = when (filter) {
        1 -> all.filter { it.status == MeasureStatus.ACTIVE }
        2 -> all.filter { it.status != MeasureStatus.ACTIVE }
        else -> all
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Меры пресечения") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Default.Add, "Новая мера")
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                FilterChip(selected = filter == 0, onClick = { filter = 0 }, label = { Text("Все") })
                FilterChip(selected = filter == 1, onClick = { filter = 1 }, label = { Text("Активные") })
                FilterChip(selected = filter == 2, onClick = { filter = 2 }, label = { Text("Завершённые") })
            }
            LazyColumn(
                contentPadding = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { m ->
                    MeasureCard(
                        measure = m,
                        onComplete = { vm.stop(m.id, completed = true) },
                        onStop = { vm.stop(m.id, completed = false) },
                        onDelete = { vm.delete(m.id) }
                    )
                }
            }
        }
    }

    if (showCreate) {
        CreateMeasureDialog(
            onDismiss = { showCreate = false },
            onCreate = { type, title, desc, hours, daysUntil ->
                val endAbs = daysUntil?.let {
                    System.currentTimeMillis() + TimeUnit.DAYS.toMillis(it)
                }
                vm.start(type, title, desc, hours, endAbs)
                showCreate = false
            }
        )
    }
}

@Composable
private fun MeasureCard(
    measure: Measure,
    onComplete: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit
) {
    val fmt = remember { SimpleDateFormat("d MMM, HH:mm", Locale("ru")) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(typeLabel(measure.type)) })
                AssistChip(onClick = {}, label = { Text(statusLabel(measure.status)) })
            }
            Text(measure.title, style = MaterialTheme.typography.titleMedium)
            if (measure.description.isNotBlank()) {
                Text(measure.description, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                buildString {
                    append("Старт: ${fmt.format(Date(measure.startedAt))}")
                    measure.endAt?.let { append(" · до ${fmt.format(Date(it))}") }
                    if (measure.durationHours > 0) append(" · ${measure.durationHours} ч")
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            if (measure.status == MeasureStatus.ACTIVE) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onComplete) { Text("Выполнено") }
                    TextButton(onClick = onStop) { Text("Остановить") }
                }
            } else {
                TextButton(onClick = onDelete) { Text("Удалить") }
            }
        }
    }
}

@Composable
private fun CreateMeasureDialog(
    onDismiss: () -> Unit,
    onCreate: (MeasureType, String, String, Long, Long?) -> Unit
) {
    var type by remember { mutableStateOf(MeasureType.DIGITAL_DETOX) }
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var hoursText by remember { mutableStateOf("24") }
    var daysText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая мера") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    MeasureType.entries.forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = {
                                type = t
                                if (title.isBlank()) title = typeLabel(t)
                            },
                            label = { Text(typeLabel(t), style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Описание") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = hoursText,
                    onValueChange = { hoursText = it.filter { c -> c.isDigit() } },
                    label = { Text("Длительность (часы)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = daysText,
                    onValueChange = { daysText = it.filter { c -> c.isDigit() } },
                    label = { Text("Или до даты через N дней") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val hours = hoursText.toLongOrNull() ?: 0L
                val days = daysText.toLongOrNull()
                onCreate(type, title, desc, hours, days)
            }) { Text("Старт") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

private fun typeLabel(t: MeasureType) = when (t) {
    MeasureType.DIGITAL_DETOX -> "Детокс"
    MeasureType.EARLY_WAKE -> "Ранний подъём"
    MeasureType.NO_SOCIAL -> "Без соцсетей"
    MeasureType.CUSTOM -> "Своя"
}

private fun statusLabel(s: MeasureStatus) = when (s) {
    MeasureStatus.ACTIVE -> "Активна"
    MeasureStatus.COMPLETED -> "Выполнена"
    MeasureStatus.STOPPED -> "Остановлена"
}
