package org.ivanivorontsov.launcher.stop

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import org.ivanivorontsov.launcher.LauncherApp
import org.ivanivorontsov.launcher.service.LockService
import org.ivanivorontsov.launcher.stop.core.Backup
import org.ivanivorontsov.launcher.stop.core.ExitChoice
import org.ivanivorontsov.launcher.stop.core.HistoryEntry
import org.ivanivorontsov.launcher.stop.core.Passcode
import org.ivanivorontsov.launcher.stop.core.Settings
import org.ivanivorontsov.launcher.stop.core.SettingsPolicy
import org.ivanivorontsov.launcher.ui.LauncherTheme
import org.ivanivorontsov.launcher.ui.collectAsStateCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * «Стоп-экран» settings inside Уют. Protected by the same password as ScreenLimit (if one is set):
 * the whole section — and leaving a pause — needs it. Themes, fonts, effects stay freely editable elsewhere.
 */
class StopActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        setContent {
            val spec by LauncherApp.instance.store.theme.collectAsStateCompat()
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !spec.dark
            LauncherTheme(spec) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
                    StopRoot { finish() }
                }
            }
        }
    }

    override fun onResume() { super.onResume(); tick++ ; StopGuard.tick() }
    /** Leaving the screen locks it again (the password is asked next time). */
    override fun onStop() { super.onStop(); if (!isChangingConfigurations) unlockedUntil = 0L }

    companion object {
        var tick by mutableIntStateOf(0)
        @Volatile var unlockedUntil = 0L
    }
}

private enum class Page(val title: String) { MAIN("Стоп-экран"), BLOCKED("Запрещённые приложения"), ALLOWED("Разрешено во время паузы"), HISTORY("История") }

@Composable
private fun StopRoot(close: () -> Unit) {
    val store = StopGuard.store
    var unlocked by remember { mutableStateOf(!store.hasPassword || SystemClock.elapsedRealtime() < StopActivity.unlockedUntil) }
    var showPasswordPrompt by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(Page.MAIN) }
    val resumeTick = StopActivity.tick
    LaunchedEffect(resumeTick) {
        if (store.hasPassword && SystemClock.elapsedRealtime() >= StopActivity.unlockedUntil) {
            unlocked = false
            showPasswordPrompt = false
        }
    }
    BackHandler {
        if (!unlocked && showPasswordPrompt) showPasswordPrompt = false
        else if (page == Page.MAIN || !unlocked) close()
        else page = Page.MAIN
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", fontSize = 34.sp, modifier = Modifier.clickable {
                if (!unlocked && showPasswordPrompt) showPasswordPrompt = false
                else if (page == Page.MAIN || !unlocked) close()
                else page = Page.MAIN
            }.padding(horizontal = 14.dp))
            Text(page.title, style = MaterialTheme.typography.headlineSmall)
        }
        if (!unlocked) {
            if (showPasswordPrompt) PasswordGate {
                StopActivity.unlockedUntil = Long.MAX_VALUE
                unlocked = true
                showPasswordPrompt = false
            } else LockedSettingsGate { showPasswordPrompt = true }
        } else when (page) {
            Page.MAIN -> MainPage { page = it }
            Page.BLOCKED -> AppChecklist(
                info = "Отмеченные приложения исчезают с главного экрана, из меню, поиска, папок и дока. Если открыть их иначе (уведомление, «Недавние», другое приложение), Уют сразу вернёт на главный экран.",
                selected = { StopGuard.store.blocked }, protectedPkgs = StopGuard.protectedPackages(),
            ) { pkg, on ->
                StopGuard.update { blocked = if (on) blocked + pkg else blocked - pkg }
            }
            Page.ALLOWED -> AppChecklist(
                info = "Во время паузы (лимит исчерпан или по расписанию) остаются только эти приложения, телефон и SMS. Темы и оформление не меняются.",
                selected = { StopGuard.store.settings.whitelist }, protectedPkgs = emptySet(),
            ) { pkg, on ->
                val old = StopGuard.store.settings
                val next = old.copy(whitelist = if (on) old.whitelist + pkg else old.whitelist - pkg)
                StopGuard.update { settings = next }
            }
            Page.HISTORY -> HistoryPage()
        }
    }
}

