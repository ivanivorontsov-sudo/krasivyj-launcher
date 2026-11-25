package org.ivanivorontsov.launcher.ai

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ivanivorontsov.launcher.LauncherApp
import org.ivanivorontsov.launcher.core.AiLayout
import org.ivanivorontsov.launcher.core.LauncherConfig
import org.ivanivorontsov.launcher.core.AiSettings
import org.ivanivorontsov.launcher.core.AiPreview
import org.ivanivorontsov.launcher.core.AiComposer
import org.ivanivorontsov.launcher.core.HomeCategories
import org.ivanivorontsov.launcher.core.ScreenLayout
import org.ivanivorontsov.launcher.data.AppInfo
import org.ivanivorontsov.launcher.ui.LauncherTheme
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Lets the user describe a home-screen layout and any launcher settings in Russian, gets a
 * structured plan from a signed-in web chat (same engines as the keyboard) or from Pollinations,
 * shows an opaque confirmation with «было → станет», and applies it with one-step undo.
 * A local parser covers common phrases when the model is unavailable.
 */
class AiArrangeActivity : ComponentActivity() {

    private val undo by lazy { LayoutUndoStore(this) }
    private var web: WebView? = null
    private var webReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        val app = LauncherApp.instance
        val themeId = intent.getStringExtra(EXTRA_THEME) ?: app.store.theme.value.id
        setContent {
            val spec = app.store.theme.value
            LauncherTheme(spec) { Screen(themeId) }
        }
    }

    @Composable
    private fun Screen(themeId: String) {
        val app = LauncherApp.instance
        val store = app.store
        val spec = store.theme.value
        val cfg = store.config.value
        val scope = rememberCoroutineScope()
        var prompt by remember { mutableStateOf("") }
        var sourceIdx by remember { mutableIntStateOf(prefsIdx()) }
        var status by remember { mutableStateOf("Опишите, как расставить приложения и что изменить в настройках: пояса часов, тема, анимация, сетка, док, жесты.") }
        var busy by remember { mutableStateOf(false) }
        var preview by remember { mutableStateOf<AiPreview?>(null) }
        var showChat by remember { mutableStateOf(false) }
        var menu by remember { mutableStateOf(false) }
        var hasUndo by remember { mutableStateOf(undo.hasAny()) }
        val apps = remember { catalog(app.apps.apps.value, cfg.usage) }
        val source = ChatEngine.SOURCES[sourceIdx.coerceIn(ChatEngine.SOURCES.indices)]
        val dark = spec.dark
        val bg = if (dark) Color(0xFF121212) else Color(0xFFF7F7F4)
        val fg = if (dark) Color(0xFFF5F5F5) else Color(0xFF121212)
        val card = if (dark) Color(0xFF1E1E1E) else Color(0xFFFFFFFF)
        val muted = if (dark) Color(0xFFB8B8B8) else Color(0xFF3C3C3C)
        val warn = if (dark) Color(0xFFFFB4A8) else Color(0xFF8B1D1D)

        fun runLocal(reason: String? = null) {
            val result = AiComposer.compose(prompt, apps, cfg.layoutFor(themeId), cfg, spec, modelText = null, maxDock = spec.columns.coerceIn(4, 7), columns = spec.columns, rows = spec.rows)
            preview = result
            status = (reason?.let { "$it\n" } ?: "") + summarize(result)
        }

        fun restorePrevious() {
            if (!undo.restore(store, themeId)) return
            hasUndo = false
            Toast.makeText(this@AiArrangeActivity, "Вернула предыдущую раскладку и настройки.", Toast.LENGTH_SHORT).show()
            finish()
        }

        fun showAllOnHome() {
            val keys = apps.map { it.key }
            store.update { AiLayout.enableAllAppsOnHome(it, themeId, keys) }
            Toast.makeText(this@AiArrangeActivity, "Все приложения снова на главном экране.", Toast.LENGTH_SHORT).show()
            finish()
        }

        if (showChat && source.url != null) {
            Column(
                Modifier.fillMaxSize().background(bg).statusBarsPadding().navigationBarsPadding()
                    .imePadding().padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { if (!busy) showChat = false }, enabled = !busy) { Text("Назад") }
                    Text(source.title, color = fg, style = MaterialTheme.typography.titleMedium)
                    if (busy) LinearProgressIndicator(Modifier.weight(1f).padding(start = 12.dp))
                }
                Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(12.dp))) { ChatView(source.url) }
            }
            return
        }

        if (preview != null) {
            val plan = preview!!
            Column(
                Modifier
                    .fillMaxSize()
                    .background(bg)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text("Подтвердите изменения", style = MaterialTheme.typography.titleLarge, color = fg)
                Text(
                    "Ничего не применяется, пока вы не нажмёте «Применить». Потом можно вернуть всё одним нажатием.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted,
                    modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
                )
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(card)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    ConfirmBody(plan, apps, cfg.layoutFor(themeId), fg, muted, warn, spec.columns, spec.rows)
                }
                if (plan.settings.changes.isEmpty() && plan.arrangement == null && plan.settings.unsupported.isNotEmpty()) {
                    Text("Менять нечего: запрос не поддерживается.", color = warn, modifier = Modifier.padding(top = 8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Button(
                        onClick = {
                            if (!plan.hasWork || (plan.settings.changes.isEmpty() && plan.arrangement == null && plan.settings.widget == null)) {
                                Toast.makeText(this@AiArrangeActivity, "Нечего применять.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            undo.save(themeId, cfg.layoutFor(themeId), cfg, spec)
                            val next = AiComposer.configToApply(plan, themeId, apps.map { it.key })
                            store.update { next }
                            if (plan.settings.themeChanged) store.setTheme(plan.settings.nextTheme)
                            hasUndo = true
                            Toast.makeText(this@AiArrangeActivity, "Готово. Можно вернуть предыдущую раскладку.", Toast.LENGTH_SHORT).show()
                            finish()
                        },
                        enabled = plan.hasWork && (plan.settings.changes.isNotEmpty() || plan.arrangement != null || plan.settings.widget != null),
                        modifier = Modifier.weight(1f),
                    ) { Text("Применить") }
                    OutlinedButton(onClick = { preview = null; status = "Предпросмотр отменён." }, modifier = Modifier.weight(1f)) { Text("Отмена") }
                }
            }
            return
        }

        Column(
            Modifier
                .fillMaxSize()
                .background(bg)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text("Расставить с ИИ", style = MaterialTheme.typography.titleLarge, color = fg)
            Text(
                "Нейросеть поймёт запрос на русском: порядок приложений, папки (только если вы их просите), тема, анимация, сетка, иконки, док, жесты, часовые пояса виджета часов. Вход в чат запоминается на этом телефоне, отдельные ключи API не нужны.",
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
                modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
            )
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                placeholder = { Text("Например: на первое место банки, на второе заказ еды и доставку, на третье всё остальное. Добавь пояса Москвы и Владивостока, часы карточкой.") },
                maxLines = 8,
            )
            Spacer(Modifier.height(8.dp))
            Box {
                OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                    Text(source.title)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    ChatEngine.SOURCES.forEachIndexed { i, s ->
                        DropdownMenuItem(text = { Text(s.title) }, onClick = {
                            sourceIdx = i
                            prefs().edit().putInt("source", i).apply()
                            menu = false
                        })
                    }
                }
            }
            if (source.needsLogin) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    TextButton(onClick = { openLogin(source) }) { Text("Войти в ${source.title.substringBefore(" (")}") }
                    TextButton(onClick = { showChat = true }) { Text("Открыть чат") }
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
            Text(status, style = MaterialTheme.typography.bodyMedium, color = fg, modifier = Modifier.padding(vertical = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        if (busy) return@Button
                        if (prompt.isBlank() && source.id != "local") {
                            status = "Напишите, чего вы хотите."
                            return@Button
                        }
                        busy = true
                        preview = null
                        status = "Думаю…"
                        scope.launch {
                            try {
                                if (source.url != null) {
                                    showChat = true
                                    awaitVisibleChat(source.url)
                                }
                                val result = when (source.id) {
                                    "local" -> AiComposer.compose(prompt, apps, cfg.layoutFor(themeId), cfg, spec, null, spec.columns.coerceIn(4, 7), spec.columns, spec.rows)
                                    "pollinations" -> requestPollinations(prompt, apps, spec.columns, spec.rows, cfg.layoutFor(themeId), cfg, spec)
                                    else -> requestChat(source, prompt, apps, spec.columns, spec.rows, cfg.layoutFor(themeId), cfg, spec)
                                }
                                preview = result
                                status = summarize(result)
                            } catch (e: Exception) {
                                runLocal("Не удалось получить ответ (${e.message ?: e.javaClass.simpleName}). Показала местный разбор.")
                            } finally {
                                busy = false
                                if (source.url != null) showChat = false
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) { Text(if (source.id == "local") "Составить" else "Спросить") }
                OutlinedButton(onClick = { finish() }, enabled = !busy) { Text("Закрыть") }
            }
            Spacer(Modifier.height(8.dp))
            if (hasUndo) {
                Button(onClick = { restorePrevious() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Вернуть предыдущую раскладку")
                }
            }
            OutlinedButton(onClick = { showAllOnHome() }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text("Показать все приложения на главном")
            }
            Spacer(Modifier.weight(1f))
        }
    }

    @Composable
    private fun ConfirmBody(
        plan: AiPreview,
        apps: List<AiLayout.CatalogApp>,
        previous: ScreenLayout,
        fg: Color,
        muted: Color,
        warn: Color,
        columns: Int,
        rows: Int,
    ) {
        if (plan.settings.changes.isNotEmpty()) {
            Text("Настройки", style = MaterialTheme.typography.titleMedium, color = fg)
            Spacer(Modifier.height(6.dp))
            plan.settings.changes.forEach { c ->
                Text("${c.label}: ${c.from} → ${c.to}", fontSize = 16.sp, color = fg, modifier = Modifier.padding(vertical = 3.dp))
            }
            Spacer(Modifier.height(12.dp))
        }
        if (plan.settings.unsupported.isNotEmpty()) {
            Text("Не поддерживается", style = MaterialTheme.typography.titleMedium, color = warn)
            Spacer(Modifier.height(6.dp))
            plan.settings.unsupported.forEach { Text("  • $it", fontSize = 16.sp, color = warn, modifier = Modifier.padding(vertical = 2.dp)) }
            Spacer(Modifier.height(12.dp))
        }
        if (plan.arrangement != null || plan.settings.widget != null) {
            Text("Рабочий стол", style = MaterialTheme.typography.titleMedium, color = fg)
            Spacer(Modifier.height(6.dp))
            PreviewBody(plan.layout, apps, columns, rows, fg, muted)
            val unmatched = plan.arrangement?.resolved?.unmatched.orEmpty()
            if (unmatched.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Не нашла на телефоне: " + unmatched.joinToString(", "), fontSize = 14.sp, color = muted)
            }
            val before = previous.placedApps().toSet()
            val after = plan.layout.placedApps().toSet()
            val gone = before - after
            if (gone.isNotEmpty()) {
                val names = apps.associate { it.key to it.label }
                Text("Пропали бы (этого быть не должно): " + gone.joinToString { names[it] ?: it }, fontSize = 14.sp, color = warn)
            }
        } else if (plan.settings.changes.isEmpty()) {
            Text("Изменений раскладки нет.", color = muted, fontSize = 15.sp)
        }
    }

    @Composable
    private fun PreviewBody(layout: ScreenLayout, apps: List<AiLayout.CatalogApp>, columns: Int, rows: Int, fg: Color, muted: Color) {
        val names = apps.associate { it.key to it.label }
        fun label(id: String) = when {
            org.ivanivorontsov.launcher.core.isFolderId(id) -> {
                val f = layout.folders[id]
                "Папка «${f?.name ?: id}»: " + (f?.apps?.joinToString { names[it] ?: it } ?: "")
            }
            org.ivanivorontsov.launcher.core.isWidgetId(id) -> {
                val w = layout.widgets[id]
                if (id == org.ivanivorontsov.launcher.core.CLOCK_WIDGET) {
                    "Виджет часов${w?.let { " ${it.spanX}×${it.spanY}" } ?: ""}"
                } else "Виджет"
            }
            else -> names[id] ?: id
        }
        if (layout.dock.isNotEmpty()) {
            Text("Док: " + layout.dock.joinToString { label(it) }, fontSize = 16.sp, color = fg)
            Spacer(Modifier.height(8.dp))
        } else {
            Text("Док: без изменений или пуст", fontSize = 15.sp, color = muted)
            Spacer(Modifier.height(8.dp))
        }
        val cap = (columns * rows).coerceAtLeast(1)
        val pages = layout.home.chunked(cap).ifEmpty { listOf(emptyList()) }
        pages.forEachIndexed { i, items ->
            Text("Страница ${i + 1}", style = MaterialTheme.typography.titleSmall, color = fg, modifier = Modifier.padding(top = 4.dp))
            items.forEach { Text("  • " + label(it), fontSize = 15.sp, color = fg, modifier = Modifier.padding(vertical = 1.dp)) }
            Spacer(Modifier.height(6.dp))
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    private fun ChatView(url: String) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                web ?: WebView(ctx).also { w ->
                    web = w
                    webReady = false
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)
                    w.settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        userAgentString = userAgentString.replace("; wv", "").replace(Regex("Version/\\S+ "), "")
                    }
                    w.webChromeClient = WebChromeClient()
                    w.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, u: String?) {
                            if (view === web) {
                                webReady = true
                                CookieManager.getInstance().flush()
                            }
                        }
                    }
                    w.loadUrl(url)
                }
            },
            update = { view ->
                if (view.url == null || (view.url != url && view.url?.startsWith(url) != true)) {
                    webReady = false
                    view.loadUrl(url)
                }
            },
        )
    }

    private fun openLogin(source: ChatEngine.Source) {
        val url = source.url ?: return
        startActivity(Intent(this, AiLoginActivity::class.java).putExtra(AiLoginActivity.EXTRA_URL, url).putExtra(AiLoginActivity.EXTRA_NAME, source.title.substringBefore(" (")))
    }

    private suspend fun requestPollinations(
        request: String,
        apps: List<AiLayout.CatalogApp>,
        columns: Int,
        rows: Int,
        current: ScreenLayout,
        config: LauncherConfig,
        theme: org.ivanivorontsov.launcher.core.ThemeSpec,
    ): AiPreview {
        val prompt = AiLayout.buildPrompt(apps, request, columns, rows, columns.coerceIn(4, 7))
        val body = withContext(Dispatchers.IO) { pollinationsChat(prompt) }
        return try {
            AiComposer.compose(request, apps, current, config, theme, body, columns.coerceIn(4, 7), columns, rows)
        } catch (e: Exception) {
            statusFallback("Pollinations ответила, но JSON не разобрать (${e.message}).")
            AiComposer.compose(request, apps, current, config, theme, null, columns.coerceIn(4, 7), columns, rows)
        }
    }

    private fun statusFallback(msg: String) {
        // The caller overwrites status with summarize(); keep this for logs only.
        android.util.Log.i("AiArrange", msg)
    }

    private suspend fun requestChat(
        source: ChatEngine.Source,
        request: String,
        apps: List<AiLayout.CatalogApp>,
        columns: Int,
        rows: Int,
        current: ScreenLayout,
        config: LauncherConfig,
        theme: org.ivanivorontsov.launcher.core.ThemeSpec,
    ): AiPreview {
        val prompt = AiLayout.buildPrompt(apps, request, columns, rows, columns.coerceIn(4, 7))
        val url = source.url ?: throw IllegalStateException("Нет адреса чата")
        val reply = withContext(Dispatchers.Main) { driveChat(url, prompt) }
        if (reply.isNullOrBlank()) {
            return AiComposer.compose(request, apps, current, config, theme, null, columns.coerceIn(4, 7), columns, rows)
        }
        return AiComposer.compose(request, apps, current, config, theme, reply, columns.coerceIn(4, 7), columns, rows)
    }

    private suspend fun driveChat(url: String, prompt: String): String? {
        ensureWeb(url)
        var last: String? = eval(ChatEngine.STATE)
        var waits = 0
        while (last == "login" && waits < 8) {
            delay(700)
            last = eval(ChatEngine.STATE)
            waits++
        }
        if (last == "login") throw IllegalStateException("Нужно войти в чат (кнопка «Войти»)")
        val injected = eval(ChatEngine.inject(prompt))
        if (injected == "noinput") throw IllegalStateException("Не нашла поле ввода. Откройте чат и войдите в выбранный сервис.")
        var sent = false
        for (attempt in 0 until 8) {
            delay(700)
            val r = eval(ChatEngine.SUBMIT)
            if (r == "sent" || r == "clicked" || r == "clicked-near" || r == "form" || r == "enter") {
                sent = true
                break
            }
            if (r == "failed") break
        }
        if (!sent) throw IllegalStateException("Не удалось отправить сообщение в чат. Проверьте поле ввода и попробуйте ещё раз.")
        var text = ""
        repeat(40) {
            delay(800)
            val raw = eval(ChatEngine.READ_REPLY) ?: return@repeat
            val t = extractText(raw)
            val stable = raw.contains("\"stable\":true")
            if (t.length > text.length) text = t
            if (stable && org.ivanivorontsov.launcher.core.AiLayout.extractJson(text) != null) return text
            if (stable && text.length > 20) return text
        }
        return text.takeIf { it.isNotBlank() } ?: throw IllegalStateException("Чат не прислал ответ за отведённое время.")
    }

    private fun extractText(raw: String): String {
        // eval() may wrap the result in extra quotes; also accept the raw JSON {text:…}.
        val unquoted = raw.trim().trim('"').replace("\\\"", "\"").replace("\\n", "\n")
        val marker = "\"text\":"
        val at = unquoted.indexOf(marker)
        if (at < 0) return unquoted
        val start = unquoted.indexOf('"', at + marker.length)
        if (start < 0) return unquoted
        val sb = StringBuilder()
        var i = start + 1
        while (i < unquoted.length) {
            val ch = unquoted[i]
            if (ch == '"' && unquoted.getOrNull(i - 1) != '\\') break
            sb.append(ch); i++
        }
        return sb.toString().replace("\\n", "\n").replace("\\\"", "\"")
    }

    private suspend fun awaitVisibleChat(url: String) {
        val deadline = System.currentTimeMillis() + 30_000L
        while (System.currentTimeMillis() < deadline) {
            val w = web
            if (w != null && w.isAttachedToWindow && w.width > 100 && w.height > 100) {
                val current = w.url
                if (current == null || (current != url && !current.startsWith(url))) {
                    webReady = false
                    w.loadUrl(url)
                }
                if (webReady) return
            }
            delay(100)
        }
        throw IllegalStateException("Не удалось открыть полноэкранный чат. Проверьте интернет и вход в выбранный сервис.")
    }

    private suspend fun ensureWeb(url: String) {
        awaitVisibleChat(url)
    }

    private suspend fun eval(js: String): String? {
        val w = web ?: return null
        var waits = 0
        while ((!webReady || !w.isAttachedToWindow || w.width <= 0 || w.height <= 0) && waits < 50) { delay(200); waits++ }
        if (!webReady || !w.isAttachedToWindow) throw IllegalStateException("Окно чата ещё не готово.")
        var result: String? = null
        var done = false
        w.evaluateJavascript(js) { value -> result = value; done = true }
        var spin = 0
        while (!done && spin < 100) { delay(50); spin++ }
        if (!done) throw IllegalStateException("Страница чата не ответила.")
        return result?.trim()?.trim('"')?.takeIf { it != "null" && it.isNotEmpty() }
    }

    private fun catalog(apps: List<AppInfo>, usage: Map<String, Int>): List<AiLayout.CatalogApp> {
        val finance = HomeCategories.platformFinanceCategory()
        return apps.map { a ->
            AiLayout.CatalogApp(
                key = a.key,
                label = a.label,
                pkg = a.pkg,
                category = HomeCategories.folderName(a.pkg, a.originalLabel.ifEmpty { a.label }, a.androidCategory, finance),
                usage = usage[a.key] ?: 0,
            )
        }
    }

    private fun summarize(result: AiPreview): String {
        val bits = ArrayList<String>()
        if (result.settings.changes.isNotEmpty()) bits += "настроек ${result.settings.changes.size}"
        if (result.arrangement != null) {
            val r = result.arrangement.resolved
            val folders = result.layout.folders.size
            val home = result.layout.home.count { !org.ivanivorontsov.launcher.core.isWidgetId(it) }
            bits += "док ${r.dock.size}, на экране $home, папок $folders"
            if (r.unmatched.isNotEmpty()) bits += "не опознала: ${r.unmatched.joinToString(", ")}"
        }
        if (result.settings.unsupported.isNotEmpty()) bits += "не поддерживается: ${result.settings.unsupported.size}"
        val head = if (bits.isEmpty()) "Изменений нет" else "Предпросмотр: " + bits.joinToString("; ")
        return "$head. Нажмите «Применить» или «Отмена»."
    }

    private fun prefs() = getSharedPreferences("ai_layout", MODE_PRIVATE)
    private fun prefsIdx() = prefs().getInt("source", 1).coerceIn(ChatEngine.SOURCES.indices)

    override fun onDestroy() {
        web?.let { it.stopLoading(); (it.parent as? ViewGroup)?.removeView(it); it.destroy() }
        web = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_THEME = "themeId"
        fun intent(ctx: Context, themeId: String? = null) =
            Intent(ctx, AiArrangeActivity::class.java).apply { if (themeId != null) putExtra(EXTRA_THEME, themeId) }
    }
}

