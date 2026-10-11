package org.ivanivorontsov.launcher.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ivanivorontsov.launcher.BuildConfig
import org.ivanivorontsov.launcher.LauncherApp
import org.ivanivorontsov.launcher.core.AccentPalette
import org.ivanivorontsov.launcher.core.HomeCategories
import org.ivanivorontsov.launcher.core.ClockStyle
import org.ivanivorontsov.launcher.core.DigitAnchor
import org.ivanivorontsov.launcher.core.DigitMode
import org.ivanivorontsov.launcher.core.DockStyle
import org.ivanivorontsov.launcher.core.DrawerStyle
import org.ivanivorontsov.launcher.core.FontCategory
import org.ivanivorontsov.launcher.core.GestureAction
import org.ivanivorontsov.launcher.core.HomeLayout
import org.ivanivorontsov.launcher.core.IconShape
import org.ivanivorontsov.launcher.core.Motion
import org.ivanivorontsov.launcher.core.Overlay
import org.ivanivorontsov.launcher.core.PageIndicator
import org.ivanivorontsov.launcher.core.Presets
import org.ivanivorontsov.launcher.core.TextCase
import org.ivanivorontsov.launcher.core.ThemeSpec
import org.ivanivorontsov.launcher.core.TileItem
import org.ivanivorontsov.launcher.core.TileKind
import org.ivanivorontsov.launcher.core.TileSize
import org.ivanivorontsov.launcher.core.TileFill
import org.ivanivorontsov.launcher.core.WallpaperStyle
import org.ivanivorontsov.launcher.data.AppInfo
import org.ivanivorontsov.launcher.data.Device
import org.ivanivorontsov.launcher.service.LockService
import org.ivanivorontsov.launcher.service.NotificationCounter
import java.io.File

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        val start = intent.getStringExtra("section")
        setContent {
            val spec by LauncherApp.instance.store.theme.collectAsStateCompat()
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !spec.dark
            LauncherTheme(spec) {
                androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) { SettingsRoot(start) { finish() } }
            }
        }
    }

    private var displayRefresh: android.hardware.display.DisplayManager.DisplayListener? = null

    override fun onStart() {
        super.onStart()
        if (displayRefresh == null) {
            displayRefresh = Device.registerDisplayRefresh(this) { LauncherApp.instance.store.config.value.highRefreshRate }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
        Device.applyRefreshRate(window, LauncherApp.instance.store.config.value.highRefreshRate)
    }

    override fun onStop() {
        Device.unregisterDisplayRefresh(this, displayRefresh)
        displayRefresh = null
        super.onStop()
    }

    companion object { var resumeTick by mutableIntStateOf(0) }
}

private enum class Screen(val title: String) {
    MAIN("Уют"), HOME("Главный экран"), THEMES("Темы"), EDITOR("Свой стиль"), FONTS("Шрифты"), GESTURES("Жесты"), HIDDEN("Скрытые приложения"),
    ICONS("Набор иконок"), LIVE("Живые плитки и разрешения"), DIGITS("Цифры поверх приложений"), SYSTEM("Системный шрифт и обои"), SMOOTH("Плавность и батарея"), ABOUT("О приложении")
}

@Composable
private fun SettingsRoot(start: String?, close: () -> Unit) {
    var screen by remember { mutableStateOf(if (start == "themes") Screen.THEMES else Screen.MAIN) }
    var fontTarget by remember { mutableStateOf("all") }
    BackHandler { if (screen == Screen.MAIN) close() else screen = if (screen == Screen.FONTS) Screen.EDITOR else Screen.MAIN }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", fontSize = 34.sp, modifier = Modifier.clickable { if (screen == Screen.MAIN) close() else screen = if (screen == Screen.FONTS) Screen.EDITOR else Screen.MAIN }.padding(horizontal = 14.dp))
            Text(screen.title, style = MaterialTheme.typography.headlineSmall)
        }
        Box(Modifier.weight(1f).navigationBarsPadding()) {
            when (screen) {
                Screen.MAIN -> MainScreen { screen = it }
                Screen.HOME -> HomeScreenSettings()
                Screen.THEMES -> ThemesScreen { screen = Screen.EDITOR }
                Screen.EDITOR -> EditorScreen(openFonts = { fontTarget = it; screen = Screen.FONTS })
                Screen.FONTS -> FontsScreen(fontTarget)
                Screen.GESTURES -> GesturesScreen()
                Screen.HIDDEN -> HiddenScreen()
                Screen.ICONS -> IconPacksScreen()
                Screen.LIVE -> LiveScreen()
                Screen.DIGITS -> DigitsScreen()
                Screen.SYSTEM -> SystemScreen()
                Screen.SMOOTH -> SmoothScreen()
                Screen.ABOUT -> AboutScreen()
            }
        }
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
private fun NavRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)) }
    }
}

@Composable
private fun Info(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), modifier = Modifier.padding(vertical = 4.dp))

