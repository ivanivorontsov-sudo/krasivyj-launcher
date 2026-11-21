package org.ivanivorontsov.launcher.core

import java.util.TimeZone

/** Russian city names → IANA time zones. Pure JVM, used by AI settings and the clock-widget search. */
object TimeZones {
    val cities: List<Pair<String, String>> = listOf(
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
    )

    fun isValidIana(id: String): Boolean {
        if (id.equals("UTC", ignoreCase = true) || id.equals("GMT", ignoreCase = true)) return true
        return TimeZone.getAvailableIDs().any { it.equals(id, ignoreCase = true) }
    }

    fun nameFor(zoneId: String): String? = cities.firstOrNull { it.second.equals(zoneId, ignoreCase = true) }?.first

    /**
     * Resolves a Russian city, a common alias or an IANA id to (label, zoneId).
     * «Москва» → Europe/Moscow, «Владивосток» → Asia/Vladivostok.
     */
    fun resolve(query: String): Pair<String, String>? {
        val raw = query.trim().trim('«', '»', '"', '“', '”')
        if (raw.isEmpty()) return null
        val n0 = normalize(raw)
        val n = n0.replace(Regex("^(в|во|на|из|к|ко|у|от|до|по|для)\\s+"), "")
        if (n == "utc" || n == "gmt" || n == "всемирное") return "UTC" to "UTC"
        cities.firstOrNull { it.second.equals(raw, ignoreCase = true) }?.let { return it.first to it.second }
        TimeZone.getAvailableIDs().firstOrNull { it.equals(raw, ignoreCase = true) }?.let { id ->
            return (nameFor(id) ?: id.substringAfterLast('/').replace('_', ' ')) to id
        }
        cities.firstOrNull { normalize(it.first) == n }?.let { return it }
        val fromStem = cities.filter { c ->
            val cn = normalize(c.first)
            stems(n).any { s -> s.length >= 4 && (cn == s || cn.startsWith(s)) } ||
                stems(cn).any { s -> s.length >= 4 && (n == s || n.startsWith(s)) }
        }
        if (fromStem.size == 1) return fromStem[0]
        fromStem.firstOrNull { normalize(it.first) == n || stems(n).any { s -> normalize(it.first) == s } }?.let { return it }
        val hits = cities.filter { normalize(it.first).contains(n) || n.contains(normalize(it.first)) }
        if (hits.size == 1) return hits[0]
        if (hits.isNotEmpty()) {
            hits.firstOrNull { normalize(it.first).startsWith(n) }?.let { return it }
        }
        return fromStem.firstOrNull()
    }

    fun normalize(s: String): String = s.lowercase().replace('ё', 'е').replace('-', ' ').replace('_', ' ').trim()

    /** «Москвы», «Владивостока» → stems that match the nominative city name. */
    fun stems(s: String): List<String> {
        val n = normalize(s)
        val out = LinkedHashSet<String>()
        out += n
        val endings = listOf("ами", "ями", "ах", "ях", "ой", "ою", "ею", "ев", "ов", "ом", "ем", "ы", "и", "е", "у", "ю", "а", "я", "о")
        for (e in endings) if (n.endsWith(e) && n.length - e.length >= 4) out += n.dropLast(e.length)
        return out.toList()
    }


    fun search(q: String): List<Pair<String, String>> {
        val s = q.trim().lowercase().replace('ё', 'е')
        if (s.isEmpty()) return cities
        return cities.filter { (c, z) -> c.lowercase().replace('ё', 'е').contains(s) || z.lowercase().contains(s) }
    }
}
