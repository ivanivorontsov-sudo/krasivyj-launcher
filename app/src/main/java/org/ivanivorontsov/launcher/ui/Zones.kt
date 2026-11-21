package org.ivanivorontsov.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ivanivorontsov.launcher.core.ClockZone
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** 1.3.8: Russian city names → IANA zones, for the search in settings. */
object Cities {
    val list: List<Pair<String, String>> get() = org.ivanivorontsov.launcher.core.TimeZones.cities
    fun search(q: String): List<Pair<String, String>> = org.ivanivorontsov.launcher.core.TimeZones.search(q)
    fun nameFor(zoneId: String): String? = org.ivanivorontsov.launcher.core.TimeZones.nameFor(zoneId)
}

fun tzOf(z: ClockZone): TimeZone = if (z.zoneId.isBlank()) TimeZone.getDefault() else TimeZone.getTimeZone(z.zoneId)

fun zoneCaption(z: ClockZone): String = z.label.ifBlank { if (z.zoneId.isBlank()) "Здесь" else Cities.nameFor(z.zoneId) ?: z.zoneId.substringAfterLast('/').replace('_', ' ') }

fun timeIn(t: Long, z: ClockZone): String = SimpleDateFormat("HH:mm", Locale("ru")).apply { timeZone = tzOf(z) }.format(Date(t))

fun utcOffset(t: Long, z: ClockZone): String {
    val m = tzOf(z).getOffset(t) / 60_000; val sign = if (m < 0) "−" else "+"; val a = kotlin.math.abs(m)
    return "UTC$sign${a / 60}" + if (a % 60 != 0) ":%02d".format(a % 60) else ""
}

/** "сегодня" / "завтра" / "вчера" relative to the phone's own date. */
fun dayShift(t: Long, z: ClockZone): String {
    fun day(tz: TimeZone) = java.util.Calendar.getInstance(tz).apply { timeInMillis = t }.let { it.get(java.util.Calendar.YEAR) * 400 + it.get(java.util.Calendar.DAY_OF_YEAR) }
    val d = day(tzOf(z)) - day(TimeZone.getDefault())
    return when { d > 0 -> "завтра"; d < 0 -> "вчера"; else -> "сегодня" }
}

/**
 * 1.3.8: second and third time zones under the main clock. Equal columns; long captions ellipsize,
 * so 2 and 3 zones always fit one row.
 */
@Composable
fun ExtraZonesRow(zones: List<ClockZone>, t: Long, modifier: Modifier = Modifier, start: Boolean = false) {
    if (zones.isEmpty()) return
    val colors = LocalColors.current; val fonts = LocalFonts.current; val spec = LocalSpec.current
    val c = colors.onWallpaper
    Row(modifier.padding(top = 10.dp), horizontalArrangement = if (start) Arrangement.spacedBy(22.dp) else Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally)) {
        zones.forEach { z ->
            Column(Modifier.padding(horizontal = 2.dp), horizontalAlignment = if (start) Alignment.Start else Alignment.CenterHorizontally) {
                Text(zoneCaption(z), style = labelStyle(c.copy(alpha = 0.8f), 13.sp).copy(fontFamily = fonts.heading), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthMaxDp(120))
                Text(timeIn(t, z), style = labelStyle(c, if (zones.size == 1) 30.sp else 26.sp).copy(fontFamily = fonts.clock, fontWeight = FontWeight(spec.clockWeight)))
                Text(dayShift(t, z) + " · " + utcOffset(t, z), style = labelStyle(c.copy(alpha = 0.65f), 11.sp), maxLines = 1)
            }
        }
    }
}

private fun Modifier.widthMaxDp(v: Int) = this.widthIn(max = v.dp)
