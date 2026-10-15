package org.ivanivorontsov.launcher.ui

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.withTimeoutOrNull
import org.ivanivorontsov.launcher.LauncherApp
import org.ivanivorontsov.launcher.core.ClockStyle
import org.ivanivorontsov.launcher.core.HomeWidget
import org.ivanivorontsov.launcher.core.HomeWidgetKind
import org.ivanivorontsov.launcher.core.resizeWidget
import org.ivanivorontsov.launcher.core.updateWidget
import kotlin.math.roundToInt

/**
 * 1.3.9: a widget placed on a home page. Long press → edit mode (frame with resize handles, move by dragging,
 * «Настроить» / «Изменить» / «Удалить» / «Готово»); long press + move → drag it to another place or page,
 * like an icon. Sizes are in grid cells ([cellW] × [cellH] px each).
 */
@Composable
fun HomeWidgetCell(ui: UiState, w: HomeWidget, cellW: Int, cellH: Int, cols: Int, rows: Int, modifier: Modifier = Modifier) {
    val ref = remember(w.id) { BoundsRef() }
    RegisterIcon(ui, w.id, ref)
    val editing = ui.homeWidgetEdit == w.id
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val dragFactory = rememberUpdatedState { local: Offset ->
        DragItem(w.id, null, DragSource.HOME, size = Size((cellW * w.spanX).toFloat(), (cellH * w.spanY).toFloat()), grab = local, startBounds = ref.bounds())
    }
    Box(modifier.trackBounds(ref).launcherItemVisibility(ui, w.id)) {
        Box(Modifier.fillMaxSize().padding(4.dp).widgetLongPress(
            onLongPress = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); ui.homeWidgetEdit = w.id },
            onDragStart = { local -> ref.bounds()?.let { b -> ui.homeWidgetEdit = null; ui.startDrag(dragFactory.value(local), b.topLeft + local) } },
        )) {
            when (w.kind) {
                HomeWidgetKind.CLOCK -> ClockHeader(Modifier.fillMaxSize(), w.clockStyle)
                HomeWidgetKind.SYSTEM -> SystemWidgetView(w, cellW * w.spanX, cellH * w.spanY)
            }
        }
        if (editing) WidgetEditFrame(ui, w, cellW, cellH, cols, rows, onDragStart = { local ->
            ref.bounds()?.let { b -> ui.homeWidgetEdit = null; ui.startDrag(dragFactory.value(local), b.topLeft + local) }
        })
    }
    @Suppress("UNUSED_EXPRESSION") density
}

/**
 * Long press without stealing ordinary touches: watches the Initial pass (before the widget's own views see it),
 * and only after the long-press timeout consumes the rest of the gesture (the Android widget then gets CANCEL).
 */
private fun Modifier.widgetLongPress(onLongPress: () -> Unit, onDragStart: (Offset) -> Unit): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val slop = viewConfiguration.touchSlop
        var fired = false
        val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                val c = ev.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                if (!c.pressed) return@withTimeoutOrNull true
                if ((c.position - down.position).getDistance() > slop) return@withTimeoutOrNull true
            }
            @Suppress("UNREACHABLE_CODE") true
        }
        if (ended != null) return@awaitEachGesture
        fired = true
        onLongPress()
        while (fired) {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            val c = ev.changes.firstOrNull { it.id == down.id } ?: break
            c.consume()
            if (!c.pressed) break
            if ((c.position - down.position).getDistance() > slop * 1.5f) { onDragStart(c.position); break }
        }
    }
}

