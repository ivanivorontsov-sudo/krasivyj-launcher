package org.ivanivorontsov.mudrost.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.ivanivorontsov.mudrost.BuildConfig

object Disclaimer {
    const val TEXT = "«Мудрость» — сборник советов и цитат для размышления. Приложение не является психологической помощью и не заменяет консультацию специалиста (психолога, психотерапевта, врача)."
    const val CRISIS = "Если вам сейчас тяжело или есть угроза жизни, позвоните 112 (экстренные службы) или 8-800-2000-122 (бесплатный телефон доверия)."
    private const val PREFS = "disclaimer"
    private const val KEY = "accepted_v1"

    fun isAccepted(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun accept(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, true).apply()
    }
}

private val licenseFiles = listOf(
    "Сторонние компоненты" to "THIRD_PARTY_NOTICES.md",
    "rubert-tiny2 — MIT" to "MIT-rubert-tiny2.txt",
    "ONNX Runtime — MIT" to "MIT-onnxruntime.txt",
    "AndroidX, Kotlin, kotlinx — Apache 2.0" to "Apache-2.0.txt",
)

private fun dial(context: Context, number: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }
}

@Composable
fun FirstRunDisclaimer() {
    val context = LocalContext.current
    var show by remember { mutableStateOf(!Disclaimer.isAccepted(context)) }
    if (show) AlertDialog(
        onDismissRequest = {},
        title = { Text("Важно") },
        text = { Text(Disclaimer.TEXT + "\n\n" + Disclaimer.CRISIS) },
        confirmButton = { TextButton(onClick = { Disclaimer.accept(context); show = false }) { Text("Понятно") } },
    )
}

@Composable
fun AboutScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    var license by remember { mutableStateOf<Pair<String, String>?>(null) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            val current = license
            if (current != null) {
                BackHandler { license = null }
                val text = remember(current) {
                    runCatching { context.assets.open("licenses/${current.second}").bufferedReader().use { it.readText() } }
                        .getOrDefault("")
                }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    TextButton(onClick = { license = null }) { Text("← Назад") }
                    Text(current.first, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 8.dp))
                    Text(text, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    TextButton(onClick = onClose) { Text("← Назад") }
                    Text("О приложении", style = MaterialTheme.typography.headlineMedium)
                    Text("Мудрость ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 16.dp))
                    Text(Disclaimer.TEXT, style = MaterialTheme.typography.bodyLarge)
                    Text(Disclaimer.CRISIS, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
                    Button(onClick = { dial(context, "112") }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("Позвонить 112") }
                    OutlinedButton(onClick = { dial(context, "88002000122") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Телефон доверия 8-800-2000-122") }
                    Text("Сторонние компоненты и лицензии", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
                    licenseFiles.forEach { item ->
                        TextButton(onClick = { license = item }, modifier = Modifier.fillMaxWidth()) { Text(item.first, modifier = Modifier.fillMaxWidth()) }
                    }
                    Text("Цитаты принадлежат их авторам и приводятся с указанием автора и источника.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
                }
            }
        }
    }
}
