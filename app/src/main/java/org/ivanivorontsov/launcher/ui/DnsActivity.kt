package org.ivanivorontsov.launcher.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class DnsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("DNS и защита сайтов", style = MaterialTheme.typography.headlineSmall)
                Text("Private DNS — системная настройка Android. Выберите провайдера ниже, скопируйте имя узла и задайте его в настройках Private DNS. Лаунчер не может включить или запаролить этот системный параметр сам.")
                OutlinedButton(onClick = { copyHost("family.adguard-dns.com") }, modifier = Modifier.fillMaxWidth()) { Text("Скопировать AdGuard Family DNS") }
                OutlinedButton(onClick = { copyHost("dns.adguard-dns.com") }, modifier = Modifier.fillMaxWidth()) { Text("Скопировать AdGuard DNS") }
                Button(onClick = {
                    try { startActivity(Intent(if (Build.VERSION.SDK_INT >= 28) "android.settings.PRIVATE_DNS_SETTINGS" else Settings.ACTION_WIRELESS_SETTINGS)) }
                    catch (_: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
                }, modifier = Modifier.fillMaxWidth()) { Text("Открыть настройки Android") }
            }
        }
    }

    private fun copyHost(host: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Private DNS hostname", host))
        Toast.makeText(this, "Скопировано: $host", Toast.LENGTH_SHORT).show()
    }
}