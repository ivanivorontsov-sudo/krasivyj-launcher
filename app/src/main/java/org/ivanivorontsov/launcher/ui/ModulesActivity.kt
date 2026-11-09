package org.ivanivorontsov.launcher.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

/** One built-in module of СуперСмарт (a former standalone app now living inside the launcher). */
data class SuperModule(val title: String, val about: String, val activity: String?, val externalPkg: String? = null, val license: String? = null)

object SuperModules {
    val all = listOf(
        SuperModule("Мудрость", "Офлайн-ответы на жизненные вопросы и цитаты", "org.ivanivorontsov.mudrost.MainActivity"),
        SuperModule("Кодекс жизни", "Статьи-нормы, поиск, меры пресечения, напоминания", "org.ivanivorontsov.kodekszhizni.MainActivity"),
        SuperModule("Боец поневоле", "Тренировки, самооборона, напоминания-звонки", "org.ivanivorontsov.boec.ui.MainActivity"),
        SuperModule("Читалка", if (org.ivanivorontsov.launcher.BuildConfig.HAS_BOOK) "EPUB и FB2, ваша книга уже внутри" else "EPUB и FB2", "ru.vorontsov.reader.MainActivity"),
        SuperModule("Заметки", "Простые быстрые заметки", "ru.zametki.notes.MainActivity"),
        SuperModule("Тихо", "Приватный браузер на GeckoView — открывается как отдельное приложение", null, "org.ivanivorontsov.tiho", "GNU GPL v3"),
    ).filter { !org.ivanivorontsov.launcher.BuildConfig.PLAY || it.externalPkg == null }
     .filter { it.externalPkg != null || (org.ivanivorontsov.launcher.BuildConfig.HAS_MODULES && (it.title != "Мудрость" || org.ivanivorontsov.launcher.BuildConfig.HAS_MUDROST)) }

    fun open(ctx: Context, m: SuperModule) {
        if (m.activity != null) {
            ctx.startActivity(Intent().setComponent(ComponentName(ctx.packageName, m.activity)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val pkg = m.externalPkg ?: return
        val launch = ctx.packageManager.getLaunchIntentForPackage(pkg)
        if (launch != null) ctx.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        else {
            Toast.makeText(ctx, "«${m.title}» не установлен — откроется страница загрузки", Toast.LENGTH_LONG).show()
            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ivanivorontsov-sudo/tiho-browser/releases")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}

/** «Модули» hub: every built-in section of СуперСмарт in one place. Also listed in the app drawer. */
class ModulesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { Hub() }
            }
        }
    }
}

@Composable
private fun Hub() {
    val ctx = LocalContext.current
    LazyColumn(
        Modifier.fillMaxSize().systemBarsPadding().displayCutoutPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("СуперСмарт · Модули", style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Встроенные приложения. Их значки есть и в меню приложений — оттуда их можно перетащить на главный экран или в папку.",
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
        }
        items(SuperModules.all) { m -> ModuleCard(m) { SuperModules.open(ctx, m) } }
        if (!org.ivanivorontsov.launcher.BuildConfig.PLAY) item {
            Text("Тихо распространяется по лицензии GNU GPL v3; исходный код: github.com/ivanivorontsov-sudo/tiho-browser. Стоп-экран встроен в сам лаунчер и здесь не дублируется.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun ModuleCard(m: SuperModule, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val icon = remember(m) {
        runCatching {
            val d = if (m.activity != null) ctx.packageManager.getActivityIcon(ComponentName(ctx.packageName, m.activity))
                    else ctx.packageManager.getApplicationIcon(m.externalPkg!!)
            d.toBitmap(144, 144).asImageBitmap()
        }.getOrNull()
    }
    ElevatedCard(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Image(icon, null, Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)))
            else Box(Modifier.size(48.dp))
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(m.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(m.about, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