@Composable
private fun LockedSettingsGate(onUnlock: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Настройки «Стоп-экрана» заблокированы.", style = MaterialTheme.typography.bodyLarge)
        Text("Нажмите кнопку, когда захотите изменить настройки. Пароль потребуется ввести только один раз за этот сеанс.", style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onUnlock) { Text("Снять блокировку") }
    }
}

@Composable
private fun PasswordGate(onOk: () -> Unit) {
    val store = StopGuard.store
    var pw by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var recovery by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (recovery) "Введите код восстановления — пароль будет удалён." else "Введите пароль, чтобы снять блокировку настроек.", style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(pw, { pw = it }, singleLine = true, label = { Text(if (recovery) "Код восстановления" else "Пароль") },
            visualTransformation = if (recovery) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = if (recovery) KeyboardType.Ascii else KeyboardType.Password), modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            val r = if (recovery) store.recover(pw) else store.check(pw)
            when (r) {
                StopStore.Check.Ok -> { if (recovery) store.log(HistoryEntry.SETTINGS, "Пароль сброшен кодом восстановления"); onOk() }
                StopStore.Check.Wrong -> msg = "Неверно"
                is StopStore.Check.Wait -> msg = "Слишком много попыток. Подождите ${(r.ms + 999) / 1000} с"
            }
            pw = ""
        }) { Text("Открыть") }
        if (msg.isNotEmpty()) Text(msg, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = { recovery = !recovery; msg = "" }) { Text(if (recovery) "Ввести пароль" else "Забыли пароль? Код восстановления") }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 6.dp))
        content()
    }
}

@Composable
private fun Info(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), modifier = Modifier.padding(vertical = 4.dp))

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked, onChange)
    }
}

private fun hm(min: Int) = "%02d:%02d".format(min / 60, min % 60)

