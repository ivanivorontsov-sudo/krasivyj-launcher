package org.ivanivorontsov.launcher.ui

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.UserManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import org.ivanivorontsov.launcher.core.SearchKey
import org.ivanivorontsov.launcher.core.WidgetSearch
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ivanivorontsov.launcher.LauncherApp
import org.ivanivorontsov.launcher.core.ClockStyle
import org.ivanivorontsov.launcher.core.WidgetItem
import org.ivanivorontsov.launcher.data.Device

object WidgetHostHolder {
    const val HOST_ID = 0x5559
    @Volatile private var host: AppWidgetHost? = null
    fun host(ctx: Context): AppWidgetHost = host ?: synchronized(this) { host ?: GatedWidgetHost(ctx.applicationContext, HOST_ID).also { host = it } }
}

/** Left-most page: “today” cards + Android widgets (stacked, each resizable in height). */
@Composable
fun WidgetsPage(ui: UiState) {
    val spec = LocalSpec.current; val colors = LocalColors.current; val fonts = LocalFonts.current
    val ctx = LocalContext.current
    val actions = LocalActions.current
    val config by LauncherApp.instance.store.config.collectAsStateCompat()
    val live = LauncherApp.instance.live
    val t = nowMinute()
    val ev by produceState<org.ivanivorontsov.launcher.data.LiveSources.Event?>(null, t / (10 * 60_000L), ui.permissionsVersion) { value = live.nextEvent() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding()) {
        Spacer(Modifier.height(18.dp))
        Text(spec.withCase("Сегодня"), style = labelStyle(colors.onWallpaper, 30.sp).copy(fontFamily = fonts.heading, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(start = 6.dp))
        Text(dateText(t), style = labelStyle(colors.onWallpaper.copy(alpha = 0.8f), 15.sp), modifier = Modifier.padding(start = 6.dp, bottom = 12.dp))
        if (spec.clockStyle != ClockStyle.CARD) { ClockHeader(Modifier.fillMaxWidth(), ClockStyle.CARD); Spacer(Modifier.height(12.dp)) }
        Card {
            Text("Календарь", style = labelStyle(colors.accent, 13.sp).copy(fontWeight = FontWeight.SemiBold, shadow = null))
            if (live.hasCalendarAccess()) {
                Text(ev?.title ?: "Ближайших событий нет", style = labelStyle(colors.onWallpaper, 18.sp).copy(fontFamily = fonts.heading), maxLines = 2, overflow = TextOverflow.Ellipsis)
                ev?.let { Text(it.whenText, style = labelStyle(colors.onWallpaper.copy(alpha = 0.75f), 13.sp)) }
            } else Text("Показывать ближайшее событие — нажмите", style = labelStyle(colors.onWallpaper, 15.sp), modifier = Modifier.clickable { actions.requestCalendar() })
        }
        Spacer(Modifier.height(12.dp))
        if (config.diagNoWidgets && config.widgets.isNotEmpty()) {
            Card { Text("Виджеты Android скрыты («Упрощённый дом» в настройках)", style = labelStyle(colors.onWallpaper, 14.sp)) }
            Spacer(Modifier.height(12.dp))
        } else config.widgets.forEachIndexed { i, w ->
            key(w.widgetId) { WidgetCell(w, i, config.widgets.size, ui.widgetEdit) }
            Spacer(Modifier.height(12.dp))
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Pill("+ Виджет") { ui.widgetPickerToToday = true; actions.addWidget() }
            if (config.widgets.isNotEmpty()) Pill(if (ui.widgetEdit) "Готово" else "Изменить") { ui.widgetEdit = !ui.widgetEdit }
            Pill("Убрать экран") { actions.openSettings("home") }
        }
        Spacer(Modifier.height(24.dp))
        @Suppress("UNUSED_EXPRESSION") ctx
    }
    @Suppress("UNUSED_EXPRESSION") Device
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    val spec = LocalSpec.current; val colors = LocalColors.current
    Column(Modifier.fillMaxWidth().glass(spec.cornerDp.coerceAtMost(28).dp, colors, 0.14f).padding(16.dp)) { content() }
}

@Composable
private fun Pill(text: String, onClick: () -> Unit) {
    val colors = LocalColors.current; val spec = LocalSpec.current
    Box(Modifier.clip(RoundedCornerShape(50)).background(colors.onWallpaper.copy(alpha = 0.16f)).clickable { onClick() }.padding(horizontal = 16.dp, vertical = 9.dp)) {
        Text(spec.withCase(text), style = labelStyle(colors.onWallpaper, 14.sp))
    }
}

@Composable
private fun WidgetCell(w: WidgetItem, index: Int, total: Int, edit: Boolean) {
    val ctx = LocalContext.current
    val spec = LocalSpec.current; val colors = LocalColors.current
    val store = LauncherApp.instance.store
    val awm = remember { AppWidgetManager.getInstance(ctx) }
    val info: AppWidgetProviderInfo? = remember(w.widgetId) { try { awm.getAppWidgetInfo(w.widgetId) } catch (_: Exception) { null } }
    val density = LocalDensity.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(spec.cornerDp.coerceAtMost(28).dp))) {
        if (info == null) {
            Box(Modifier.fillMaxWidth().height(64.dp).glass(spec.cornerDp.dp, colors), contentAlignment = Alignment.Center) { Text("Виджет недоступен", color = colors.onWallpaper) }
        } else {
            AndroidView(
                factory = { c ->
                    WidgetHostHolder.host(c).createView(c.applicationContext, w.widgetId, info).apply { setPadding(0, 0, 0, 0) }
                },
                update = { v: AppWidgetHostView ->
                    val wd = (v.width / density.density).toInt().takeIf { it > 0 } ?: 360
                    // 1.4.3: only on a real size change (as on home pages). Before, every recomposition sent the size
                    // to the widget's app, which answered with fresh RemoteViews re-inflated on the main thread.
                    val tag = wd * 10_000 + w.heightDp
                    if (v.tag != tag) { v.tag = tag; @Suppress("DEPRECATION") v.updateAppWidgetSize(Bundle(), wd, w.heightDp, wd, w.heightDp) }
                },
                modifier = Modifier.fillMaxWidth().height(w.heightDp.dp),
            )
        }
        if (edit) Row(Modifier.fillMaxWidth().background(colors.surface.copy(alpha = 0.9f)).padding(6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            fun upd(f: (List<WidgetItem>) -> List<WidgetItem>) = store.update { it.copy(widgets = f(it.widgets)) }
            EditBtn("▲") { if (index > 0) upd { l -> l.toMutableList().apply { add(index - 1, removeAt(index)) } } }
            EditBtn("▼") { if (index < total - 1) upd { l -> l.toMutableList().apply { add(index + 1, removeAt(index)) } } }
            EditBtn("−") { upd { l -> l.map { if (it.widgetId == w.widgetId) it.copy(heightDp = (it.heightDp - 40).coerceAtLeast(60)) else it } } }
            EditBtn("+") { upd { l -> l.map { if (it.widgetId == w.widgetId) it.copy(heightDp = (it.heightDp + 40).coerceAtMost(600)) else it } } }
            EditBtn("✕") { WidgetHostHolder.host(ctx).deleteAppWidgetId(w.widgetId); upd { l -> l.filter { it.widgetId != w.widgetId } } }
        }
    }
}

