package org.ivanivorontsov.kodekszhizni.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel) {
    val s by vm.settings.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Настройки") }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Уведомления используют местное время устройства. Значения по умолчанию (8:00, 13:00, 20:00) удобны и для Asia/Vladivostok.",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Ежедневные советы (3× в день)")
                Switch(checked = s.enabled, onCheckedChange = vm::setEnabled)
            }
            TimeRow("Утро", s.morningHour, s.morningMinute) { h, m -> vm.setMorning(h, m) }
            TimeRow("День", s.middayHour, s.middayMinute) { h, m -> vm.setMidday(h, m) }
            TimeRow("Вечер", s.eveningHour, s.eveningMinute) { h, m -> vm.setEvening(h, m) }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Ежемесячный пересмотр")
                    Text(
                        "«Пересмотри нормы и меры»",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Switch(checked = s.monthlyReviewEnabled, onCheckedChange = vm::setMonthly)
            }
            Button(onClick = vm::sendTestTip, modifier = Modifier.fillMaxWidth()) {
                Text("Тестовое уведомление")
            }
            Text(
                "Кодекс жизни · org.ivanivorontsov.kodekszhizni",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun TimeRow(label: String, hour: Int, minute: Int, onChange: (Int, Int) -> Unit) {
    var hText by remember(hour) { mutableStateOf(hour.toString()) }
    var mText by remember(minute) { mutableStateOf("%02d".format(minute)) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = hText,
            onValueChange = {
                hText = it.filter { c -> c.isDigit() }.take(2)
                val h = hText.toIntOrNull()
                val m = mText.toIntOrNull() ?: minute
                if (h != null && h in 0..23) onChange(h, m.coerceIn(0, 59))
            },
            label = { Text("ч") },
            modifier = Modifier.weight(0.7f),
            singleLine = true
        )
        OutlinedTextField(
            value = mText,
            onValueChange = {
                mText = it.filter { c -> c.isDigit() }.take(2)
                val m = mText.toIntOrNull()
                val h = hText.toIntOrNull() ?: hour
                if (m != null && m in 0..59) onChange(h.coerceIn(0, 23), m)
            },
            label = { Text("мин") },
            modifier = Modifier.weight(0.7f),
            singleLine = true
        )
    }
}
