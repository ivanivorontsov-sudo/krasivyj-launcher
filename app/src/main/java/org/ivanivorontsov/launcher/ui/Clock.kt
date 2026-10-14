package org.ivanivorontsov.launcher.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ivanivorontsov.launcher.LauncherApp
import org.ivanivorontsov.launcher.core.ClockStyle
import org.ivanivorontsov.launcher.data.Device
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ru = Locale("ru")
fun timeText(t: Long): String = SimpleDateFormat("HH:mm", ru).format(Date(t))
fun dateText(t: Long): String = SimpleDateFormat("EEEE, d MMMM", ru).format(Date(t)).replaceFirstChar { it.uppercase() }
fun dayOfWeek(t: Long): String = SimpleDateFormat("EEEE", ru).format(Date(t))
fun dayNumber(t: Long): String = SimpleDateFormat("d", ru).format(Date(t))
fun monthText(t: Long): String = SimpleDateFormat("d MMMM", ru).format(Date(t))

@Composable
fun nowMinute(): Long {
    val t by LauncherApp.instance.live.minute.collectAsStateCompat()
    return t
}

@Composable
fun ClockHeader(modifier: Modifier = Modifier, styleOverride: ClockStyle? = null) {
    val spec = LocalSpec.current; val colors = LocalColors.current; val fonts = LocalFonts.current
    val ctx = LocalContext.current
    val t = nowMinute()
    val alarm = remember(t) { LauncherApp.instance.live.nextAlarm() }
    val style = styleOverride ?: spec.clockStyle
    val cfg by LauncherApp.instance.store.config.collectAsStateCompat()
    val zones = cfg.zonesShown()
    val main = zones[0]
    val extra = zones.drop(1)
    fun timeText(t: Long) = timeIn(t, main)
    fun dateText(t: Long): String = (if (extra.isNotEmpty()) zoneCaption(main) + " · " else "") + org.ivanivorontsov.launcher.ui.dateText(t)
    val c = colors.onWallpaper
    val tap = Modifier.pressFeedback(onClick = { Device.openClock(ctx) }, onLongClick = null, tiltOverride = false)
    if (style == ClockStyle.NONE) return
    // 1.2: the clock never overflows its slot. Measured at its natural size and scaled down uniformly to fit
    // whatever the home page gives it (small screens, 2-row slot, big system font scale, wide script fonts).
    val start = style == ClockStyle.STACKED || style == ClockStyle.TERMINAL
    FitBox(modifier, boundedWidth = style == ClockStyle.CARD, alignStart = start) {
        when (style) {
            ClockStyle.NONE -> {}
            ClockStyle.BIG -> Column(Modifier.then(tap).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(timeText(t), style = labelStyle(c, 76.sp).copy(fontFamily = fonts.clock, fontWeight = FontWeight(spec.clockWeight)))
                Text(spec.withCase(dateText(t)) + (alarm?.let { "  ·  ⏰ $it" } ?: ""), style = labelStyle(c, 15.sp).copy(fontFamily = fonts.heading))
                ExtraZonesRow(extra, t)
            }
            ClockStyle.STACKED -> Column(Modifier.then(tap).padding(horizontal = 28.dp)) {
                val (h, m) = timeText(t).split(":")
                Text(h, style = labelStyle(c, 92.sp).copy(fontFamily = fonts.clock, fontWeight = FontWeight(spec.clockWeight), lineHeight = 88.sp))
                Text(m, style = labelStyle(colors.accent, 92.sp).copy(fontFamily = fonts.clock, fontWeight = FontWeight(spec.clockWeight), lineHeight = 88.sp))
                Text(spec.withCase(dateText(t)), style = labelStyle(c, 16.sp).copy(fontFamily = fonts.heading))
                ExtraZonesRow(extra, t, start = true)
            }
            ClockStyle.SCRIPT -> Column(Modifier.then(tap).padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(timeText(t), style = labelStyle(c, 84.sp).copy(fontFamily = fonts.clock))
                Text(dateText(t), style = labelStyle(c, 24.sp).copy(fontFamily = fonts.heading))
                ExtraZonesRow(extra, t)
            }
            ClockStyle.TERMINAL -> Column(Modifier.then(tap).padding(horizontal = 22.dp)) {
                val blink = rememberInfiniteTransition(label = "cursor")
                val a by blink.animateFloat(1f, 0f, infiniteRepeatable(tween(530, easing = { if (it < 0.5f) 0f else 1f }), RepeatMode.Restart), label = "a")
                Text("ivan@uyut:~$ date", style = labelStyle(c.copy(alpha = 0.7f), 14.sp).copy(fontFamily = fonts.body))
                Text(timeText(t), style = labelStyle(c, 64.sp).copy(fontFamily = fonts.clock))
                Text(dateText(t).lowercase() + (alarm?.let { "  [будильник $it]" } ?: ""), style = labelStyle(c, 14.sp).copy(fontFamily = fonts.body))
                ExtraZonesRow(extra, t, start = true)
                Row { Text("ivan@uyut:~$ ", style = labelStyle(c, 14.sp).copy(fontFamily = fonts.body)); Text("█", style = labelStyle(c, 14.sp), modifier = Modifier.alpha(a)) }
            }
            ClockStyle.CARD -> Box(Modifier.padding(horizontal = 22.dp).fillMaxWidth().height(if (extra.isEmpty()) 156.dp else 226.dp).glass(spec.cornerDp.dp, colors, 0.14f).then(tap).padding(18.dp)) {
                Column(Modifier.align(Alignment.TopStart)) {
                    Text(dayOfWeek(t).uppercase(), style = labelStyle(colors.accent.copy(alpha = 1f), 13.sp).copy(fontFamily = fonts.heading, fontWeight = FontWeight.SemiBold))
                    Text(dayNumber(t), style = labelStyle(c, 40.sp).copy(fontFamily = fonts.heading, fontWeight = FontWeight.Light))
                }
                if (extra.isNotEmpty()) ExtraZonesRow(extra, t, Modifier.align(Alignment.BottomStart), start = true)
                Column(Modifier.align(if (extra.isEmpty()) Alignment.BottomEnd else Alignment.TopEnd), horizontalAlignment = Alignment.End) {
                    Text(timeText(t), style = labelStyle(c, 54.sp).copy(fontFamily = fonts.clock, fontWeight = FontWeight(spec.clockWeight)), textAlign = TextAlign.End)
                    Text(alarm?.let { "Будильник $it" } ?: ((if (extra.isNotEmpty()) zoneCaption(main) + " · " else "") + monthText(t)), style = labelStyle(c.copy(alpha = 0.8f), 13.sp))
                }
            }
        }
    }
}