@Composable
private fun MainPage(go: (Page) -> Unit) {
    val ctx = LocalContext.current as ComponentActivity
    val store = StopGuard.store
    val view by StopGuard.view.collectAsStateCompat()
    val tick = StopActivity.tick
    var ver by remember { mutableIntStateOf(0) }
    val s = remember(ver, view) { store.settings }
    val a11y = remember(tick) { LockService.isEnabled }
    val adminActive = remember(tick) {
        val manager = ctx.getSystemService(android.app.admin.DevicePolicyManager::class.java)
        manager?.isAdminActive(android.content.ComponentName(ctx, StopAdminReceiver::class.java)) == true
    }
    var recoveryCode by remember { mutableStateOf<String?>(null) }
    var setPw by remember { mutableStateOf(false) }
    fun upd(f: StopStore.() -> Unit) { StopGuard.update(f); ver++ }
    fun requestWeakening(title: String, action: () -> Unit) { action() }
    fun changeSettings(next: Settings, title: String) { upd { settings = next } }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val r = runCatching {
            val text = ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            val parsed = Backup.parse(text, store.settings)
            parsed.settings?.let { ns -> upd { settings = ns } }
            store.mergeHistory(parsed.history)
            store.log(HistoryEntry.IMPORT, "Настройки перенесены из «Стоп-экран»")
            "Перенесено: лимит ${parsed.settings?.limitMinutes ?: "—"} мин, расписание ${parsed.settings?.let { hm(it.scheduleStartMin) + "–" + hm(it.scheduleEndMin) } ?: "—"}, разрешённых приложений ${parsed.settings?.whitelist?.size ?: 0}"
        }.getOrElse { "Не удалось прочитать файл: ${it.message}" }
        Toast.makeText(ctx, r, Toast.LENGTH_LONG).show()
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item { Section("Режим") {
            SwitchRow("Стоп-экран включён", view.enabled) { v ->
                val apply = { upd { enabled = v; log(HistoryEntry.SETTINGS, if (v) "Режим включён" else "Режим выключен") } }
                if (v) apply() else requestWeakening("Выключить «Стоп-экран»?", apply)
            }
            Info(when {
                !view.enabled -> "Выключен: все приложения на месте."
                view.paused -> "Пауза" + (if (view.pauseUntil > 0) " до " + SimpleDateFormat("HH:mm", Locale("ru")).format(Date(view.pauseUntil)) else "") + ". На экране только разрешённые приложения, телефон и SMS."
                view.remainingMs != null -> "Сегодня за экраном: ${StopGuard.fmt(view.usedMs)}, осталось ${StopGuard.fmt(view.remainingMs!!)}. Скрыто приложений: ${view.hidden.size}."
                else -> "Скрыто приложений: ${view.hidden.size}. Лимит и расписание выключены."
            })
            Info("Все темы, шрифты, эффекты и расстановка работают как обычно — просто запрещённых приложений нет ни на экране, ни в меню, ни в поиске.")
        } }
        if (view.paused) item { Section("Выйти из паузы") {
            Info("После подтверждения доступ вернётся на выбранное время. Каждый выход записывается в историю.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 60).forEach { m -> OutlinedButton(onClick = { StopGuard.grantExit(ExitChoice.Extra(m * 60_000L)); ver++ }) { Text("+$m мин") } }
            }
            OutlinedButton(onClick = { StopGuard.grantExit(ExitChoice.RestOfDay); ver++ }) { Text("До конца суток") }
        } }
        item { Section("Приложения") {
            Row(Modifier.fillMaxWidth().clickable { go(Page.BLOCKED) }.padding(vertical = 10.dp)) { Text("Запрещённые приложения: ${store.blocked.size}", style = MaterialTheme.typography.bodyLarge) }
            Row(Modifier.fillMaxWidth().clickable { go(Page.ALLOWED) }.padding(vertical = 10.dp)) { Text("Разрешено во время паузы: ${s.whitelist.size}", style = MaterialTheme.typography.bodyLarge) }
        } }
        item { Section("Дневной лимит") {
            SwitchRow("Лимит экранного времени", s.limitEnabled) { v ->
                changeSettings(s.copy(limitEnabled = v), if (v) "Включить лимит" else "Отключить дневной лимит")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { changeSettings(s.copy(limitMinutes = (s.limitMinutes - 15).coerceAtLeast(15)), "Уменьшить дневной лимит") }) { Text("−15") }
                Text(StopGuard.fmt(s.limitMinutes * 60_000L), Modifier.width(110.dp).padding(horizontal = 12.dp), style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { changeSettings(s.copy(limitMinutes = (s.limitMinutes + 15).coerceAtMost(24 * 60)), "Увеличить дневной лимит") }) { Text("+15") }
            }
            Info("Считается время, когда экран включён и разблокирован; звонки не считаются. Сброс в полночь.")
        } }
        item { Section("Пауза по расписанию") {
            SwitchRow("Расписание", s.scheduleEnabled) { v ->
                changeSettings(s.copy(scheduleEnabled = v), if (v) "Включить расписание" else "Отключить паузу по расписанию")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { android.app.TimePickerDialog(ctx, { _, h, m -> changeSettings(store.settings.copy(scheduleStartMin = h * 60 + m), "Изменить время начала паузы") }, s.scheduleStartMin / 60, s.scheduleStartMin % 60, true).show() }) { Text("с ${hm(s.scheduleStartMin)}") }
                OutlinedButton(onClick = { android.app.TimePickerDialog(ctx, { _, h, m -> changeSettings(store.settings.copy(scheduleEndMin = h * 60 + m), "Изменить время окончания паузы") }, s.scheduleEndMin / 60, s.scheduleEndMin % 60, true).show() }) { Text("до ${hm(s.scheduleEndMin)}") }
            }
        } }
        item { Section("Защита") {
            SwitchRow("Перехват запрещённых приложений", a11y) {
                LockService.grantUntil = SystemClock.uptimeMillis() + 120_000L
                runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            }
            Info(if (a11y) "Служба спец. возможностей Уюта включена ✓ — запрещённое приложение, открытое из уведомления или «Недавних», сразу закрывается."
                 else "Без службы специальных возможностей запрещённые приложения только скрыты: перехват открытия не работает.")
            Button(onClick = {
                LockService.grantUntil = SystemClock.uptimeMillis() + 120_000L
                runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            }) { Text("Настроить специальные возможности") }
            OutlinedButton(onClick = {
                LockService.grantUntil = SystemClock.uptimeMillis() + 120_000L
                runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${ctx.packageName}"))) }
            }) { Text("О приложении · разрешить ограниченные настройки") }
            OutlinedButton(onClick = {
                LockService.grantUntil = SystemClock.uptimeMillis() + 120_000L
                runCatching { ctx.startActivity(Intent("android.settings.HOME_SETTINGS")) }
            }) { Text("Выбрать лаунчер по умолчанию") }
            Info(if (adminActive) "Администратор устройства включён ✓. Перед удалением Уюта Android попросит сначала отключить администратора."
                 else "Администратор устройства добавляет системное подтверждение перед удалением Уюта. Это дополнительный барьер, а не замена пароля и строгого режима.")
            if (!adminActive) {
                Button(onClick = {
                    val component = android.content.ComponentName(ctx, StopAdminReceiver::class.java)
                    val request = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                        .putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
                        .putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "Для снятия защиты «Стоп-экрана» сначала потребуется отключить администратора устройства в настройках Android.")
                    runCatching { ctx.startActivity(request) }
                        .onFailure { Toast.makeText(ctx, "Не удалось открыть активацию администратора", Toast.LENGTH_LONG).show() }
                }) { Text("Включить администратора устройства") }
            } else {
                OutlinedButton(onClick = {
                    runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)) }
                        .onFailure { Toast.makeText(ctx, "Откройте настройки безопасности Android вручную", Toast.LENGTH_LONG).show() }
                }) { Text("Управление администратором устройства") }
            }
            SwitchRow("Строгий режим", s.strictMode) { v ->
                changeSettings(s.copy(strictMode = v), if (v) "Включить строгий режим" else "Отключить строгий режим")
            }
            Info("Закрывает страницы настроек, через которые можно отключить защиту: «О приложении» Уюта, спец. возможности, выбор лаунчера по умолчанию, «Для разработчиков».")
            Info("Уют должен быть лаунчером по умолчанию: тогда «Домой» всегда ведёт в Уют и никаких окон блокировки не появляется.")
        } }
        item { Section("Пароль") {
            Info(if (store.hasPassword) "🔑 Пароль задан: без него этот раздел и выход из паузы не открыть." else "Пароль не задан — раздел открыт всем. Пусть пароль задаст другой человек.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { setPw = true }) { Text(if (store.hasPassword) "Сменить пароль" else "Задать пароль") }
                if (store.hasPassword) OutlinedButton(onClick = {
                    requestWeakening("Удалить пароль защиты?") { upd { clearPassword(); log(HistoryEntry.SETTINGS, "Пароль удалён") } }
                }) { Text("Убрать") }
            }
        } }
        item { Section("Перенос из приложения «Стоп-экран»") {
            Info("В старом «Стоп-экран»: Экспорт → сохраните JSON. Здесь: «Импорт» — перенесутся лимит, расписание, разрешённые приложения, строгий режим и история. Пароль переносится вручную: задайте его заново.")
            OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Импорт JSON") }
        } }
        item { Section("Предупреждения") {
            SwitchRow("Предупреждать о скором окончании лимита", s.warningsEnabled) { v ->
                changeSettings(s.copy(warningsEnabled = v), if (v) "Включить предупреждения" else "Отключить предупреждения")
            }
            SwitchRow("Финальное предупреждение перед паузой", s.finalWarning) { v ->
                changeSettings(s.copy(finalWarning = v), if (v) "Включить финальное предупреждение" else "Отключить финальное предупреждение")
            }
            Info("Предупреждения показываются перед автоматической паузой. Их отключение считается ослаблением защиты.")
        } }
        item { Section("История") { Row(Modifier.fillMaxWidth().clickable { go(Page.HISTORY) }.padding(vertical = 10.dp)) { Text("Открыть историю (${store.history.size})") } } }
    }

    if (setPw) PasswordDialog(onDismiss = { setPw = false }) { pw ->
        setPw = false
        recoveryCode = store.setPassword(pw); store.log(HistoryEntry.SETTINGS, "Пароль задан"); ver++
    }
    recoveryCode?.let { code ->
        AlertDialog(onDismissRequest = {}, confirmButton = { TextButton(onClick = { recoveryCode = null }) { Text("Записал") } },
            title = { Text("Код восстановления") },
            text = { Column { Text(code, style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.padding(4.dp)); Text("Покажется только один раз. Он сбрасывает забытый пароль — спрячьте его.") } })
    }
}