class AiLoginActivity : ComponentActivity() {
    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL) ?: return finish()
        val name = intent.getStringExtra(EXTRA_NAME) ?: "чат"
        val root = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.VERTICAL }
        val bar = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(24, 24, 16, 8)
        }
        bar.addView(android.widget.TextView(this).apply { text = "Вход в $name: войдите и нажмите «Готово»"; textSize = 16f }, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(android.widget.Button(this).apply { text = "Готово"; isAllCaps = false; setOnClickListener { CookieManager.getInstance().flush(); finish() } })
        root.addView(bar)
        web = WebView(this)
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        web.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true; databaseEnabled = true
            userAgentString = userAgentString.replace("; wv", "").replace(Regex("Version/\\S+ "), "")
        }
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, u: String?) { CookieManager.getInstance().flush() }
        }
        web.loadUrl(url)
        root.addView(web, android.widget.LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    override fun onDestroy() { CookieManager.getInstance().flush(); if (::web.isInitialized) web.destroy(); super.onDestroy() }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_NAME = "name"
    }
}

internal fun pollinationsChat(prompt: String): String {
    val payload = """{"model":"openai","messages":[{"role":"user","content":${ChatEngine.jsonQuote(prompt)}}]}"""
    val conn = (URL(ChatEngine.POLLINATIONS_CHAT).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 20_000
        readTimeout = 60_000
        doOutput = true
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
        setRequestProperty("Accept", "application/json, text/plain")
    }
    try {
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
        if (code !in 200..299) {
            // GET fallback for short prompts.
            val get = (URL(ChatEngine.pollinationsUrl(prompt.take(1200))).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000; readTimeout = 60_000
            }
            return try {
                if (get.responseCode !in 200..299) throw IllegalStateException("Pollinations: HTTP ${get.responseCode}")
                get.inputStream.bufferedReader().use(BufferedReader::readText)
            } finally {
                get.disconnect()
            }
        }
        val content = Regex("\"content\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").find(body)?.groupValues?.get(1)
        return content?.replace("\\n", "\n")?.replace("\\\"", "\"")?.replace("\\\\", "\\") ?: body
    } finally {
        conn.disconnect()
    }
}