@Composable
private fun MainScreen(go: (Screen) -> Unit) {
    val ctx = LocalContext.current as ComponentActivity
    val tick = SettingsActivity.resumeTick
    val isDefault = remember(tick) { Device.isDefaultLauncher(ctx) }
    val spec by LauncherApp.instance.store.theme.collectAsStateCompat()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item {
            Column(Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)).padding(16.dp)) {
                Text(if (isDefault) "Уют — ваш лаунчер ✓" else "Сделайте Уют лаунчером по умолчанию", style = MaterialTheme.typography.titleMedium)
                Info(if (isDefault) "Ярлыки приложений, жесты и кнопка «Домой» работают." else "Тогда кнопка «Домой» откроет Уют, и заработают ярлыки приложений по долгому нажатию.")
                if (!isDefault) Button(onClick = { Device.openDefaultLauncherChooser(ctx) }) { Text("Выбрать Уют") }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Presets.all.take(8).forEach { t -> ThemePreview(t, t.id == spec.id, Modifier.width(118.dp)) { LauncherApp.instance.store.setTheme(t) } }
            }
        }
        item { NavRow("Все темы", "${Presets.all.size} готовых тем с живым предпросмотром") { go(Screen.THEMES) } }
        item { NavRow("Главный экран", "Перетаскивание и папки, эффекты перелистывания, новые приложения, анимация возврата к иконке") { go(Screen.HOME) } }
        item { NavRow("Стоп-экран", "Лимит экранного времени, расписание, запрещённые приложения, пароль") { ctx.startActivity(android.content.Intent(ctx, org.ivanivorontsov.launcher.stop.StopActivity::class.java)) } }
        item { NavRow("Свой стиль", "Шрифты, цвета, форма иконок, сетка, анимации, обои") { go(Screen.EDITOR) } }
        item { NavRow("Шрифты", "${org.ivanivorontsov.launcher.ui.FontCatalog.all.size} шрифтов с кириллицей, в том числе рукописные") { go(Screen.FONTS) } }
        item { NavRow("Системный шрифт и обои", "Что можно поменять во всём телефоне") { go(Screen.SYSTEM) } }
        item { NavRow("Жесты", "Свайпы, двойное касание для блокировки") { go(Screen.GESTURES) } }
        item { NavRow("Живые плитки и разрешения", "Фото, календарь, счётчики уведомлений") { go(Screen.LIVE) } }
        item { NavRow("Цифры поверх приложений", "Крупный чёрный счётчик времени. Выключен, пока сами не включите") { go(Screen.DIGITS) } }
        item { NavRow("Скрытые приложения") { go(Screen.HIDDEN) } }
        item { NavRow("Набор иконок") { go(Screen.ICONS) } }
        item { NavRow("Плавность и батарея", "Частота экрана, фоновая работа (${Device.oemName()})") { go(Screen.SMOOTH) } }
        item { NavRow("О приложении", "Версия ${BuildConfig.VERSION_NAME}, лицензии шрифтов") { go(Screen.ABOUT) } }
    }
}

@Composable
private fun ThemesScreen(openEditor: () -> Unit) {
    val store = LauncherApp.instance.store
    val spec by store.theme.collectAsStateCompat()
    val custom by store.custom.collectAsStateCompat()
    val all = remember(custom) { (listOfNotNull(custom) + Presets.all) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 40.dp)) {
        items(all.chunked(2)) { pair ->
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                pair.forEach { t -> ThemePreview(t, t.id == spec.id, Modifier.weight(1f)) { store.setTheme(t) } }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item { OutlinedButton(onClick = openEditor, modifier = Modifier.fillMaxWidth()) { Text("Настроить текущую тему…") } }
    }
}

