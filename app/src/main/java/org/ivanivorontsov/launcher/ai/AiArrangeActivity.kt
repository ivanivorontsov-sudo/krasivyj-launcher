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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ivanivorontsov.launcher.LauncherApp
import org.ivanivorontsov.launcher.core.AiLayout
import org.ivanivorontsov.launcher.core.HomeCategories
import org.ivanivorontsov.launcher.core.ScreenLayout
import org.ivanivorontsov.launcher.data.AppInfo
import org.ivanivorontsov.launcher.ui.LauncherTheme
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Lets the user describe a home-screen layout in Russian, gets a structured plan from a
 * signed-in web chat (same engines as the keyboard) or from Pollinations, shows a preview,
 * and applies it with one-step undo. A local category fallback is always available.
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
        var status by remember { mutableStateOf("Опишите, как расставить приложения: папки, порядок, док, категории, страницы.") }
        var busy by remember { mutableStateOf(false) }
        var preview by remember { mutableStateOf<AiLayout.Application?>(null) }
        var showChat by remember { mutableStateOf(false) }
        var menu by remember { mutableStateOf(false) }
        var hasUndo by remember { mutableStateOf(undo.has(themeId)) }
        val apps = remember { catalog(app.apps.apps.value, cfg.usage) }
        val source = ChatEngine.SOURCES[sourceIdx.coerceIn(ChatEngine.SOURCES.indices)]

        fun runLocal(reason: String? = null) {
            val result = AiLayout.localFallback(apps, cfg.layoutFor(themeId), spec.columns.coerceIn(4, 7), spec.columns, spec.rows, prompt)
            preview = result
            status = (reason?.let { "$it\n" } ?: "") + summarize(result)
        }

        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text("Расставить с ИИ", style = MaterialTheme.typography.titleLarge)
            Text(
                "Нейросеть поймёт запрос на русском и предложит раскладку. Вход в чат запоминается на этом телефоне, отдельные ключи API не нужны — так же, как в клавиатуре.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
            )
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                placeholder = { Text("Например: на док — телефон, сообщения, браузер. Соцсети в папку «Общение», игры — в «Игры». Остальное по категориям на втором экране.") },
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
                    TextButton(onClick = { showChat = !showChat }) { Text(if (showChat) "Скрыть чат" else "Показать чат") }
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
            Text(status, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        if (busy) return@Button
                        if (prompt.isBlank() && source.id != "local") {
                            status = "Напишите, как расставить приложения."
                            return@Button
                        }
                        busy = true
                        preview = null
                        status = "Думаю…"
                        scope.launch {
                            try {
                                val result = when (source.id) {
                                    "local" -> AiLayout.localFallback(apps, cfg.layoutFor(themeId), spec.columns.coerceIn(4, 7), spec.columns, spec.rows, prompt)
                                    "pollinations" -> requestPollinations(prompt, apps, spec.columns, spec.rows, cfg.layoutFor(themeId))
                                    else -> requestChat(source, prompt, apps, spec.columns, spec.rows, cfg.layoutFor(themeId))
                                }
                                preview = result
                                status = summarize(result)
                            } catch (e: Exception) {
                                runLocal("Не удалось получить ответ (${e.message ?: e.javaClass.simpleName}). Показала местную раскладку.")
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) { Text(if (source.id == "local") "Составить" else "Спросить") }
                OutlinedButton(onClick = { finish() }, enabled = !busy) { Text("Закрыть") }
            }

            val plan = preview
            if (plan != null) {
                Column(
                    Modifier
                        .weight(1f)
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text("Предпросмотр", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    PreviewBody(plan.layout, apps)
                    if (plan.resolved.unmatched.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Не нашла на телефоне: " + plan.resolved.unmatched.joinToString(", "), fontSize = 13.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Button(onClick = {
                        undo.save(themeId, plan.previous)
                        store.update { it.copy(layouts = it.layouts + (themeId to plan.layout), lastEdited = themeId) }
                        hasUndo = true
                        Toast.makeText(this@AiArrangeActivity, "Раскладка применена. Можно отменить.", Toast.LENGTH_SHORT).show()
                        finish()
                    }, modifier = Modifier.weight(1f)) { Text("Применить") }
                    OutlinedButton(onClick = { preview = null; status = "Предпросмотр отменён." }, modifier = Modifier.weight(1f)) { Text("Отмена") }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (hasUndo) {
                TextButton(onClick = {
                    val previous = undo.consume(themeId) ?: return@TextButton
                    store.update { it.copy(layouts = it.layouts + (themeId to previous), lastEdited = themeId) }
                    hasUndo = false
                    Toast.makeText(this@AiArrangeActivity, "Вернула предыдущую раскладку.", Toast.LENGTH_SHORT).show()
                    finish()
                }) { Text("Отменить последнюю расстановку") }
            }
            if (showChat && source.url != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(12.dp)),
                ) { ChatView(source.url) }
            }
        }
    }

    @Composable
    private fun PreviewBody(layout: ScreenLayout, apps: List<AiLayout.CatalogApp>) {
        val names = apps.associate { it.key to it.label }
        fun label(id: String) = when {
            org.ivanivorontsov.launcher.core.isFolderId(id) -> {
                val f = layout.folders[id]
                "📁 ${f?.name ?: id} (" + (f?.apps?.joinToString { names[it] ?: it } ?: "") + ")"
            }
            org.ivanivorontsov.launcher.core.isWidgetId(id) -> "🧩 виджет"
            else -> names[id] ?: id
        }
        if (layout.dock.isNotEmpty()) {
            Text("Док: " + layout.dock.joinToString { label(it) }, fontSize = 15.sp)
            Spacer(Modifier.height(6.dp))
        }
        val pages = layout.home.chunked(20).ifEmpty { listOf(emptyList()) }
        pages.forEachIndexed { i, items ->
            Text("Страница ${i + 1}", style = MaterialTheme.typography.titleSmall)
            items.forEach { Text("  • " + label(it), fontSize = 14.sp) }
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
                            webReady = true
                            CookieManager.getInstance().flush()
                        }
                    }
                    w.loadUrl(url)
                }
            },
            update = { view ->
                if (view.url == null || (view.url != url && view.url?.startsWith(url) != true && !webReady)) {
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
    ): AiLayout.Application {
        val prompt = AiLayout.buildPrompt(apps, request, columns, rows, columns.coerceIn(4, 7))
        val body = withContext(Dispatchers.IO) { pollinationsChat(prompt) }
        return try {
            AiLayout.applyPlan(current, AiLayout.parse(body), apps, columns.coerceIn(4, 7), columns, rows)
        } catch (e: Exception) {
            AiLayout.localFallback(apps, current, columns.coerceIn(4, 7), columns, rows, request).also {
                statusFallback("Pollinations ответила, но JSON не разобрать (${e.message}).")
            }
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
    ): AiLayout.Application {
        val prompt = AiLayout.buildPrompt(apps, request, columns, rows, columns.coerceIn(4, 7))
        val url = source.url ?: throw IllegalStateException("Нет адреса чата")
        val reply = withContext(Dispatchers.Main) { driveChat(url, prompt) }
        if (reply.isNullOrBlank()) {
            return AiLayout.localFallback(apps, current, columns.coerceIn(4, 7), columns, rows, request)
        }
        return try {
            AiLayout.applyPlan(current, AiLayout.parse(reply), apps, columns.coerceIn(4, 7), columns, rows)
        } catch (_: Exception) {
            AiLayout.localFallback(apps, current, columns.coerceIn(4, 7), columns, rows, request)
        }
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
        if (injected == "noinput") throw IllegalStateException("Не нашла поле ввода. Откройте «Показать чат» и войдите.")
        var sent = false
        repeat(8) {
            delay(700)
            val r = eval(ChatEngine.SUBMIT)
            if (r == "sent" || r == "clicked" || r == "clicked-near" || r == "form") sent = true
            if (r == "sent" || r == "failed") return@repeat
        }
        if (!sent) {
            // Still wait — Enter may have worked without a recognised return value.
        }
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
        return text.ifBlank { null }
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

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWeb(url: String) {
        if (web == null) {
            val w = WebView(this)
            web = w
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
                override fun onPageFinished(view: WebView?, u: String?) { webReady = true; CookieManager.getInstance().flush() }
            }
            w.layoutParams = ViewGroup.LayoutParams(1, 1)
        }
        val w = web!!
        val cur = w.url
        if (cur == null || (cur != url && !cur.startsWith(url))) {
            webReady = false
            w.loadUrl(url)
        }
    }

    private suspend fun eval(js: String): String? {
        val w = web ?: return null
        var waits = 0
        while (!webReady && waits < 25) { delay(200); waits++ }
        var result: String? = null
        var done = false
        w.evaluateJavascript(js) { value -> result = value; done = true }
        var spin = 0
        while (!done && spin < 50) { delay(50); spin++ }
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

    private fun summarize(result: AiLayout.Application): String {
        val r = result.resolved
        val folders = result.layout.folders.size
        val home = result.layout.home.count { !org.ivanivorontsov.launcher.core.isWidgetId(it) }
        val miss = if (r.unmatched.isEmpty()) "" else " Не опознала: ${r.unmatched.joinToString(", ")}."
        return "Предпросмотр: док ${r.dock.size}, на экране $home, папок $folders.$miss Нажмите «Применить» или «Отмена»."
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