private data class PendingWeakening(val title: String, val action: () -> Unit)

@Composable
private fun WeakeningDialog(title: String, store: StopStore, onCancel: () -> Unit, onConfirm: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var phrase by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var seconds by remember { mutableIntStateOf(10) }
    LaunchedEffect(title) { seconds = 10; while (seconds > 0) { delay(1000); seconds-- } }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Это ослабит защиту «Стоп-экрана». Подтвердите действие вручную. Для продолжения нужно выждать 10 секунд и ввести фразу подтверждения.")
                if (store.hasPassword) {
                    OutlinedTextField(password, { password = it }, singleLine = true, label = { Text("Пароль защиты") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                } else Text("Чтобы ослабить защиту, сначала задайте пароль в разделе «Пароль», затем повторите действие.")
                OutlinedTextField(phrase, { phrase = it }, singleLine = true, label = { Text("Введите ОСЛАБИТЬ ЗАЩИТУ") }, modifier = Modifier.fillMaxWidth())
                if (seconds > 0) Text("Кнопка станет активна через $seconds с.")
                if (message.isNotEmpty()) Text(message, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(enabled = store.hasPassword && seconds == 0 && phrase.trim() == "ОСЛАБИТЬ ЗАЩИТУ", onClick = {
                if (store.hasPassword) {
                    when (val result = store.check(password)) {
                        StopStore.Check.Ok -> onConfirm()
                        StopStore.Check.Wrong -> message = "Неверный пароль"
                        is StopStore.Check.Wait -> message = "Слишком много попыток. Подождите ${(result.ms + 999) / 1000} с"
                    }
                }
            }) { Text("Ослабить защиту") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Отмена") } }
    )
}

@Composable
private fun PasswordDialog(onDismiss: () -> Unit, onSet: (String) -> Unit) {
    var a by remember { mutableStateOf("") }; var b by remember { mutableStateOf("") }
    val ok = Passcode.isAcceptablePassword(a) && a == b
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Пароль «Стоп-экрана»") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(a, { a = it }, singleLine = true, label = { Text("Пароль (от ${Passcode.MIN_PASSWORD_LEN} символов)") }, visualTransformation = PasswordVisualTransformation())
            OutlinedTextField(b, { b = it }, singleLine = true, label = { Text("Ещё раз") }, visualTransformation = PasswordVisualTransformation())
        } },
        confirmButton = { TextButton(onClick = { onSet(a) }, enabled = ok) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}

/** "Are you sure?" with a 10-second countdown — the impulse brake of ScreenLimit, in one dialog. */
@Composable
private fun HoldConfirm(onCancel: () -> Unit, onConfirm: () -> Unit) {
    var left by remember { mutableIntStateOf(10) }
    LaunchedEffect(Unit) { while (left > 0) { delay(1000); left-- } }
    AlertDialog(onDismissRequest = onCancel, title = { Text("Точно выйти из паузы?") },
        text = { Text("Подумайте ещё раз: пауза нужна именно сейчас. Кнопка станет активной через $left с.") },
        confirmButton = { TextButton(onClick = onConfirm, enabled = left == 0) { Text(if (left == 0) "Выйти" else "$left") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Остаться на паузе") } })
}

@Composable
private fun AppChecklist(info: String, selected: () -> Set<String>, protectedPkgs: Set<String>, onToggle: (String, Boolean) -> Unit) {
    val apps by LauncherApp.instance.apps.apps.collectAsStateCompat()
    val list = remember(apps) { apps.distinctBy { it.pkg }.sortedBy { it.label.lowercase() } }
    var sel by remember { mutableStateOf(selected()) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item { Column(Modifier.padding(horizontal = 16.dp)) { Info(info) } }
        items(list, key = { it.pkg }) { a ->
            val locked = a.pkg in protectedPkgs
            Row(Modifier.fillMaxWidth().clickable(enabled = !locked) { val on = a.pkg !in sel; onToggle(a.pkg, on); sel = selected() }
                .padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(a.pkg in sel, { on -> if (!locked) { onToggle(a.pkg, on); sel = selected() } }, enabled = !locked)
                Column(Modifier.weight(1f)) {
                    Text(a.label, style = MaterialTheme.typography.bodyLarge)
                    if (locked) Text("телефон, SMS и сам Уют не запрещаются", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun HistoryPage() {
    val h = remember { StopGuard.store.history }
    val f = remember { SimpleDateFormat("d MMM HH:mm", Locale("ru")) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        if (h.isEmpty()) item { Info("Пока пусто.") }
        items(h) { e -> Text("${f.format(Date(e.ts))} — ${e.detail}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp)) }
    }
}