/** Every edit produces a “custom” theme that applies live to the home screen behind. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorScreen(openFonts: (String) -> Unit) {
    val store = LauncherApp.instance.store
    val spec by store.theme.collectAsStateCompat()
    fun set(f: (ThemeSpec) -> ThemeSpec) = store.setCustom(f(spec).copy(name = "Мой стиль", description = "Собран в редакторе"))
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
        item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { ThemePreview(spec, false, Modifier.width(150.dp).padding(8.dp)) } }
        item { Section("Макет") {
            Chips(HomeLayout.entries, spec.layout, { it.title }) { v -> set { it.copy(layout = v) } }
            Text("Меню приложений", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            Chips(DrawerStyle.entries, spec.drawer, { it.title }) { v -> set { it.copy(drawer = v) } }
        } }
        item { Section("Шрифты") {
            NavRow("Часы: " + (FontCatalog[spec.clockFont]?.name ?: "")) { openFonts("clock") }
            NavRow("Заголовки: " + (FontCatalog[spec.headingFont]?.name ?: "")) { openFonts("heading") }
            NavRow("Текст и подписи: " + (FontCatalog[spec.bodyFont]?.name ?: "")) { openFonts("body") }
            Labeled("Толщина часов: ${spec.clockWeight}") { Slider(spec.clockWeight.toFloat(), { v -> set { it.copy(clockWeight = (v / 100).toInt() * 100) } }, valueRange = 100f..900f, steps = 7) }
        } }
        item { Section("Цвета") {
            if (Build.VERSION.SDK_INT >= 31) SwitchRow("Цвета из обоев (Material You)", spec.dynamicColor) { v -> set { it.copy(dynamicColor = v) } }
            SwitchRow("Тёмная тема", spec.dark) { v -> set { it.copy(dark = v, background = if (v) 0xFF101114 else 0xFFF7F7F8, surface = if (v) 0xFF1B1D22 else 0xFFFFFFFF, onSurface = if (v) 0xFFF2F3F5 else 0xFF111827) } }
            Text("Акцент", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                (AccentPalette.tiles.map { it.second } + AccentPalette.soft).forEach { c ->
                    Box(Modifier.size(34.dp).clip(CircleShape).background(c.c()).border(if (spec.accent == c) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape).clickable { set { it.copy(accent = c) } })
                }
            }
            SwitchRow("Светлый текст на обоях", spec.onWallpaper == 0xFFFFFFFF) { v -> set { it.copy(onWallpaper = if (v) 0xFFFFFFFF else 0xFF111827) } }
        } }
        item { Section("Обои") {
            Chips(WallpaperStyle.entries, spec.wallpaper, { it.title }) { v -> set { it.copy(wallpaper = v, wallpaperColors = Presets.all.firstOrNull { p -> p.wallpaper == v }?.wallpaperColors ?: it.wallpaperColors) } }
            Labeled("Затемнение: ${(spec.wallpaperDim * 100).toInt()}%") { Slider(spec.wallpaperDim, { v -> set { it.copy(wallpaperDim = v) } }, valueRange = 0f..0.7f) }
            Labeled("Непрозрачность меню: ${(spec.surfaceAlpha * 100).toInt()}%") { Slider(spec.surfaceAlpha, { v -> set { it.copy(surfaceAlpha = v) } }, valueRange = 0.3f..1f) }
            SwitchRow("Матовое размытие", spec.blur) { v -> set { it.copy(blur = v) } }
        } }
        item { Section("Иконки") {
            Chips(IconShape.entries, spec.iconShape, { it.title }) { v -> set { it.copy(iconShape = v) } }
            Labeled("Размер: ${spec.iconSizeDp} dp") { Slider(spec.iconSizeDp.toFloat(), { v -> set { it.copy(iconSizeDp = v.toInt()) } }, valueRange = 40f..72f) }
            SwitchRow("Подписи", spec.showLabels) { v -> set { it.copy(showLabels = v) } }
            Labeled("Размер подписей: ${spec.labelSizeSp.toInt()} sp") { Slider(spec.labelSizeSp, { v -> set { it.copy(labelSizeSp = v) } }, valueRange = 9f..30f) }
            Chips(TextCase.entries, spec.textCase, { it.title }) { v -> set { it.copy(textCase = v) } }
            Labeled("Скругление карточек: ${spec.cornerDp} dp") { Slider(spec.cornerDp.toFloat(), { v -> set { it.copy(cornerDp = v.toInt()) } }, valueRange = 0f..40f) }
        } }
        item { Section("Сетка") {
            Labeled("Столбцы: ${spec.columns}") { Slider(spec.columns.toFloat(), { v -> set { it.copy(columns = v.toInt()) } }, valueRange = 3f..6f, steps = 2) }
            Labeled("Ряды: ${spec.rows}") { Slider(spec.rows.toFloat(), { v -> set { it.copy(rows = v.toInt()) } }, valueRange = 4f..8f, steps = 3) }
            Text("Док", style = MaterialTheme.typography.labelLarge); Chips(DockStyle.entries, spec.dockStyle, { it.title }) { v -> set { it.copy(dockStyle = v) } }
            Text("Часы", style = MaterialTheme.typography.labelLarge); Chips(ClockStyle.entries, spec.clockStyle, { it.title }) { v -> set { it.copy(clockStyle = v) } }
            Text("Индикатор страниц", style = MaterialTheme.typography.labelLarge); Chips(PageIndicator.entries, spec.pageIndicator, { it.title }) { v -> set { it.copy(pageIndicator = v) } }
        } }
        item { Section("Плитки") {
            Chips(TileFill.entries, spec.tileFill, { it.title }) { v -> set { it.copy(tileFill = v, wallpaper = if (v == TileFill.TRANSPARENT && it.wallpaper == WallpaperStyle.SOLID) WallpaperStyle.AURORA else it.wallpaper) } }
            SwitchRow("Больше плиток (6 в ряд)", spec.tileColumns == 6) { v -> set { it.copy(tileColumns = if (v) 6 else 4) } }
            Labeled("Промежутки: ${"%.0f".format(spec.tileGapDp * 2)} px") { Slider(spec.tileGapDp, { v -> set { it.copy(tileGapDp = v) } }, valueRange = 0f..12f) }
        } }
        item { Section("Движение и эффекты") {
            Chips(Motion.entries, spec.motion, { it.title }) { v -> set { it.copy(motion = v) } }
            Text("Наложение", style = MaterialTheme.typography.labelLarge); Chips(Overlay.entries, spec.overlay, { it.title }) { v -> set { it.copy(overlay = v) } }
            SwitchRow("Неоновое свечение текста", spec.glow) { v -> set { it.copy(glow = v) } }
            SwitchRow("3D-наклон при нажатии", spec.iconTilt) { v -> set { it.copy(iconTilt = v) } }
        } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Chips(values: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { v -> FilterChip(selected = v == selected, onClick = { onPick(v) }, label = { Text(label(v)) }) }
    }
}

@Composable
private fun Labeled(label: String, content: @Composable () -> Unit) { Column(Modifier.padding(top = 6.dp)) { Text(label, style = MaterialTheme.typography.labelLarge); content() } }

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked, onChange)
    }
}

private const val SAMPLE = "Съешь же ещё этих мягких французских булок, да выпей чаю. 0123456789"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FontsScreen(initialTarget: String) {
    val store = LauncherApp.instance.store
    val spec by store.theme.collectAsStateCompat()
    var target by remember { mutableStateOf(initialTarget) }
    val targets = listOf("all" to "Везде", "clock" to "Часы", "heading" to "Заголовки", "body" to "Текст")
    fun apply(id: String) = store.setCustom(when (target) {
        "clock" -> spec.copy(clockFont = id); "heading" -> spec.copy(headingFont = id); "body" -> spec.copy(bodyFont = id)
        else -> spec.copy(clockFont = id, headingFont = id, bodyFont = id)
    }.copy(name = "Мой стиль", description = "Собран в редакторе"))
    val current = when (target) { "clock" -> spec.clockFont; "heading" -> spec.headingFont; else -> spec.bodyFont }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Info("Все шрифты проверены программно: в каждом есть все 66 букв русского алфавита. Лицензия SIL OFL — в разделе «О приложении».")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { targets.forEach { (k, l) -> FilterChip(target == k, { target = k }, { Text(l) }) } }
            }
        }
        FontCategory.entries.forEach { cat ->
            val list = FontCatalog.all.filter { it.category == cat }
            if (list.isNotEmpty()) {
                item { Text(cat.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp)) }
                items(list, key = { it.id }) { f ->
                    val fam = FontRegistry.family(f.id)
                    Column(Modifier.fillMaxWidth().clickable { apply(f.id) }.background(if (f.id == current) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent).padding(horizontal = 20.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(f.name, fontFamily = fam, fontSize = 24.sp, modifier = Modifier.weight(1f))
                            if (f.id == current) Text("✓", color = MaterialTheme.colorScheme.primary, fontSize = 20.sp)
                        }
                        Text(SAMPLE, fontFamily = fam, fontSize = 17.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
                        Text(f.designer, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

@Composable
private fun GesturesScreen() {
    val ctx = LocalContext.current
    val store = LauncherApp.instance.store
    val cfg by store.config.collectAsStateCompat()
    val tick = SettingsActivity.resumeTick
    val lockOn = remember(tick) { LockService.isEnabled }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item { Section("Свайп вверх") { Chips(listOf(GestureAction.DRAWER, GestureAction.NOTIFICATIONS, GestureAction.SETTINGS, GestureAction.NONE), cfg.swipeUp, { it.title }) { v -> store.update { it.copy(swipeUp = v) } } } }
        item { Section("Свайп вниз") { Chips(listOf(GestureAction.NOTIFICATIONS, GestureAction.DRAWER, GestureAction.SETTINGS, GestureAction.NONE), cfg.swipeDown, { it.title }) { v -> store.update { it.copy(swipeDown = v) } } } }
        item { Section("Двойное касание") { Chips(listOf(GestureAction.LOCK, GestureAction.NOTIFICATIONS, GestureAction.SETTINGS, GestureAction.NONE), cfg.doubleTap, { it.title }) { v -> store.update { it.copy(doubleTap = v) } } } }
        item { Section("Блокировка двойным касанием") {
            Info(if (lockOn) "Служба включена ✓" else "Для блокировки экрана нужна служба спец. возможностей «Уют: блокировка экрана». Она ничего не читает с экрана и умеет только заблокировать телефон и открыть шторку.")
            if (Build.VERSION.SDK_INT >= 33) Info("Android 13+: если переключатель серый — откройте «О приложении» Уют → ⋮ → «Разрешить ограниченные настройки».")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { Device.openAccessibilitySettings(ctx) }) { Text("Спец. возможности") }
                OutlinedButton(onClick = { Device.openAppDetails(ctx) }) { Text("О приложении") }
            }
        } }
        item { Section("Клавиатура") { SwitchRow("Открывать клавиатуру в меню приложений", cfg.autoKeyboard) { v -> store.update { it.copy(autoKeyboard = v) } } } }
    }
}

@Composable
private fun HiddenScreen() {
    val store = LauncherApp.instance.store
    val cfg by store.config.collectAsStateCompat()
    val apps by LauncherApp.instance.apps.apps.collectAsStateCompat()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item { Info("  Скрытые приложения не видны в меню, но находятся поиском.") }
        items(apps, key = { it.key }) { a ->
            Row(Modifier.fillMaxWidth().clickable { store.update { c -> c.copy(hidden = if (a.key in c.hidden) c.hidden - a.key else c.hidden + a.key) } }.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(a, 36.dp); Spacer(Modifier.width(14.dp))
                Text(a.label, Modifier.weight(1f)); Switch(a.key in cfg.hidden, null)
            }
        }
    }
}

@Composable
private fun IconPacksScreen() {
    val store = LauncherApp.instance.store
    val cfg by store.config.collectAsStateCompat()
    val packs = remember { LauncherApp.instance.icons.packs.installed() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item { NavRow((if (cfg.iconPack == null) "✓  " else "") + "Системные иконки") { store.update { it.copy(iconPack = null) } } }
        items(packs, key = { it.pkg }) { p -> NavRow((if (cfg.iconPack == p.pkg) "✓  " else "") + p.label, p.pkg) { store.update { it.copy(iconPack = p.pkg) } } }
        if (packs.isEmpty()) item { Info("  Наборы иконок не найдены. Подходят любые наборы для Nova/ADW из магазина приложений.") }
    }
}

@Composable
private fun LiveScreen() {
    val ctx = LocalContext.current
    val store = LauncherApp.instance.store
    val live = LauncherApp.instance.live
    val tick = SettingsActivity.resumeTick
    var permTick by remember { mutableIntStateOf(0) }
    val photos = remember(tick, permTick) { live.hasPhotoAccess() }
    val calendar = remember(tick, permTick) { live.hasCalendarAccess() }
    val notif = remember(tick) { androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName) }
    val photoReq = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permTick++ }
    val calReq = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permTick++ }
    val scope = rememberCoroutineScope()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val f = withContext(Dispatchers.IO) { copyDownscaled(ctx, uri) }
            if (f != null) store.update { it.copy(tileBackgroundUri = Uri.fromFile(f).toString() + "?t=" + System.currentTimeMillis()) }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item { Section("Счётчики уведомлений на плитках") {
            Info(if (notif) "Включено ✓ (хранится только число уведомлений, текст не читается и не сохраняется)" else "Необязательно. Дайте Уют доступ к уведомлениям, чтобы плитки и иконки показывали число новых.")
            Button(onClick = { Device.openNotificationAccess(ctx) }) { Text(if (notif) "Настройки доступа" else "Разрешить") }
            @Suppress("UNUSED_EXPRESSION") NotificationCounter
        } }
        item { Section("Плитка «Фото»") {
            Info(if (photos) "Доступ к фото есть ✓ Фото не покидают телефон." else "Показывает ваши фото с плавным приближением. Разрешение спрашивается только для этой плитки.")
            if (!photos) Button(onClick = { photoReq.launch(live.photoPermissions()) }) { Text("Разрешить фото") }
        } }
        item { Section("Плитка «Календарь»") {
            Info(if (calendar) "Доступ к календарю есть ✓" else "Показывает ближайшее событие. Необязательно.")
            if (!calendar) Button(onClick = { calReq.launch(android.Manifest.permission.READ_CALENDAR) }) { Text("Разрешить календарь") }
        } }
        item { Section("Фон прозрачных плиток") {
            Info("Для темы «Плитки: окна» можно выбрать своё фото — оно будет видно только внутри плиток и плыть с параллаксом.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Выбрать фото") }
                OutlinedButton(onClick = { store.update { it.copy(tileBackgroundUri = null) } }) { Text("Обои темы") }
            }
        } }
    }
}

private fun copyDownscaled(ctx: android.content.Context, uri: Uri): File? = try {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
    val target = 1600; var s = 1
    while (o.outWidth / (s * 2) >= target && o.outHeight / (s * 2) >= target) s *= 2
    val b = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) }
    b?.let { File(ctx.filesDir, "tile_bg.jpg").also { f -> f.outputStream().use { os -> it.compress(Bitmap.CompressFormat.JPEG, 90, os) } } }
} catch (_: Exception) { null }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DigitsScreen() {
    val ctx = LocalContext.current as ComponentActivity
    val store = LauncherApp.instance.store
    val cfg by store.config.collectAsStateCompat()
    val tick = SettingsActivity.resumeTick
    var explain by remember { mutableStateOf(false) }
    var waitingForPermission by remember { mutableStateOf(false) }
    LaunchedEffect(tick) {
        if (!waitingForPermission) return@LaunchedEffect
        waitingForPermission = false
        if (android.provider.Settings.canDrawOverlays(ctx)) store.update { it.copy(digitOverlay = true) }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item { Section("Показ") {
            SwitchRow("Показывать цифры", cfg.digitOverlay) { on ->
                if (!on) {
                    store.update { it.copy(digitOverlay = false) }
                    return@SwitchRow
                }
                if (android.provider.Settings.canDrawOverlays(ctx)) store.update { it.copy(digitOverlay = true) }
                else explain = true
            }
            Info("Выключено, пока сами не включите. Крупные чёрные цифры рисуются поверх других приложений и обновляются каждую секунду. Касания проходят сквозь них: кнопки под цифрами нажимаются как обычно.")
            if (cfg.digitOverlay && !android.provider.Settings.canDrawOverlays(ctx)) {
                Info("Разрешение «Поверх других приложений» не выдано, поэтому цифры сейчас не видны.")
                Button(onClick = { explain = true }) { Text("Выдать разрешение") }
            }
        } }
        item { Section("Что показывать") {
            Chips(DigitMode.entries, cfg.digitMode, { it.title }) { v -> store.update { it.copy(digitMode = v) } }
            Info(when (cfg.digitMode) {
                DigitMode.APP -> "Сколько это приложение на экране в текущий заход: мм:сс, после часа — ч:мм:сс. Считается, пока включена служба специальных возможностей Уюта (та же, что для «Стоп-экрана»)."
                DigitMode.CLOCK -> "Текущее время, с секундами."
                DigitMode.TODAY -> "Сколько сегодня экран был включён, пока цифры включены. В полночь счётчик обнуляется."
            })
        } }
        item { Section("Вид") {
            Text("Положение", style = MaterialTheme.typography.labelLarge)
            Chips(DigitAnchor.entries, cfg.digitAnchor, { it.title }) { v -> store.update { it.copy(digitAnchor = v) } }
            Labeled("Размер: ${cfg.digitSizeSp} sp") {
                Slider(cfg.digitSizeSp.toFloat(), { v -> store.update { it.copy(digitSizeSp = v.toInt()) } }, valueRange = 36f..140f)
            }
            Labeled("Непрозрачность: ${(cfg.digitAlpha * 100).toInt()}%") {
                Slider(cfg.digitAlpha, { v -> store.update { it.copy(digitAlpha = v) } }, valueRange = 0.25f..1f)
            }
            SwitchRow("Тонкая белая обводка", cfg.digitOutline) { v -> store.update { it.copy(digitOutline = v) } }
            Info("Цифры чёрные. Обводка помогает читать их на тёмном фоне.")
        } }
    }
    if (explain) AlertDialog(
        onDismissRequest = { explain = false },
        title = { Text("Цифры поверх приложений") },
        text = { Text("Чтобы показать цифры поверх других приложений, Android просит разрешение «Поверх других приложений». Без него переключатель останется выключенным. Цифры не перехватывают касания.") },
        confirmButton = {
            TextButton(onClick = {
                explain = false
                waitingForPermission = true
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:${ctx.packageName}"),
                )
                runCatching { ctx.startActivity(intent) }.onFailure {
                    waitingForPermission = false
                    Toast.makeText(ctx, "Не удалось открыть разрешение", Toast.LENGTH_LONG).show()
                }
            }) { Text("Открыть разрешение") }
        },
        dismissButton = { TextButton(onClick = { explain = false }) { Text("Не сейчас") } },
    )
}

@Composable
private fun SystemScreen() {
    val ctx = LocalContext.current
    val spec by LauncherApp.instance.store.theme.collectAsStateCompat()
    val scope = rememberCoroutineScope()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item { Section("Честно о системном шрифте") {
            Info("Android не даёт обычным приложениям менять шрифт всей системы — для этого нужен root или фирменный магазин тем производителя. Поэтому Уют меняет шрифты везде, где рисует сам: часы, подписи, меню, виджеты и плитки.")
            Info("Кнопка ниже откроет экран шрифтов вашего телефона (${Device.oemName()}), если производитель его сделал; иначе — настройки экрана.")
            Button(onClick = { if (!Device.openSystemFontSettings(ctx)) Toast.makeText(ctx, "Не нашёл настройки шрифта", Toast.LENGTH_SHORT).show() }) { Text("Открыть настройки шрифта телефона") }
        } }
        item { Section("Обои темы — во всём телефоне") {
            Info("Можно поставить нарисованные обои текущей темы «${spec.name}» на рабочий стол и экран блокировки. На Android 12+ система сама перекрасит акцентные цвета (Material You) — шторка, настройки и приложения подстроятся под тему.")
            Button(enabled = spec.wallpaper != WallpaperStyle.SYSTEM, onClick = {
                scope.launch {
                    val dm = ctx.resources.displayMetrics
                    val ok = withContext(Dispatchers.Default) {
                        val bmp = Wallpapers.render(spec, minOf(dm.widthPixels, dm.heightPixels), maxOf(dm.widthPixels, dm.heightPixels))
                        Device.setSystemWallpaper(ctx, bmp)
                    }
                    Toast.makeText(ctx, if (ok) "Обои установлены" else "Не получилось установить обои", Toast.LENGTH_SHORT).show()
                }
            }) { Text("Установить обои темы") }
            if (spec.wallpaper == WallpaperStyle.SYSTEM) Info("В этой теме используются обои телефона.")
        } }
    }
}

@Composable
private fun HomeScreenSettings() {
    val launcher = LauncherApp.instance
    val store = launcher.store
    val cfg by store.config.collectAsStateCompat()
    val spec by store.theme.collectAsStateCompat()
    val installedApps by launcher.apps.apps.collectAsStateCompat()
    val own = cfg.hasOwnLayout(spec.id)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item { Section("Главный экран без меню приложений") {
            SwitchRow("Все приложения на главном экране", cfg.allAppsOnHome) { enabled ->
                val keys = installedApps.filter { it.key !in cfg.hidden }.map { it.key }.distinct()
                store.update { current ->
                    val base = current.copy(allAppsOnHome = enabled)
                    if (!enabled) base else base.editAllLayouts { layout ->
                        var next = layout
                        keys.forEach { key ->
                            next = next.appendHome(key)
                            next = next.pinTile(TileItem("t_$key", TileKind.APP, key, TileSize.SMALL))
                        }
                        next.copy(favorites = (next.favorites + keys).distinct()).normalized()
                    }
                }
            }
            Info("Включите, чтобы убрать отдельное меню приложений и разместить установленные приложения на страницах главного экрана. В теме «Плитки» они появятся плитками; в минималистичной теме — текстовым списком. Новые приложения тоже будут добавляться автоматически.")
            SortIntoFoldersButton(spec.id, installedApps)
        } }
        item { Section("Новые приложения") {
            SwitchRow("Добавлять новые приложения на главный экран", cfg.autoAddNew) { v -> store.update { it.copy(autoAddNew = v) } }
            Info("Только что установленное приложение встаёт в первую свободную ячейку (в «Плитках» — маленькой плиткой). Обновления, системные приложения и копии из рабочего профиля не добавляются.")
        } }
        item { Section("Перелистывание страниц") {
            val cur = cfg.pageEffectFor(spec.id, spec.layout)
            Chips(org.ivanivorontsov.launcher.core.PageEffect.entries, cur, { it.title }) { v -> store.update { it.copy(pageEffects = it.pageEffects + (spec.id to v)) } }
            Info("Эффект хранится отдельно для каждой темы (сейчас — «${spec.name}»). «Цилиндр» сворачивает страницы на объёмный барабан: в покое экран ровный, при листании края уходят вглубь, а соседняя страница видна с обратной стороны.")
        } }
        item { Section("Перетаскивание") {
            Info("Долгое нажатие поднимает иконку — тяните её: остальные иконки расступаются. Отпустите на другой иконке, чтобы сделать папку; у края экрана страница перелистнётся сама. Сверху появятся «Удалить с экрана», «О приложении» и «Удалить приложение».")
            Info("Из меню приложений можно вытащить иконку прямо на главный экран. В «Плитках» плитки двигаются так же, а размер меняется кнопкой в углу плитки.")
            Info(if (own) "У темы «${spec.name}» своя расстановка — у каждой темы она хранится отдельно." else "Тема «${spec.name}» пока использует общую расстановку. Как только вы что-то передвинете, у неё появится своя.")
            if (own) OutlinedButton(onClick = { store.update { it.resetLayout(spec.id) } }) { Text("Сбросить расстановку этой темы") }
        } }
        item { Section("Возврат на главный экран") {
            SwitchRow("Анимация «приложение сворачивается в иконку»", cfg.returnAnimation) { v -> store.update { it.copy(returnAnimation = v) } }
            SwitchRow("Жест «домой» прилетает в иконку (Android 11+)", cfg.gestureContract) { v -> store.update { it.copy(gestureContract = v) } }
            Info("Окно чужого приложения сторонний лаунчер анимировать не может — это делает система. Уют сообщает системе, где лежит иконка (так же, как Lawnchair), и сам дорисовывает «сворачивание» карточки в иконку. На части прошивок (в том числе ColorOS / realme UI) система этот сигнал игнорирует — тогда работает только анимация Уюта.")
            Info("realme UI / ColorOS с жестами: примерно секунду после жеста «Домой» система показывает только обои — так у всех сторонних лаунчеров (Nova, Lawnchair, Niagara, Smart Launcher), это не исправить без root. Уют начинает «сворачивание» ровно в момент, когда система его показала. С кнопочной навигацией задержки нет.")
        } }
    }
}

@Composable
private fun SortIntoFoldersButton(themeId: String, installedApps: List<AppInfo>) {
    val store = LauncherApp.instance.store
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    val finance = remember { HomeCategories.platformFinanceCategory() }
    Button(onClick = { confirm = true }, modifier = Modifier.padding(top = 8.dp)) { Text("Разложить по папкам") }
    Info("Автоматически по категориям. Док не меняется. Уже созданные папки остаются; если папка с таким названием уже есть на главном экране, приложения добавятся в неё.")
    if (!confirm) return
    AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Разложить по папкам?") },
        text = {
            Text("Свободные иконки главного экрана будут собраны в папки: Игры, Музыка, Видео, Фото, Общение, Новости, Карты, Продуктивность, Специальные возможности, Финансы и «Прочее». Док, плитки и ваши папки не разбираются. Одно приложение в категории останется иконкой.")
        },
        confirmButton = {
            TextButton(onClick = {
                confirm = false
                val byKey = installedApps.associateBy { it.key }
                var changed = false
                store.update { current ->
                    val cur = current.layoutFor(themeId)
                    val next = cur.groupHomeIntoFolders { key ->
                        val app = byKey[key]
                        if (app == null) HomeCategories.OTHER
                        else HomeCategories.folderName(app.pkg, app.originalLabel.ifEmpty { app.label }, app.androidCategory, finance)
                    }
                    changed = next != cur
                    if (!changed) current else current.copy(layouts = current.layouts + (themeId to next), lastEdited = themeId)
                }
                Toast.makeText(ctx, if (changed) "Готово: приложения разложены по папкам" else "На главном экране нечего собирать в папки", Toast.LENGTH_SHORT).show()
            }) { Text("Разложить") }
        },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Отмена") } },
    )
}

@Composable
private fun SmoothScreen() {
    val ctx = LocalContext.current as ComponentActivity
    val store = LauncherApp.instance.store
    val cfg by store.config.collectAsStateCompat()
    val tick = SettingsActivity.resumeTick
    val maxHz = remember { Device.maxRefreshRate(ctx.window).toInt() }
    val ignoring = remember(tick) { Device.isIgnoringBatteryOptimizations(ctx) }
    val anim = remember(tick) { Device.animationsEnabled() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item { Section("Частота экрана") {
            val activeHz = remember(tick) { Device.activeRefreshRate(ctx.window) }
            var taps by remember { mutableIntStateOf(0) }
            Text(
                "Сейчас ${if (activeHz > 1f) "%.0f".format(activeHz) else "—"} Гц",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.clickable {
                    taps++
                    val now = Device.activeRefreshRate(ctx.window)
                    val line = "UyutHz: active ${"%.1f".format(now)}Hz"
                    android.util.Log.i("UyutHz", line)
                    if (taps >= 5) {
                        taps = 0
                        Toast.makeText(ctx, "Частота окна: ${"%.1f".format(now)} Гц (лог UyutHz)", Toast.LENGTH_LONG).show()
                    }
                },
            )
            SwitchRow("Максимальная частота (до $maxHz Гц)", cfg.highRefreshRate) { v -> store.update { it.copy(highRefreshRate = v) } }
            Info("Все анимации считаются по времени, а не по кадрам: на 60, 90, 120 и 144 Гц они одинаковые по длительности, просто плавнее. Уют просит самый высокий режим для текущего разрешения и не меняет разрешение.")
            Info("realme и ColorOS часто держат сторонние приложения на 60 Гц, даже если экран умеет 144. Если жест «Домой» всё равно дёргается: Настройки телефона → Экран → Частота обновления → высокая частота для приложений — добавьте Уют. Фактический режим пишется в logcat, тег UyutHz. Пять нажатий на строку «Сейчас» показывают его тостом.")
        } }
        item { Section("Анимации") { Info(if (anim) "Системные анимации включены ✓" else "В настройках телефона отключены анимации — Уют работает без анимаций и не тратит на них энергию.") } }
        item { Section("Фоновая работа") {
            Info(if (ignoring) "Уют исключён из оптимизации батареи ✓" else "Чтобы система не выгружала лаунчер (и он не «перезапускался» при возврате домой), исключите его из оптимизации батареи.")
            if (!ignoring) Button(onClick = { Device.requestIgnoreBatteryOptimizations(ctx) }) { Text("Не выгружать Уют") }
            if (Device.oem == Device.Oem.REALME_OPPO_ONEPLUS) {
                Info("realme / ColorOS: дополнительно откройте «Настройки → Приложения → Уют → Использование батареи» и разрешите фоновую активность; в «Недавних» потяните карточку Уюта вниз, чтобы закрепить её замком.")
                Info("На realme UI системная анимация сторонних лаунчеров может начинаться с задержкой или идти на 60 Гц. Добавьте Уют в список приложений с высокой частотой обновления (Экран → Частота обновления). Уют дополнительно рисует собственную анимацию после системного перехода.")
            }
            if (Device.oem == Device.Oem.XIAOMI) Info("Xiaomi: «Безопасность → Приложения → Уют → Автозапуск» и «Контроль активности → Нет ограничений».")
            if (Device.oem == Device.Oem.SAMSUNG) Info("Samsung: «Батарея → Ограничения фонового использования → Никогда не переводить в спящий режим» → добавьте Уют.")
            if (Device.oem == Device.Oem.HUAWEI_HONOR) Info("Huawei/Honor: «Батарея → Запуск приложений → Уют → Управлять вручную» и включите все переключатели.")
            OutlinedButton(onClick = { Device.openAppDetails(ctx) }) { Text("О приложении Уют") }
        } }
    }
}

@Composable
private fun AboutScreen() {
    val ctx = LocalContext.current
    var showOfl by remember { mutableStateOf(false) }
    val ofl = remember { try { ctx.assets.open("OFL.txt").bufferedReader().readText() } catch (_: Exception) { "" } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 40.dp)) {
        item {
            Text("Уют ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleLarge)
            Info("Без интернета (нет разрешения INTERNET), без рекламы и трекеров. Все данные — на телефоне.")
            Info("Идеи взяты из лучших лаунчеров: быстрый поиск и плавность Kvaesitso и Olauncher, алфавит-«волна» Niagara, формы иконок и шрифты Lawnchair, живые плитки SquareHome/Launcher 10. Темы «Купертино» и «Плитки» — оригинальные работы по мотивам классических раскладок; логотипы, иконки, обои и фирменные шрифты Apple и Microsoft не используются.")
            Text("Шрифты", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            Info("Все шрифты распространяются по лицензии SIL Open Font License 1.1. Шрифты с «зарезервированным именем» включены без изменений; остальные сокращены до латиницы и кириллицы (это разрешено лицензией).")
        }
        items(FontCatalog.all, key = { it.id }) { f ->
            Column(Modifier.padding(vertical = 6.dp)) {
                Text(f.name + "  —  " + f.designer, fontFamily = FontRegistry.family(f.id), fontSize = 17.sp)
                Text(f.copyright.ifEmpty { "SIL OFL 1.1" } + (if (f.unmodified) "  ·  без изменений" else "  ·  подмножество: латиница + кириллица"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
        item {
            OutlinedButton(onClick = { showOfl = !showOfl }, modifier = Modifier.padding(top = 12.dp)) { Text(if (showOfl) "Скрыть текст лицензии" else "Текст лицензии SIL OFL 1.1") }
            if (showOfl) Text(ofl, fontSize = 11.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(24.dp).navigationBarsPadding())
        }
    }
    @Suppress("UNUSED_EXPRESSION") FontWeight
}
