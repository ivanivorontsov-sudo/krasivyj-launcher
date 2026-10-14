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
    val list: List<Pair<String, String>> = listOf(
        "Владивосток" to "Asia/Vladivostok", "Хабаровск" to "Asia/Vladivostok", "Находка" to "Asia/Vladivostok", "Уссурийск" to "Asia/Vladivostok",
        "Москва" to "Europe/Moscow", "Санкт-Петербург" to "Europe/Moscow", "Казань" to "Europe/Moscow", "Нижний Новгород" to "Europe/Moscow",
        "Ростов-на-Дону" to "Europe/Moscow", "Краснодар" to "Europe/Moscow", "Сочи" to "Europe/Moscow", "Воронеж" to "Europe/Moscow", "Симферополь" to "Europe/Simferopol",
        "Калининград" to "Europe/Kaliningrad", "Самара" to "Europe/Samara", "Ижевск" to "Europe/Samara", "Саратов" to "Europe/Saratov", "Волгоград" to "Europe/Volgograd",
        "Ульяновск" to "Europe/Ulyanovsk", "Астрахань" to "Europe/Astrakhan", "Екатеринбург" to "Asia/Yekaterinburg", "Пермь" to "Asia/Yekaterinburg",
        "Челябинск" to "Asia/Yekaterinburg", "Уфа" to "Asia/Yekaterinburg", "Тюмень" to "Asia/Yekaterinburg", "Омск" to "Asia/Omsk",
        "Новосибирск" to "Asia/Novosibirsk", "Барнаул" to "Asia/Barnaul", "Томск" to "Asia/Tomsk", "Новокузнецк" to "Asia/Novokuznetsk", "Кемерово" to "Asia/Novokuznetsk",
        "Красноярск" to "Asia/Krasnoyarsk", "Норильск" to "Asia/Krasnoyarsk", "Иркутск" to "Asia/Irkutsk", "Улан-Удэ" to "Asia/Irkutsk", "Чита" to "Asia/Chita",
        "Якутск" to "Asia/Yakutsk", "Благовещенск" to "Asia/Yakutsk", "Магадан" to "Asia/Magadan", "Южно-Сахалинск" to "Asia/Sakhalin",
        "Петропавловск-Камчатский" to "Asia/Kamchatka", "Анадырь" to "Asia/Anadyr", "Минск" to "Europe/Minsk", "Киев" to "Europe/Kyiv",
        "Астана" to "Asia/Almaty", "Алматы" to "Asia/Almaty", "Ташкент" to "Asia/Tashkent", "Бишкек" to "Asia/Bishkek", "Душанбе" to "Asia/Dushanbe",
        "Баку" to "Asia/Baku", "Ереван" to "Asia/Yerevan", "Тбилиси" to "Asia/Tbilisi", "Кишинёв" to "Europe/Chisinau", "Рига" to "Europe/Riga",
        "Вильнюс" to "Europe/Vilnius", "Таллин" to "Europe/Tallinn", "Улан-Батор" to "Asia/Ulaanbaatar",
        "Пекин" to "Asia/Shanghai", "Шанхай" to "Asia/Shanghai", "Харбин" to "Asia/Shanghai", "Гонконг" to "Asia/Hong_Kong", "Тайбэй" to "Asia/Taipei",
        "Сеул" to "Asia/Seoul", "Токио" to "Asia/Tokyo", "Пхеньян" to "Asia/Pyongyang", "Бангкок" to "Asia/Bangkok", "Ханой" to "Asia/Bangkok",
        "Сингапур" to "Asia/Singapore", "Куала-Лумпур" to "Asia/Kuala_Lumpur", "Джакарта" to "Asia/Jakarta", "Манила" to "Asia/Manila",
        "Дели" to "Asia/Kolkata", "Мумбаи" to "Asia/Kolkata", "Дубай" to "Asia/Dubai", "Абу-Даби" to "Asia/Dubai", "Тегеран" to "Asia/Tehran",
        "Стамбул" to "Europe/Istanbul", "Анкара" to "Europe/Istanbul", "Анталья" to "Europe/Istanbul", "Тель-Авив" to "Asia/Jerusalem", "Каир" to "Africa/Cairo",
        "Лондон" to "Europe/London", "Париж" to "Europe/Paris", "Берлин" to "Europe/Berlin", "Рим" to "Europe/Rome", "Мадрид" to "Europe/Madrid",
        "Прага" to "Europe/Prague", "Варшава" to "Europe/Warsaw", "Вена" to "Europe/Vienna", "Афины" to "Europe/Athens", "Хельсинки" to "Europe/Helsinki",
        "Белград" to "Europe/Belgrade", "Нью-Йорк" to "America/New_York", "Вашингтон" to "America/New_York", "Чикаго" to "America/Chicago",
        "Денвер" to "America/Denver", "Лос-Анджелес" to "America/Los_Angeles", "Сан-Франциско" to "America/Los_Angeles", "Ванкувер" to "America/Vancouver",
        "Торонто" to "America/Toronto", "Мехико" to "America/Mexico_City", "Гавана" to "America/Havana", "Сан-Паулу" to "America/Sao_Paulo",
        "Буэнос-Айрес" to "America/Argentina/Buenos_Aires", "Сидней" to "Australia/Sydney", "Мельбурн" to "Australia/Melbourne", "Окленд" to "Pacific/Auckland",
        "Гонолулу" to "Pacific/Honolulu", "Анкоридж" to "America/Anchorage", "Йоханнесбург" to "Africa/Johannesburg", "Найроби" to "Africa/Nairobi",
        "UTC (всемирное)" to "UTC",
    ).filter { (_, z) -> TimeZone.getAvailableIDs().contains(z) || z == "UTC" }

    fun search(q: String): List<Pair<String, String>> {
        val s = q.trim().lowercase().replace('ё', 'е')
        if (s.isEmpty()) return list
        return list.filter { (c, z) -> c.lowercase().replace('ё', 'е').contains(s) || z.lowercase().contains(s) }
    }

    fun nameFor(zoneId: String): String? = list.firstOrNull { it.second == zoneId }?.first
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