@Composable
private fun EditBtn(s: String, onClick: () -> Unit) {
    val colors = LocalColors.current
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(50)).background(colors.onSurface.copy(alpha = 0.12f)).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Text(s, color = colors.onSurface, fontSize = 16.sp)
    }
}

/** Own widget picker: providers from every profile, grouped by app, with previews (decoded off the main thread). */
@Composable
fun WidgetPicker(toToday: Boolean = false, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val colors = LocalColors.current; val fonts = LocalFonts.current
    val actions = LocalActions.current as? WidgetBinder
    val all by produceState<List<Pair<String, List<PickerEntry>>>?>(null) {
        value = withContext(Dispatchers.IO) {
            val awm = AppWidgetManager.getInstance(ctx)
            val um = ctx.getSystemService(UserManager::class.java)
            val pm = ctx.packageManager
            fun app(p: AppWidgetProviderInfo) = try { pm.getApplicationLabel(pm.getApplicationInfo(p.provider.packageName, 0)).toString() } catch (_: Exception) { p.provider.packageName }
            um.userProfiles.flatMap { awm.getInstalledProvidersForProfile(it) }
                .groupBy { app(it) }.toSortedMap(String.CASE_INSENSITIVE_ORDER)
                .map { (a, l) -> a to l.map { p -> val lb = p.loadLabel(pm) ?: ""; PickerEntry(p, lb, WidgetSearch.key(lb, a, p.provider.packageName)) }.sortedBy { it.label } }
        }
    }
    // Search by widget label and app name: case/ё-insensitive, translit and wrong keyboard layout (same engine as app search).
    var query by rememberSaveable { mutableStateOf("") }
    val groups = remember(all, query) {
        all?.let { g -> if (query.isBlank()) g else g.mapNotNull { (a, l) -> WidgetSearch.filter(l, query) { it.key }.takeIf { it.isNotEmpty() }?.let { a to it } } }
    }
    val showOwnClock = query.isBlank() || WidgetSearch.filter(listOf(Unit), query) { WidgetSearch.key("Часы", "СуперСмарт", "clock время") }.isNotEmpty()
    androidx.activity.compose.BackHandler { onDismiss() }
    Column(Modifier.fillMaxSize().background(colors.surface).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (toToday) "Виджеты: «Сегодня»" else "Виджеты на главный экран", color = colors.onSurface, fontFamily = fonts.heading, fontSize = 24.sp, modifier = Modifier.weight(1f))
            Text("Закрыть", color = colors.accent, modifier = Modifier.clickable { onDismiss() }.padding(8.dp))
        }
        Box(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 6.dp).fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp))
                .background(colors.onSurface.copy(alpha = 0.08f)).padding(start = 16.dp, end = 4.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (query.isEmpty()) Text("⌕  Поиск виджета или приложения", color = colors.onSurface.copy(alpha = 0.55f), fontFamily = fonts.body, fontSize = 16.sp)
            BasicTextField(
                query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(end = 40.dp),
                textStyle = androidx.compose.ui.text.TextStyle(color = colors.onSurface, fontFamily = fonts.body, fontSize = 17.sp),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
            )
            if (query.isNotEmpty()) Text("✕", color = colors.onSurface.copy(alpha = 0.6f), fontSize = 18.sp,
                modifier = Modifier.align(Alignment.CenterEnd).clickable { query = "" }.padding(10.dp))
        }
        if (all == null) Text("Загружаю список виджетов…", color = colors.onSurface.copy(alpha = 0.7f), modifier = Modifier.padding(16.dp))
        else if (all!!.isEmpty()) Text("На телефоне нет приложений с виджетами", color = colors.onSurface, modifier = Modifier.padding(16.dp))
        else if (groups.orEmpty().isEmpty() && !(showOwnClock && !toToday)) Text("Ничего не найдено по «$query»", color = colors.onSurface.copy(alpha = 0.7f), modifier = Modifier.padding(16.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
          // 1.3.9: the launcher's own widgets first.
          if (!toToday && showOwnClock) {
            item(key = "h:own") {
                Text("СуперСмарт", color = colors.accent, fontFamily = fonts.heading, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 2.dp))
            }
            item(key = "own:clock") {
                Row(Modifier.fillMaxWidth().clickable { actions?.addBuiltInClock(); onDismiss() }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(96.dp, 72.dp).clip(RoundedCornerShape(12.dp)).background(colors.onSurface.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
                        Text(timeIn(nowMinute(), org.ivanivorontsov.launcher.core.ClockZone()), color = colors.onSurface, fontFamily = fonts.clock, fontSize = 26.sp)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Часы", color = colors.onSurface, fontFamily = fonts.body, fontSize = 16.sp)
                        Text("до 3 часовых поясов · размер меняется", color = colors.onSurface.copy(alpha = 0.6f), fontSize = 13.sp)
                    }
                }
            }
          }
          groups.orEmpty().forEach { (appName, providers) ->
            item(key = "h:$appName") {
                Text(appName, color = colors.accent, fontFamily = fonts.heading, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 2.dp))
            }
            items(providers, key = { it.info.provider.flattenToString() + it.info.profile.hashCode() }) { e ->
                val p = e.info
                val preview by produceState<ImageBitmap?>(null, p.provider) {
                    value = withContext(Dispatchers.IO) {
                        try {
                            val d = p.loadPreviewImage(ctx, 0) ?: p.loadIcon(ctx, 0)
                            d?.let {
                                val maxW = 360; val iw = it.intrinsicWidth.coerceAtLeast(1); val ih = it.intrinsicHeight.coerceAtLeast(1)
                                val sc = minOf(1f, maxW / iw.toFloat()); val bw = (iw * sc).toInt().coerceAtLeast(1); val bh = (ih * sc).toInt().coerceAtLeast(1)
                                val b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888); it.setBounds(0, 0, bw, bh); it.draw(Canvas(b)); b.asImageBitmap()
                            }
                        } catch (_: Exception) { null }
                    }
                }
                Row(Modifier.fillMaxWidth().clickable { actions?.bindWidget(p); onDismiss() }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(96.dp, 72.dp).clip(RoundedCornerShape(12.dp)).background(colors.onSurface.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
                        preview?.let { Image(it, null, Modifier.heightIn(max = 72.dp)) }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.label, color = colors.onSurface, fontFamily = fonts.body, fontSize = 16.sp, maxLines = 2)
                        Text("размер " + cells(p.minWidth, ctx) + "×" + cells(p.minHeight, ctx), color = colors.onSurface.copy(alpha = 0.6f), fontSize = 13.sp)
                    }
                }
            }
          }
        }
    }
}

private class PickerEntry(val info: AppWidgetProviderInfo, val label: String, val key: SearchKey)

private fun cells(px: Int, ctx: Context): Int = ((px / ctx.resources.displayMetrics.density + 30) / 70).toInt().coerceAtLeast(1)

interface WidgetBinder {
    fun bindWidget(info: AppWidgetProviderInfo)
    /** 1.3.9: built-in widgets (currently the clock) go straight onto the current home page. */
    fun addBuiltInClock()
    /** 1.3.9: opens the widget's own settings screen again («Настроить»). */
    fun reconfigureWidget(appWidgetId: Int)
}