@Composable
private fun SystemWidgetView(w: HomeWidget, wPx: Int, hPx: Int) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val awm = remember { AppWidgetManager.getInstance(ctx) }
    val info: AppWidgetProviderInfo? = remember(w.appWidgetId) { try { awm.getAppWidgetInfo(w.appWidgetId) } catch (_: Exception) { null } }
    val colors = LocalColors.current
    if (info == null) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(colors.onWallpaper.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Text("Виджет недоступен", color = colors.onWallpaper, fontSize = 13.sp)
        }
        return
    }
    val cfg by LauncherApp.instance.store.config.collectAsStateCompat()
    if (cfg.diagNoWidgets) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(colors.onWallpaper.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
            Text(info.loadLabel(ctx.packageManager), color = colors.onWallpaper, fontSize = 13.sp)
        }
        return
    }
    AndroidView(
        factory = { c -> WidgetHostHolder.host(c).createView(c.applicationContext, w.appWidgetId, info).apply { setPadding(0, 0, 0, 0) } },
        update = { v: AppWidgetHostView ->
            val wd = (wPx / density.density).toInt().coerceAtLeast(40); val hd = (hPx / density.density).toInt().coerceAtLeast(40)
            val tag = wd * 10_000 + hd
            if (v.tag != tag) { v.tag = tag; @Suppress("DEPRECATION") v.updateAppWidgetSize(Bundle(), wd, hd, wd, hd) }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun WidgetEditFrame(ui: UiState, w: HomeWidget, cellW: Int, cellH: Int, cols: Int, rows: Int, onDragStart: (Offset) -> Unit) {
    val colors = LocalColors.current
    val store = LauncherApp.instance.store
    val ctx = LocalContext.current
    var dx by remember { mutableStateOf(0f) }
    var dy by remember { mutableStateOf(0f) }
    var clockDialog by remember { mutableStateOf(false) }
    fun resize(sx: Int, sy: Int) = store.update { c -> c.editLayout(ui.themeId) { it.resizeWidget(w.id, sx, sy, cols, rows) } }
    val accent = colors.accent
    Box(Modifier.fillMaxSize()
        .pointerInput(w.id) { detectDragGestures(onDragStart = { onDragStart(it) }) { ch, _ -> ch.consume() } }
        .border(2.dp, accent, RoundedCornerShape(14.dp))
        .background(accent.copy(alpha = 0.10f), RoundedCornerShape(14.dp))) {
        // Resize handles: right edge (width), bottom edge (height), corner (both). Snaps to whole cells.
        @Composable
        fun Handle(align: Alignment, horiz: Boolean, vert: Boolean) {
            Box(Modifier.align(align).offset { IntOffset(if (horiz) dx.roundToInt() else 0, if (vert) dy.roundToInt() else 0) }
                .size(34.dp).padding(5.dp).clip(CircleShape).background(accent).border(2.dp, Color.White, CircleShape)
                .pointerInput(w.id, horiz, vert) {
                    detectDragGestures(
                        onDragEnd = { dx = 0f; dy = 0f },
                        onDragCancel = { dx = 0f; dy = 0f },
                    ) { ch, d ->
                        ch.consume()
                        if (horiz) dx += d.x
                        if (vert) dy += d.y
                        val sx = if (horiz) ((w.spanX * cellW + dx) / cellW).roundToInt().coerceIn(1, cols) else w.spanX
                        val sy = if (vert) ((w.spanY * cellH + dy) / cellH).roundToInt().coerceIn(1, rows) else w.spanY
                        if (sx != w.spanX || sy != w.spanY) {
                            if (horiz) dx -= (sx - w.spanX) * cellW
                            if (vert) dy -= (sy - w.spanY) * cellH
                            resize(sx, sy)
                        }
                    }
                })
        }
        Handle(Alignment.CenterEnd, true, false)
        Handle(Alignment.BottomCenter, false, true)
        Handle(Alignment.BottomEnd, true, true)
        Row(Modifier.align(Alignment.TopCenter).padding(6.dp).clip(RoundedCornerShape(50)).background(colors.surface.copy(alpha = 0.95f)).padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            @Composable fun Btn(t: String, danger: Boolean = false, f: () -> Unit) =
                Text(t, color = if (danger) Color(0xFFE5484D) else colors.onSurface, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(50)).clickable { f() }.padding(horizontal = 9.dp, vertical = 7.dp))
            if (w.kind == HomeWidgetKind.CLOCK) Btn("Изменить") { clockDialog = true }
            else {
                val info = remember(w.appWidgetId) { try { AppWidgetManager.getInstance(ctx).getAppWidgetInfo(w.appWidgetId) } catch (_: Exception) { null } }
                if (info?.configure != null) Btn("Настроить") { (ui.actions as? WidgetBinder)?.reconfigureWidget(w.appWidgetId) }
            }
            Btn("${w.spanX}×${w.spanY}") {}
            Btn("Удалить", danger = true) {
                ui.homeWidgetEdit = null
                store.update { c -> c.editLayout(ui.themeId) { it.remove(w.id) } }
                if (w.kind == HomeWidgetKind.SYSTEM) try { WidgetHostHolder.host(ctx).deleteAppWidgetId(w.appWidgetId) } catch (_: Exception) {}
            }
            Btn("Готово") { ui.homeWidgetEdit = null }
        }
    }
    if (clockDialog) ClockWidgetDialog(ui, w) { clockDialog = false }
}

/** «Изменить» for the clock widget: look, how many time zones and which ones. Size is changed with the frame handles. */
@Composable
private fun ClockWidgetDialog(ui: UiState, w: HomeWidget, onDismiss: () -> Unit) {
    val store = LauncherApp.instance.store
    val cfg by store.config.collectAsStateCompat()
    val spec = LocalSpec.current
    var editing by remember { mutableIntStateOf(-1) }
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
        title = { Text("Часы") },
        text = {
            Column {
                Text("Вид", fontSize = 14.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(w.clockStyle == null, { store.update { c -> c.editLayout(ui.themeId) { it.updateWidget(w.id) { x -> x.copy(clockStyle = null) } } } },
                        label = { Text("Как в теме (${spec.clockStyle.title})") })
                    ClockStyle.entries.filter { it != ClockStyle.NONE }.forEach { st ->
                        FilterChip(w.clockStyle == st, { store.update { c -> c.editLayout(ui.themeId) { it.updateWidget(w.id) { x -> x.copy(clockStyle = st) } } } }, label = { Text(st.title) })
                    }
                }
                Text("Часовых поясов", fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..3).forEach { n -> FilterChip(cfg.clockZoneCount.coerceIn(1, 3) == n, { store.update { it.copy(clockZoneCount = n) } }, label = { Text("$n") }) }
                }
                (0 until cfg.clockZoneCount.coerceIn(1, 3)).forEach { i ->
                    val z = cfg.zone(i)
                    Text("Время ${i + 1}: " + zoneCaption(z) + "  ›", fontSize = 15.sp, modifier = Modifier.fillMaxWidth().clickable { editing = i }.padding(vertical = 8.dp))
                }
                Text("Размер — потяните за кружки на рамке. Переместить — потяните за сам виджет.", fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        })
    if (editing >= 0) ZoneDialog(cfg.zone(editing), onDismiss = { editing = -1 }) { nz ->
        val i = editing
        store.update { c -> val l = (0 until maxOf(3, c.clockZones.size)).map { c.zone(it) }.toMutableList(); l[i] = nz; c.copy(clockZones = l) }
        editing = -1
    }
}
