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
    val c = colors.onWallpaper
    val tap = Modifier.pressFeedback(onClick = { Device.openClock(ctx) }, onLongClick = null, tiltOverride = false)
    when (style) {
        ClockStyle.NONE -> {}
        ClockStyle.BIG -> Column(modifier.then(tap).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(timeText(t), style = labelStyle(c, 76.sp).copy(fontFamily = fonts.clock, fontWeight = FontWeight(spec.clockWeight)))
            Text(spec.withCase(dateText(t)) + (alarm?.let { "  ·  ⏰ $it" } ?: ""), style = labelStyle(c, 15.sp).copy(fontFamily = fonts.heading))
        }
        ClockStyle.STACKED -> Column(modifier.then(tap).padding(horizontal = 28.dp)) {
            val (h, m) = timeText(t).split(":")
            Text(h, style = labelStyle(c, 92.sp).copy(fontFamily = fonts.clock, fontWeight = FontWeight(spec.clockWeight), lineHeight = 88.sp))
            Text(m, style = labelStyle(colors.accent, 92.sp).copy(fontFamily = fonts.clock, fontWeight = FontWeight(spec.clockWeight), lineHeight = 88.sp))
            Text(spec.withCase(dateText(t)), style = labelStyle(c, 16.sp).copy(fontFamily = fonts.heading))
        }
        ClockStyle.SCRIPT -> Column(modifier.then(tap).padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(timeText(t), style = labelStyle(c, 84.sp).copy(fontFamily = fonts.clock))
            Text(dateText(t), style = labelStyle(c, 24.sp).copy(fontFamily = fonts.heading))
        }
        ClockStyle.TERMINAL -> Column(modifier.then(tap).padding(horizontal = 22.dp)) {
            val blink = rememberInfiniteTransition(label = "cursor")
            val a by blink.animateFloat(1f, 0f, infiniteRepeatable(tween(530, easing = { if (it < 0.5f) 0f else 1f }), RepeatMode.Restart), label = "a")
            Text("ivan@uyut:~$ date", style = labelStyle(c.copy(alpha = 0.7f), 14.sp).copy(fontFamily = fonts.body))
            Text(timeText(t), style = labelStyle(c, 64.sp).copy(fontFamily = fonts.clock))
            Text(dateText(t).lowercase() + (alarm?.let { "  [будильник $it]" } ?: ""), style = labelStyle(c, 14.sp).copy(fontFamily = fonts.body))
            Row { Text("ivan@uyut:~$ ", style = labelStyle(c, 14.sp).copy(fontFamily = fonts.body)); Text("█", style = labelStyle(c, 14.sp), modifier = Modifier.alpha(a)) }
        }
        ClockStyle.CARD -> Box(modifier.padding(horizontal = 22.dp).fillMaxWidth().height(156.dp).glass(spec.cornerDp.dp, colors, 0.14f).then(tap).padding(18.dp)) {
            Column(Modifier.align(Alignment.TopStart)) {
                Text(dayOfWeek(t).uppercase(), style = labelStyle(colors.accent.copy(alpha = 1f), 13.sp).copy(fontFamily = fonts.heading, fontWeight = FontWeight.SemiBold))
                Text(dayNumber(t), style = labelStyle(c, 40.sp).copy(fontFamily = fonts.heading, fontWeight = FontWeight.Light))
            }
            Column(Modifier.align(Alignment.BottomEnd), horizontalAlignment = Alignment.End) {
                Text(timeText(t), style = labelStyle(c, 54.sp).copy(fontFamily = fonts.clock, fontWeight = FontWeight(spec.clockWeight)), textAlign = TextAlign.End)
                Text(alarm?.let { "Будильник $it" } ?: monthText(t), style = labelStyle(c.copy(alpha = 0.8f), 13.sp))
            }
        }
    }
}

@Composable
fun SpacerRow(h: Int) = Row(Modifier.height(h.dp), horizontalArrangement = Arrangement.Start) {}