/** Lays out [content] at its natural size and scales it down (never up) so it fits the incoming constraints. */
@Composable
fun FitBox(modifier: Modifier = Modifier, boundedWidth: Boolean = false, alignStart: Boolean = false, content: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(content, modifier) { ms, c ->
        val child = androidx.compose.ui.unit.Constraints(
            maxWidth = if (boundedWidth && c.hasBoundedWidth) c.maxWidth else androidx.compose.ui.unit.Constraints.Infinity)
        val ps = ms.map { it.measure(child) }
        val w = (ps.maxOfOrNull { it.width } ?: 0).coerceAtLeast(1)
        val h = (ps.maxOfOrNull { it.height } ?: 0).coerceAtLeast(1)
        val sw = if (c.hasBoundedWidth) c.maxWidth.toFloat() / w else 1f
        val sh = if (c.hasBoundedHeight) c.maxHeight.toFloat() / h else 1f
        val s = minOf(1f, sw, sh)
        val outW = (if (c.hasBoundedWidth && c.minWidth > 0) c.maxWidth else (w * s).toInt()).coerceIn(c.minWidth, c.maxWidth)
        val outH = (h * s).toInt().coerceIn(c.minHeight, c.maxHeight)
        layout(outW, outH) {
            ps.forEach { p ->
                val x = if (alignStart) ((p.width * s - p.width) / 2f).toInt() else (outW - p.width) / 2
                p.placeWithLayer(x, (outH - p.height) / 2) { scaleX = s; scaleY = s }
            }
        }
    }
}

@Composable
fun SpacerRow(h: Int) = Row(Modifier.height(h.dp), horizontalArrangement = Arrangement.Start) {}
