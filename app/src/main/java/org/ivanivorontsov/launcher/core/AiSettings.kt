package org.ivanivorontsov.launcher.core

/**
 * Schema of every real launcher setting the AI may change, plus validation and a local
 * Russian-phrase parser. Pure Kotlin so unit tests run on the JVM.
 *
 * Unknown or unsupported requests are returned as [unsupported] and never applied silently.
 */
object AiSettings {

    enum class Kind { BOOL, ENUM, INT, FLOAT, STRING, TZ_LIST, THEME, WIDGET }

    data class Def(
        val key: String,
        val label: String,
        val kind: Kind,
        val group: String,
        val allowed: List<Pair<String, String>> = emptyList(),
        val min: Double? = null,
        val max: Double? = null,
    )

    data class Input(val key: String, val value: Any?, val raw: String? = null)

    data class Change(
        val key: String,
        val label: String,
        val from: String,
        val to: String,
    )

    data class ClockWidgetEdit(
        val add: Boolean = false,
        val remove: Boolean = false,
        val spanX: Int? = null,
        val spanY: Int? = null,
        val page: Int? = null,
        val col: Int? = null,
        val row: Int? = null,
        val style: ClockStyle? = null,
    )

    data class Proposal(
        val changes: List<Change> = emptyList(),
        val unsupported: List<String> = emptyList(),
        val nextConfig: LauncherConfig,
        val nextTheme: ThemeSpec,
        val widget: ClockWidgetEdit? = null,
        val themeChanged: Boolean = false,
    )

    fun catalog(): List<Def> = defs

    fun def(key: String): Def? = byKey[key.lowercase()] ?: aliases[key.lowercase()]?.let { byKey[it] }

    /** Validate a list of proposed setting writes against the schema and the current config/theme. */
    fun validate(
        inputs: List<Input>,
        config: LauncherConfig,
        theme: ThemeSpec,
        extraUnsupported: List<String> = emptyList(),
    ): Proposal {
        var cfg = config
        var th = theme
        val changes = ArrayList<Change>()
        val unsupported = ArrayList<String>()
        extraUnsupported.forEach { if (it.isNotBlank()) unsupported += it.trim() }
        var widget: ClockWidgetEdit? = null
        var themeChanged = false

        for (input in inputs) {
            val keyRaw = input.key.trim()
            if (keyRaw.isEmpty()) continue
            if (keyRaw.equals("unsupported", ignoreCase = true)) {
                when (val v = input.value) {
                    is List<*> -> v.mapNotNull { it?.toString()?.trim() }.filter { it.isNotEmpty() }.forEach { unsupported += it }
                    else -> input.value?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { unsupported += it }
                }
                continue
            }
            val d = def(keyRaw)
            if (d == null) {
                unsupported += "«$keyRaw» — не поддерживается"
                continue
            }
            when (d.kind) {
                Kind.WIDGET -> {
                    val edit = parseWidget(input.value)
                    if (edit == null) {
                        unsupported += "Виджет «${d.label}»: не поняла значение"
                    } else {
                        widget = (widget ?: ClockWidgetEdit()) + edit
                        val from = clockWidgetSummary(config.layoutFor(theme.id), theme)
                        val to = widgetSummary(widget, theme)
                        if (from != to) changes += Change(d.key, d.label, from, to)
                    }
                }
                Kind.TZ_LIST -> {
                    val zones = parseZones(input.value)
                    if (zones.resolved.isEmpty() && zones.bad.isNotEmpty()) {
                        unsupported += "Часовой пояс: не узнала «${zones.bad.joinToString(", ")}»"
                    } else {
                        zones.bad.forEach { unsupported += "Часовой пояс «$it» — не поддерживается" }
                        val next = zones.resolved.take(3)
                        if (next.isEmpty()) {
                            unsupported += "Часовой пояс: пустой список"
                        } else {
                            val from = formatZones(cfg.zonesShown())
                            cfg = cfg.copy(clockZones = next, clockZoneCount = next.size.coerceIn(1, 3))
                            val to = formatZones(cfg.zonesShown())
                            if (from != to) changes += Change(d.key, d.label, from, to)
                        }
                    }
                }
                Kind.THEME -> {
                    val name = stringify(input.value)
                    val preset = matchPreset(name)
                    if (preset == null) {
                        unsupported += "Тема «$name» — не поддерживается"
                        continue
                    }
                    if (preset.id != th.id || preset.name != th.name) {
                        changes += Change(d.key, d.label, th.name, preset.name)
                        th = preset
                        themeChanged = true
                    }
                }
                Kind.BOOL -> {
                    val v = parseBool(input.value)
                    if (v == null) {
                        unsupported += "«${d.label}»: ожидала да/нет, получила «${stringify(input.value)}»"
                    } else {
                        val from = boolLabel(readBool(cfg, th, d.key))
                        val applied = writeBool(cfg, th, d.key, v)
                        cfg = applied.first; th = applied.second
                        val to = boolLabel(v)
                        if (from != to) changes += Change(d.key, d.label, from, to)
                        if (d.group == "Тема" && from != to) themeChanged = true
                    }
                }
                Kind.ENUM -> {
                    val raw = stringify(input.value)
                    val picked = matchAllowed(raw, d.allowed)
                    if (picked == null) {
                        unsupported += "«${d.label}»: значение «$raw» не поддерживается. Можно: ${d.allowed.joinToString { it.second }}"
                    } else {
                        val from = enumLabel(readEnum(cfg, th, theme.id, d.key), d)
                        val applied = writeEnum(cfg, th, theme.id, d.key, picked)
                        cfg = applied.first; th = applied.second
                        val to = d.allowed.first { it.first == picked }.second
                        if (from != to) changes += Change(d.key, d.label, from, to)
                        if (d.group == "Тема" && from != to) themeChanged = true
                    }
                }
                Kind.INT -> {
                    val n = parseNumber(input.value)?.toInt()
                    if (n == null) {
                        unsupported += "«${d.label}»: ожидала число, получила «${stringify(input.value)}»"
                    } else {
                        val clamped = n.toDouble().let { v ->
                            val lo = d.min ?: v; val hi = d.max ?: v
                            v.coerceIn(lo, hi).toInt()
                        }
                        val from = readInt(cfg, th, d.key)?.toString() ?: "—"
                        val applied = writeInt(cfg, th, d.key, clamped)
                        cfg = applied.first; th = applied.second
                        if (from != clamped.toString()) changes += Change(d.key, d.label, from, clamped.toString())
                        if (d.group == "Тема" && from != clamped.toString()) themeChanged = true
                    }
                }
                Kind.FLOAT -> {
                    val n = parseNumber(input.value)
                    if (n == null) {
                        unsupported += "«${d.label}»: ожидала число, получила «${stringify(input.value)}»"
                    } else {
                        val clamped = n.coerceIn(d.min ?: n, d.max ?: n).toFloat()
                        val from = readFloat(cfg, th, d.key)?.let { fmtFloat(it) } ?: "—"
                        val applied = writeFloat(cfg, th, d.key, clamped)
                        cfg = applied.first; th = applied.second
                        val to = fmtFloat(clamped)
                        if (from != to) changes += Change(d.key, d.label, from, to)
                        if (d.group == "Тема" && from != to) themeChanged = true
                    }
                }
                Kind.STRING -> {
                    val s = stringify(input.value).trim()
                    if (s.isEmpty()) {
                        unsupported += "«${d.label}»: пустое значение"
                    } else if (d.key == "headingFont" || d.key == "bodyFont" || d.key == "clockFont") {
                        val id = matchFont(s)
                        if (id == null) {
                            unsupported += "Шрифт «$s» — не поддерживается"
                        } else {
                            val from = readString(th, d.key) ?: "—"
                            th = writeFont(th, d.key, id)
                            if (from != id) {
                                changes += Change(d.key, d.label, from, id)
                                themeChanged = true
                            }
                        }
                    } else if (d.key == "iconPack") {
                        val from = cfg.iconPack ?: "по умолчанию"
                        cfg = cfg.copy(iconPack = s)
                        if (from != s) changes += Change(d.key, d.label, from, s)
                    }
                }
            }
        }
        return Proposal(
            changes = changes,
            unsupported = unsupported.distinct(),
            nextConfig = cfg.sanitized(),
            nextTheme = th.sanitized(),
            widget = widget,
            themeChanged = themeChanged || th != theme,
        )
    }

    internal fun parseSettings(root: JsonMini.Obj): List<Input> {
        val out = ArrayList<Input>()
        for (o in root.objList("settings")) {
            val key = o.str("key") ?: o.str("name") ?: o.str("id") ?: continue
            out += Input(key, o.any("value") ?: o.any("to") ?: o.any("new"))
        }
        // Also accept a flat "settings" object: { "allAppsOnHome": true }
        val flat = root.obj("settings")
        if (flat != null && root.objList("settings").isEmpty()) {
            for ((k, v) in flat.entries()) out += Input(k, v)
        }
        return out
    }

    internal fun parseUnsupported(root: JsonMini.Obj): List<String> =
        root.stringList("unsupported") + root.stringList("unsupportedRequests") + root.stringList("unknown")

    /**
     * Local fallback for common Russian phrases about settings, widgets and time zones.
     * Arrangement phrases are handled separately in [AiLayout].
     */
    fun localParse(prompt: String, config: LauncherConfig, theme: ThemeSpec): Proposal {
        val detected = detectLocal(prompt)
        return validate(detected.first, config, theme, detected.second)
    }

    fun detectLocal(prompt: String): Pair<List<Input>, List<String>> {
        val inputs = ArrayList<Input>()
        val unsupported = ArrayList<String>()
        val t = norm(prompt)
        if (t.isBlank()) return emptyList<Input>() to emptyList()

        // all-apps-on-home
        when {
            Regex("верн\\p{L}*\\s+меню\\s+приложен|отдельн\\p{L}*\\s+меню\\s+приложен|выключ\\p{L}*\\s+все\\s+приложен").containsMatchIn(t) ->
                inputs += Input("allAppsOnHome", false)
            Regex("все\\s+приложен\\p{L}*\\s+на\\s+(главн|рабоч)|без\\s+меню\\s+приложен|убер\\p{L}*\\s+меню\\s+приложен|покаж\\p{L}*\\s+все\\s+приложен").containsMatchIn(t) ->
                inputs += Input("allAppsOnHome", true)
        }

        // theme preset
        Regex("(?:тем[аыу]\\s+|включи\\s+тем[уа]\\s+|поставь\\s+тем[уа]\\s+)[«\"“]?([^«»\"“”\\.,;]+)").findAll(prompt).forEach { m ->
            val name = m.groupValues[1].trim()
            if (matchPreset(name) != null) inputs += Input("theme", name)
            else if (!norm(name).matches(Regex("темн\\p{L}*|светл\\p{L}*|системн\\p{L}*"))) unsupported += "Тема «$name» — не поддерживается"
        }
        if (inputs.none { it.key == "theme" }) {
            for (p in Presets.all) {
                val n = norm(p.name)
                if (n.length >= 3 && (t.contains("тема " + n) || t.contains("тему " + n) || t.endsWith(n) && t.contains("тем"))) {
                    inputs += Input("theme", p.name)
                    break
                }
            }
        }

        when {
            Regex("светл\\p{L}*\\s+тем").containsMatchIn(t) -> inputs += Input("dark", false)
            Regex("т[её]мн\\p{L}*\\s+тем").containsMatchIn(t) -> inputs += Input("dark", true)
        }

        // page effect / animation
        val effect = matchPageEffect(t)
        if (effect != null && Regex("анимац|переход|эффект|лист\\p{L}*|страниц|цилиндр|стопка|колод|карусел|аккордеон|куб|волна|глубин").containsMatchIn(t)) {
            inputs += Input("pageEffect", effect)
        }

        Regex("сетк[ауи]\\s+(\\d+)\\s*(?:x|×|на|столб\\p{L}*|колон\\p{L}*)(?:\\s*(\\d+)\\s*(?:ряд\\p{L}*)?)?").find(t)?.let { m ->
            inputs += Input("columns", m.groupValues[1].toInt())
            m.groupValues[2].toIntOrNull()?.let { inputs += Input("rows", it) }
        }
        Regex("(\\d+)\\s*(?:столбц|столбик|колон)").find(t)?.let { inputs += Input("columns", it.groupValues[1].toInt()) }
        Regex("(\\d+)\\s*ряд").find(t)?.let { inputs += Input("rows", it.groupValues[1].toInt()) }

        when {
            Regex("кругл\\p{L}*\\s+икон").containsMatchIn(t) -> inputs += Input("iconShape", "CIRCLE")
            Regex("квадратн\\p{L}*\\s+икон").containsMatchIn(t) -> inputs += Input("iconShape", "SQUARE")
            Regex("сквиркл").containsMatchIn(t) -> inputs += Input("iconShape", "SQUIRCLE")
            Regex("икон\\p{L}*\\s+капл|капл\\p{L}*\\s+икон").containsMatchIn(t) -> inputs += Input("iconShape", "TEARDROP")
            Regex("соты|шестигран").containsMatchIn(t) -> inputs += Input("iconShape", "HEXAGON")
        }
        Regex("иконк\\p{L}*\\s+(\\d+)").find(t)?.let { inputs += Input("iconSizeDp", it.groupValues[1].toInt()) }
        when {
            Regex("крупн\\p{L}*\\s+икон").containsMatchIn(t) -> inputs += Input("iconSizeDp", 72)
            Regex("маленьк\\p{L}*\\s+икон|мелк\\p{L}*\\s+икон").containsMatchIn(t) -> inputs += Input("iconSizeDp", 40)
        }

        when {
            Regex("убер\\p{L}*\\s+док|без\\s+дока|спряч\\p{L}*\\s+док").containsMatchIn(t) -> inputs += Input("dockStyle", "NONE")
            Regex("док\\p{L}*\\s+(из\\s+)?стекл|матов\\p{L}*\\s+стекл").containsMatchIn(t) -> inputs += Input("dockStyle", "GLASS")
            Regex("док\\p{L}*\\s+плашк|плашк\\p{L}*\\s+док").containsMatchIn(t) -> inputs += Input("dockStyle", "SOLID")
            Regex("док\\p{L}*\\s+без\\s+фона").containsMatchIn(t) -> inputs += Input("dockStyle", "FLAT")
        }

        parseGesture(t, "свайп\\s+вверх|жест\\s+вверх", "swipeUp")?.let { inputs += it }
        parseGesture(t, "свайп\\s+вниз|жест\\s+вниз", "swipeDown")?.let { inputs += it }
        parseGesture(t, "двойн\\p{L}*\\s+(нажат|тап)", "doubleTap")?.let { inputs += it }

        // clock zones: «добавь пояс Москвы и Владивостока», «часовые пояса: Москва, Токио»
        val zoneBits = extractZoneQueries(prompt)
        if (zoneBits.isNotEmpty()) inputs += Input("clockZones", zoneBits)

        when {
            Regex("час\\p{L}*\\s+карточ|виджет-?карточ|стиль\\s+карточ").containsMatchIn(t) -> {
                inputs += Input("clockStyle", "CARD")
            }
            Regex("крупн\\p{L}*\\s+час|больш\\p{L}*\\s+час").containsMatchIn(t) && !t.contains("пояс") ->
                inputs += Input("clockStyle", "BIG")
            Regex("час\\p{L}*\\s+столбик").containsMatchIn(t) -> inputs += Input("clockStyle", "STACKED")
            Regex("час\\p{L}*\\s+терминал").containsMatchIn(t) -> inputs += Input("clockStyle", "TERMINAL")
            Regex("рукописн\\p{L}*\\s+час").containsMatchIn(t) -> inputs += Input("clockStyle", "SCRIPT")
            Regex("убер\\p{L}*\\s+час|без\\s+часов").containsMatchIn(t) && !t.contains("пояс") ->
                inputs += Input("clockStyle", "NONE")
        }

        val widget = ClockWidgetEdit(
            add = Regex("добав\\p{L}*\\s+(виджет\\s+)?час|поставь\\s+(виджет\\s+)?час").containsMatchIn(t),
            remove = Regex("убер\\p{L}*\\s+(виджет\\s+)?час|удал\\p{L}*\\s+(виджет\\s+)?час").containsMatchIn(t),
            spanX = Regex("(?:час|виджет)[\\p{L}\\s,]*?(\\d+)\\s*(?:x|×|на)\\s*(\\d+)").find(t)?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("(?:час|виджет)[\\p{L}\\s,]*?на\\s+(\\d+)\\s+клет").find(t)?.groupValues?.get(1)?.toIntOrNull(),
            spanY = Regex("(?:час|виджет)[\\p{L}\\s,]*?(\\d+)\\s*(?:x|×|на)\\s*(\\d+)").find(t)?.groupValues?.get(2)?.toIntOrNull(),
            page = when {
                Regex("час\\p{L}*[\\p{L}\\s]*на\\s+втор\\p{L}*\\s+страниц").containsMatchIn(t) -> 1
                Regex("час\\p{L}*[\\p{L}\\s]*на\\s+треть\\p{L}*\\s+страниц").containsMatchIn(t) -> 2
                Regex("час\\p{L}*[\\p{L}\\s]*на\\s+перв\\p{L}*\\s+страниц|час\\p{L}*[\\p{L}\\s]*наверху").containsMatchIn(t) -> 0
                else -> Regex("час\\p{L}*[\\p{L}\\s]*на\\s+(\\d+)\\s*страниц").find(t)?.groupValues?.get(1)?.toIntOrNull()?.minus(1)
            },
            col = if (Regex("лев\\p{L}*\\s+(верхн\\p{L}*\\s+)?угол|слева").containsMatchIn(t) && t.contains("час")) 0 else null,
            row = if (Regex("вверху|верхн\\p{L}*\\s+угол").containsMatchIn(t) && t.contains("час")) 0 else null,
        )
        if (widget.add || widget.remove || widget.spanX != null || widget.spanY != null || widget.page != null || widget.col != null || widget.row != null) {
            inputs += Input("clockWidget", widget)
        }

        when {
            Regex("убер\\p{L}*\\s+страниц\\p{L}*\\s+сегодня|выключ\\p{L}*\\s+сегодня").containsMatchIn(t) -> inputs += Input("todayPage", false)
            Regex("включ\\p{L}*\\s+сегодня|страниц\\p{L}*\\s+сегодня").containsMatchIn(t) -> inputs += Input("todayPage", true)
        }

        // obviously impossible requests we should surface rather than ignore
        val knownUnsupported = listOf(
            Regex("жив\\p{L}*\\s+обо[ия]") to "Живые обои из видео — не поддерживается",
            Regex("системн\\p{L}*\\s+шрифт\\s+всего\\s+телефон") to "Системный шрифт всего телефона — не поддерживается",
            Regex("поменят?ь\\s+обои\\s+из\\s+галере") to "Смена обоев из галереи через ИИ — не поддерживается",
            Regex("root|перепрошив") to "Системные изменения телефона — не поддерживается",
        )
        for ((rx, msg) in knownUnsupported) if (rx.containsMatchIn(t)) unsupported += msg

        return inputs to unsupported
    }

    /** Apply a validated clock-widget edit to a layout. */
    fun applyWidget(layout: ScreenLayout, edit: ClockWidgetEdit, columns: Int, rows: Int): ScreenLayout {
        var l = layout
        val id = CLOCK_WIDGET
        if (edit.remove) {
            return l.copy(home = l.home - id, widgets = l.widgets - id)
        }
        if (edit.add || edit.spanX != null || edit.spanY != null || edit.page != null || edit.col != null || edit.row != null || edit.style != null) {
            if (id !in l.widgets || id !in l.home) {
                l = l.addWidget(HomeWidget(id, HomeWidgetKind.CLOCK, spanX = columns.coerceIn(1, columns), spanY = 2), 0)
            }
        }
        val cur = l.widgets[id] ?: return l
        val sx = (edit.spanX ?: cur.spanX).coerceIn(1, columns)
        val sy = (edit.spanY ?: cur.spanY).coerceIn(1, rows)
        var next = cur.copy(spanX = sx, spanY = sy, clockStyle = edit.style ?: cur.clockStyle)
        l = l.copy(widgets = l.widgets + (id to next))
        if (edit.page != null || edit.col != null || edit.row != null) {
            val page = (edit.page ?: 0).coerceAtLeast(0)
            val col = (edit.col ?: 0).coerceIn(0, (columns - sx).coerceAtLeast(0))
            val row = (edit.row ?: 0).coerceIn(0, (rows - sy).coerceAtLeast(0))
            val cap = columns * rows
            val index = (page * cap + row * columns + col).coerceAtLeast(0)
            val without = l.home - id
            val at = index.coerceIn(0, without.size)
            l = l.copy(home = without.take(at) + id + without.drop(at))
        }
        return l.normalized()
    }

    fun formatChange(c: Change): String = "${c.label}: ${c.from} → ${c.to}"

    fun formatZones(zones: List<ClockZone>): String =
        if (zones.isEmpty() || zones.all { it.zoneId.isBlank() }) "не заданы"
        else zones.joinToString(", ") { z ->
            val name = z.label.ifBlank { TimeZones.nameFor(z.zoneId) ?: z.zoneId }
            if (name == z.zoneId) name else "$name (${z.zoneId})"
        }

    // --- internals ---

    private operator fun ClockWidgetEdit.plus(o: ClockWidgetEdit) = ClockWidgetEdit(
        add = add || o.add,
        remove = remove || o.remove,
        spanX = o.spanX ?: spanX,
        spanY = o.spanY ?: spanY,
        page = o.page ?: page,
        col = o.col ?: col,
        row = o.row ?: row,
        style = o.style ?: style,
    )

    private data class ZoneParse(val resolved: List<ClockZone>, val bad: List<String>)

    private fun parseZones(value: Any?): ZoneParse {
        val parts = flattenZoneValues(value)
        val resolved = ArrayList<ClockZone>()
        val bad = ArrayList<String>()
        val seen = HashSet<String>()
        for (p in parts) {
            val hit = TimeZones.resolve(p)
            if (hit == null) bad += p
            else if (hit.second !in seen) {
                seen += hit.second
                resolved += ClockZone(zoneId = hit.second, label = hit.first)
            }
        }
        return ZoneParse(resolved, bad)
    }

    private fun flattenZoneValues(value: Any?): List<String> = when (value) {
        null -> emptyList()
        is ClockZone -> listOf(value.label.ifBlank { value.zoneId })
        is String -> splitCities(value)
        is List<*> -> value.flatMap { v ->
            when (v) {
                is String -> splitCities(v)
                is Map<*, *> -> {
                    val s = (v["city"] ?: v["label"] ?: v["name"] ?: v["zoneId"] ?: v["id"] ?: v["zone"])?.toString()
                    if (s.isNullOrBlank()) emptyList() else listOf(s)
                }
                is ClockZone -> listOf(v.label.ifBlank { v.zoneId })
                else -> v?.toString()?.let { listOf(it) } ?: emptyList()
            }
        }
        is Map<*, *> -> flattenZoneValues(listOf(value))
        else -> splitCities(value.toString())
    }

    private fun splitCities(s: String): List<String> =
        s.split(Regex("(?:,|;|\\s+и\\s+|\\n)+"))
            .map { it.trim().trim('«', '»', '"', '“', '”', '.', ' ') }
            .filter { it.isNotBlank() }

    internal fun extractZoneQueries(prompt: String): List<String> {
        val found = ArrayList<String>()
        val rx = Regex(
            "(?:часов\\p{L}*\\s+пояс\\p{L}*|пояс\\p{L}*|зон\\p{L}*\\s+часов|часы(?:\\s+для)?)\\s*(?:для\\s+|на\\s+|в\\s+)?[:\\-–]?\\s*(.+)",
            RegexOption.IGNORE_CASE,
        )
        rx.findAll(prompt).forEach { m ->
            val tail = m.groupValues[1]
            val cut = tail.split(Regex("\\.(?:\\s|$)|,\\s+а\\s+|тем[аыу] |анимац|сетк|иконк|док"))[0]
            found += splitCities(cut)
        }
        val after = Regex("добав\\p{L}+\\s+(.+)", RegexOption.IGNORE_CASE).find(prompt)?.groupValues?.get(1)
        if (after != null) {
            for (bit in splitCities(after)) if (TimeZones.resolve(bit) != null) found += bit
        }
        val n = TimeZones.normalize(prompt)
        if (n.contains("пояс") || (n.contains("зон") && n.contains("час"))) {
            for ((city, _) in TimeZones.cities) {
                val stem = TimeZones.stems(city).minByOrNull { it.length } ?: TimeZones.normalize(city)
                if (stem.length >= 4 && n.contains(stem)) found += city
            }
        }
        return found.distinct()
    }

    private fun parseWidget(value: Any?): ClockWidgetEdit? = when (value) {
        is ClockWidgetEdit -> value
        is Map<*, *> -> {
            fun num(k: String) = (value[k] as? Number)?.toInt() ?: value[k]?.toString()?.toIntOrNull()
            fun str(k: String) = value[k]?.toString()
            ClockWidgetEdit(
                add = str("add") == "true" || value["add"] == true || str("action").equals("add", true),
                remove = str("remove") == "true" || value["remove"] == true || str("action").equals("remove", true),
                spanX = num("spanX") ?: num("w") ?: num("width"),
                spanY = num("spanY") ?: num("h") ?: num("height"),
                page = num("page"),
                col = num("col") ?: num("column") ?: num("x"),
                row = num("row") ?: num("y"),
                style = str("style")?.let { matchAllowed(it, enumPairs(ClockStyle.entries.toTypedArray()) { it.title }) }?.let { ClockStyle.valueOf(it) },
            )
        }
        is String -> localParse("виджет часов $value", LauncherConfig(), Presets.cupertino).widget
        else -> null
    }

    private fun clockWidgetSummary(layout: ScreenLayout, theme: ThemeSpec): String {
        val w = layout.widgets[CLOCK_WIDGET]?.takeIf { CLOCK_WIDGET in layout.home } ?: return "нет виджета часов"
        val style = (w.clockStyle ?: theme.clockStyle).title
        return "$style, ${w.spanX}×${w.spanY}"
    }

    private fun widgetSummary(edit: ClockWidgetEdit, theme: ThemeSpec): String {
        if (edit.remove) return "убрать виджет часов"
        val parts = ArrayList<String>()
        if (edit.add) parts += "добавить"
        parts += (edit.style?.title ?: theme.clockStyle.title)
        if (edit.spanX != null || edit.spanY != null) parts += "${edit.spanX ?: "?"}×${edit.spanY ?: "?"}"
        if (edit.page != null) parts += "страница ${edit.page!! + 1}"
        if (edit.col != null || edit.row != null) parts += "клетка ${edit.col ?: 0},${edit.row ?: 0}"
        return parts.joinToString(", ")
    }

    private fun parseBool(v: Any?): Boolean? = when (v) {
        is Boolean -> v
        is Number -> v.toInt() != 0
        is String -> when (norm(v)) {
            "true", "1", "да", "вкл", "включить", "включи", "on", "yes", "угу" -> true
            "false", "0", "нет", "выкл", "выключить", "выключи", "off", "no" -> false
            else -> null
        }
        else -> null
    }

    private fun parseNumber(v: Any?): Double? = when (v) {
        is Number -> v.toDouble()
        is String -> v.replace(',', '.').trim().toDoubleOrNull()
        is Boolean -> if (v) 1.0 else 0.0
        else -> null
    }

    private fun stringify(v: Any?): String = when (v) {
        null -> ""
        is String -> v
        is List<*> -> v.joinToString(", ") { it?.toString().orEmpty() }
        else -> v.toString()
    }

    private fun boolLabel(v: Boolean?): String = when (v) {
        true -> "вкл"
        false -> "выкл"
        null -> "—"
    }

    private fun fmtFloat(v: Float): String = if (v == v.toInt().toFloat()) v.toInt().toString() else "%.2f".format(v)

    private fun matchPreset(name: String): ThemeSpec? {
        val n = norm(name)
        if (n.isBlank()) return null
        return Presets.all.firstOrNull { norm(it.name) == n || norm(it.id) == n }
            ?: Presets.all.firstOrNull { norm(it.name).startsWith(n) || n.startsWith(norm(it.name)) }
    }

    private fun matchFont(name: String): String? {
        val n = norm(name).replace(" ", "_")
        return FontIds.all.firstOrNull { it == n || it.replace("_", " ") == norm(name) }
    }

    private fun matchAllowed(raw: String, allowed: List<Pair<String, String>>): String? {
        val n = norm(raw)
        if (n.isBlank()) return null
        allowed.firstOrNull { norm(it.first) == n || norm(it.second) == n }?.let { return it.first }
        allowed.firstOrNull { norm(it.second).contains(n) || n.contains(norm(it.second)) }?.let { return it.first }
        aliasesForEffect(n)?.let { id -> if (allowed.any { it.first == id }) return id }
        return null
    }

    private fun matchPageEffect(t: String): String? {
        val table = listOf(
            "стопка карт" to "STACK", "колода" to "STACK", "стопка" to "STACK",
            "цилиндр" to "CYLINDER", "куб внутри" to "CUBE_IN", "куб снаружи" to "CUBE", "куб" to "CUBE",
            "карусел" to "CAROUSEL", "аккордеон" to "ACCORDION", "переворот" to "FLIP",
            "поворот" to "ROTATE", "наплыв" to "ZOOM_IN", "отдален" to "ZOOM", "масштаб" to "ZOOM",
            "наклон" to "TILT", "глубин" to "DEPTH", "волна" to "WAVE", "колесо" to "WHEEL",
            "затухан" to "FADE", "вращен" to "SPIN", "без эффекта" to "NONE", "без анимац" to "NONE",
        )
        for ((k, v) in table) if (t.contains(k)) return v
        return null
    }

    private fun aliasesForEffect(n: String): String? = matchPageEffect(n)

    private fun parseGesture(t: String, lead: String, key: String): Input? {
        val m = Regex("(?:$lead)\\s*[-—:]*\\s*(.+)").find(t) ?: return null
        val tail = m.groupValues[1].split(Regex("[,.;]|свайп|двойн"))[0]
        val allowed = enumPairs(GestureAction.entries.toTypedArray()) { it.title }
        val hit = matchAllowed(tail, allowed) ?: when {
            Regex("меню|шторк\\p{L}*\\s+приложен|все\\s+приложен").containsMatchIn(norm(tail)) -> "DRAWER"
            Regex("уведомл|шторк").containsMatchIn(norm(tail)) -> "NOTIFICATIONS"
            Regex("поиск").containsMatchIn(norm(tail)) -> "SEARCH"
            Regex("блок").containsMatchIn(norm(tail)) -> "LOCK"
            Regex("настрой").containsMatchIn(norm(tail)) -> "SETTINGS"
            Regex("виджет").containsMatchIn(norm(tail)) -> "WIDGETS"
            Regex("ничего|ничем|отключ").containsMatchIn(norm(tail)) -> "NONE"
            else -> null
        } ?: return null
        return Input(key, hit)
    }

    private fun enumLabel(value: String?, d: Def): String =
        d.allowed.firstOrNull { it.first == value }?.second ?: value ?: "—"

    private fun <T : Enum<T>> enumPairs(values: Array<T>, title: (T) -> String): List<Pair<String, String>> =
        values.map { it.name to title(it) }

    private fun readBool(cfg: LauncherConfig, th: ThemeSpec, key: String): Boolean? = when (key) {
        "allAppsOnHome" -> cfg.allAppsOnHome
        "autoKeyboard" -> cfg.autoKeyboard
        "highRefreshRate" -> cfg.highRefreshRate
        "autoAddNew" -> cfg.autoAddNew
        "returnAnimation" -> cfg.returnAnimation
        "autoRotate" -> cfg.autoRotate
        "multiFingerDrag" -> cfg.multiFingerDrag
        "notificationBadges" -> cfg.notificationBadges
        "digitOverlay" -> cfg.digitOverlay
        "digitOutline" -> cfg.digitOutline
        "todayPage" -> cfg.todayPage
        "frameMeter" -> cfg.frameMeter
        "gestureContract" -> cfg.gestureContract
        "ownLanding" -> cfg.ownLanding
        "dynamicColor" -> th.dynamicColor
        "dark" -> th.dark
        "showLabels" -> th.showLabels
        "blur" -> th.blur
        "glow" -> th.glow
        "iconTilt" -> th.iconTilt
        else -> null
    }

    private fun writeBool(cfg: LauncherConfig, th: ThemeSpec, key: String, v: Boolean): Pair<LauncherConfig, ThemeSpec> = when (key) {
        "allAppsOnHome" -> cfg.copy(allAppsOnHome = v) to th
        "autoKeyboard" -> cfg.copy(autoKeyboard = v) to th
        "highRefreshRate" -> cfg.copy(highRefreshRate = v) to th
        "autoAddNew" -> cfg.copy(autoAddNew = v) to th
        "returnAnimation" -> cfg.copy(returnAnimation = v) to th
        "autoRotate" -> cfg.copy(autoRotate = v) to th
        "multiFingerDrag" -> cfg.copy(multiFingerDrag = v) to th
        "notificationBadges" -> cfg.copy(notificationBadges = v) to th
        "digitOverlay" -> cfg.copy(digitOverlay = v) to th
        "digitOutline" -> cfg.copy(digitOutline = v) to th
        "todayPage" -> cfg.copy(todayPage = v) to th
        "frameMeter" -> cfg.copy(frameMeter = v) to th
        "gestureContract" -> cfg.copy(gestureContract = v) to th
        "ownLanding" -> cfg.copy(ownLanding = v) to th
        "dynamicColor" -> cfg to th.copy(dynamicColor = v)
        "dark" -> cfg to th.copy(dark = v)
        "showLabels" -> cfg to th.copy(showLabels = v)
        "blur" -> cfg to th.copy(blur = v)
        "glow" -> cfg to th.copy(glow = v)
        "iconTilt" -> cfg to th.copy(iconTilt = v)
        else -> cfg to th
    }

    private fun readEnum(cfg: LauncherConfig, th: ThemeSpec, themeId: String, key: String): String? = when (key) {
        "swipeUp" -> cfg.swipeUp.name
        "swipeDown" -> cfg.swipeDown.name
        "doubleTap" -> cfg.doubleTap.name
        "homeAnim" -> cfg.homeAnim.name
        "backAnim" -> cfg.backAnim.name
        "homeIconFx" -> cfg.homeIconFx.name
        "unlockAnim" -> cfg.unlockAnim.name
        "pageEffect" -> cfg.pageEffectFor(themeId, th.layout).name
        "digitMode" -> cfg.digitMode.name
        "digitAnchor" -> cfg.digitAnchor.name
        "layout" -> th.layout.name
        "drawer" -> th.drawer.name
        "iconShape" -> th.iconShape.name
        "wallpaper" -> th.wallpaper.name
        "dockStyle" -> th.dockStyle.name
        "pageIndicator" -> th.pageIndicator.name
        "clockStyle" -> th.clockStyle.name
        "motion" -> th.motion.name
        "overlay" -> th.overlay.name
        "textCase" -> th.textCase.name
        "tileFill" -> th.tileFill.name
        else -> null
    }

    private fun writeEnum(cfg: LauncherConfig, th: ThemeSpec, themeId: String, key: String, name: String): Pair<LauncherConfig, ThemeSpec> = when (key) {
        "swipeUp" -> cfg.copy(swipeUp = GestureAction.valueOf(name)) to th
        "swipeDown" -> cfg.copy(swipeDown = GestureAction.valueOf(name)) to th
        "doubleTap" -> cfg.copy(doubleTap = GestureAction.valueOf(name)) to th
        "homeAnim" -> cfg.copy(homeAnim = HomeAnim.valueOf(name)) to th
        "backAnim" -> cfg.copy(backAnim = BackAnim.valueOf(name)) to th
        "homeIconFx" -> cfg.copy(homeIconFx = IconFx.valueOf(name)) to th
        "unlockAnim" -> cfg.copy(unlockAnim = UnlockAnim.valueOf(name)) to th
        "pageEffect" -> cfg.copy(pageEffects = cfg.pageEffects + (themeId to PageEffect.valueOf(name))) to th
        "digitMode" -> cfg.copy(digitMode = DigitMode.valueOf(name)) to th
        "digitAnchor" -> cfg.copy(digitAnchor = DigitAnchor.valueOf(name)) to th
        "layout" -> cfg to th.copy(layout = HomeLayout.valueOf(name))
        "drawer" -> cfg to th.copy(drawer = DrawerStyle.valueOf(name))
        "iconShape" -> cfg to th.copy(iconShape = IconShape.valueOf(name))
        "wallpaper" -> cfg to th.copy(wallpaper = WallpaperStyle.valueOf(name))
        "dockStyle" -> cfg to th.copy(dockStyle = DockStyle.valueOf(name))
        "pageIndicator" -> cfg to th.copy(pageIndicator = PageIndicator.valueOf(name))
        "clockStyle" -> cfg to th.copy(clockStyle = ClockStyle.valueOf(name))
        "motion" -> cfg to th.copy(motion = Motion.valueOf(name))
        "overlay" -> cfg to th.copy(overlay = Overlay.valueOf(name))
        "textCase" -> cfg to th.copy(textCase = TextCase.valueOf(name))
        "tileFill" -> cfg to th.copy(tileFill = TileFill.valueOf(name))
        else -> cfg to th
    }

    private fun readInt(cfg: LauncherConfig, th: ThemeSpec, key: String): Int? = when (key) {
        "returnDurationMs" -> cfg.returnDurationMs
        "gestureReturnSpeed" -> cfg.gestureReturnSpeed
        "clockZoneCount" -> cfg.clockZoneCount
        "digitSizeSp" -> cfg.digitSizeSp
        "iconSizeDp" -> th.iconSizeDp
        "cornerDp" -> th.cornerDp
        "columns" -> th.columns
        "rows" -> th.rows
        "clockWeight" -> th.clockWeight
        "tileColumns" -> th.tileColumns
        else -> null
    }

    private fun writeInt(cfg: LauncherConfig, th: ThemeSpec, key: String, v: Int): Pair<LauncherConfig, ThemeSpec> = when (key) {
        "returnDurationMs" -> cfg.copy(returnDurationMs = v) to th
        "gestureReturnSpeed" -> cfg.copy(gestureReturnSpeed = v) to th
        "clockZoneCount" -> cfg.copy(clockZoneCount = v.coerceIn(1, 3)) to th
        "digitSizeSp" -> cfg.copy(digitSizeSp = v) to th
        "iconSizeDp" -> cfg to th.copy(iconSizeDp = v)
        "cornerDp" -> cfg to th.copy(cornerDp = v)
        "columns" -> cfg to th.copy(columns = v)
        "rows" -> cfg to th.copy(rows = v)
        "clockWeight" -> cfg to th.copy(clockWeight = v)
        "tileColumns" -> cfg to th.copy(tileColumns = if (v >= 6) 6 else 4)
        else -> cfg to th
    }

    private fun readFloat(cfg: LauncherConfig, th: ThemeSpec, key: String): Float? = when (key) {
        "animSpeed" -> cfg.animSpeed
        "digitAlpha" -> cfg.digitAlpha
        "wallpaperDim" -> th.wallpaperDim
        "labelSizeSp" -> th.labelSizeSp
        "surfaceAlpha" -> th.surfaceAlpha
        "tileGapDp" -> th.tileGapDp
        else -> null
    }

    private fun writeFloat(cfg: LauncherConfig, th: ThemeSpec, key: String, v: Float): Pair<LauncherConfig, ThemeSpec> = when (key) {
        "animSpeed" -> cfg.copy(animSpeed = v) to th
        "digitAlpha" -> cfg.copy(digitAlpha = v) to th
        "wallpaperDim" -> cfg to th.copy(wallpaperDim = v)
        "labelSizeSp" -> cfg to th.copy(labelSizeSp = v)
        "surfaceAlpha" -> cfg to th.copy(surfaceAlpha = v)
        "tileGapDp" -> cfg to th.copy(tileGapDp = v)
        else -> cfg to th
    }

    private fun readString(th: ThemeSpec, key: String): String? = when (key) {
        "headingFont" -> th.headingFont
        "bodyFont" -> th.bodyFont
        "clockFont" -> th.clockFont
        else -> null
    }

    private fun writeFont(th: ThemeSpec, key: String, id: String): ThemeSpec = when (key) {
        "headingFont" -> th.copy(headingFont = id)
        "bodyFont" -> th.copy(bodyFont = id)
        "clockFont" -> th.copy(clockFont = id)
        else -> th
    }

    private fun norm(s: String): String = s.lowercase().replace('ё', 'е').trim()

    private val defs: List<Def> = listOf(
        Def("allAppsOnHome", "Все приложения на главном", Kind.BOOL, "Главный экран"),
        Def("autoAddNew", "Новые приложения на главный", Kind.BOOL, "Главный экран"),
        Def("todayPage", "Страница «Сегодня»", Kind.BOOL, "Главный экран"),
        Def("notificationBadges", "Значки уведомлений", Kind.BOOL, "Главный экран"),
        Def("theme", "Тема", Kind.THEME, "Тема"),
        Def("dark", "Тёмная тема", Kind.BOOL, "Тема"),
        Def("dynamicColor", "Динамический цвет", Kind.BOOL, "Тема"),
        Def("layout", "Вид главного экрана", Kind.ENUM, "Тема", enumPairs(HomeLayout.entries.toTypedArray()) { it.title }),
        Def("drawer", "Вид меню приложений", Kind.ENUM, "Тема", enumPairs(DrawerStyle.entries.toTypedArray()) { it.title }),
        Def("wallpaper", "Обои темы", Kind.ENUM, "Тема", enumPairs(WallpaperStyle.entries.toTypedArray()) { it.title }),
        Def("wallpaperDim", "Затемнение обоев", Kind.FLOAT, "Тема", min = 0.0, max = 0.8),
        Def("iconShape", "Форма иконок", Kind.ENUM, "Тема", enumPairs(IconShape.entries.toTypedArray()) { it.title }),
        Def("iconSizeDp", "Размер иконок, dp", Kind.INT, "Тема", min = 36.0, max = 80.0),
        Def("cornerDp", "Скругление, dp", Kind.INT, "Тема", min = 0.0, max = 40.0),
        Def("columns", "Столбцов сетки", Kind.INT, "Сетка", min = 3.0, max = 6.0),
        Def("rows", "Рядов сетки", Kind.INT, "Сетка", min = 3.0, max = 8.0),
        Def("showLabels", "Подписи иконок", Kind.BOOL, "Тема"),
        Def("labelSizeSp", "Размер подписей", Kind.FLOAT, "Тема", min = 9.0, max = 22.0),
        Def("textCase", "Регистр подписей", Kind.ENUM, "Тема", enumPairs(TextCase.entries.toTypedArray()) { it.title }),
        Def("dockStyle", "Стиль дока", Kind.ENUM, "Док", enumPairs(DockStyle.entries.toTypedArray()) { it.title }),
        Def("pageIndicator", "Индикатор страниц", Kind.ENUM, "Тема", enumPairs(PageIndicator.entries.toTypedArray()) { it.title }),
        Def("clockStyle", "Стиль часов", Kind.ENUM, "Виджеты", enumPairs(ClockStyle.entries.toTypedArray()) { it.title }),
        Def("clockZones", "Часовые пояса", Kind.TZ_LIST, "Виджеты"),
        Def("clockZoneCount", "Сколько поясов показать", Kind.INT, "Виджеты", min = 1.0, max = 3.0),
        Def("clockWidget", "Виджет часов (размер и место)", Kind.WIDGET, "Виджеты"),
        Def("motion", "Характер движения", Kind.ENUM, "Анимация", enumPairs(Motion.entries.toTypedArray()) { it.title }),
        Def("pageEffect", "Анимация перелистывания", Kind.ENUM, "Анимация", enumPairs(PageEffect.entries.toTypedArray()) { it.title }),
        Def("homeAnim", "Анимация «Домой»", Kind.ENUM, "Анимация", enumPairs(HomeAnim.entries.toTypedArray()) { it.title }),
        Def("backAnim", "Анимация «Назад»", Kind.ENUM, "Анимация", enumPairs(BackAnim.entries.toTypedArray()) { it.title }),
        Def("unlockAnim", "Анимация разблокировки", Kind.ENUM, "Анимация", enumPairs(UnlockAnim.entries.toTypedArray()) { it.title }),
        Def("homeIconFx", "Эффект на значке после возврата", Kind.ENUM, "Анимация", enumPairs(IconFx.entries.toTypedArray()) { it.title }),
        Def("animSpeed", "Скорость анимации", Kind.FLOAT, "Анимация", min = 0.4, max = 2.0),
        Def("returnDurationMs", "Длительность возврата, мс", Kind.INT, "Анимация", min = 150.0, max = 1000.0),
        Def("gestureReturnSpeed", "Ускорение возврата жестом", Kind.INT, "Анимация", min = 0.0, max = 2.0),
        Def("returnAnimation", "Анимация возврата в иконку", Kind.BOOL, "Анимация"),
        Def("blur", "Размытие", Kind.BOOL, "Тема"),
        Def("glow", "Свечение", Kind.BOOL, "Тема"),
        Def("iconTilt", "Наклон иконок", Kind.BOOL, "Тема"),
        Def("overlay", "Наложение", Kind.ENUM, "Тема", enumPairs(Overlay.entries.toTypedArray()) { it.title }),
        Def("tileFill", "Заливка плиток", Kind.ENUM, "Тема", enumPairs(TileFill.entries.toTypedArray()) { it.title }),
        Def("tileColumns", "Столбцов плиток", Kind.INT, "Сетка", min = 4.0, max = 6.0),
        Def("headingFont", "Шрифт заголовков", Kind.STRING, "Тема"),
        Def("bodyFont", "Шрифт текста", Kind.STRING, "Тема"),
        Def("clockFont", "Шрифт часов", Kind.STRING, "Тема"),
        Def("clockWeight", "Насыщенность шрифта часов", Kind.INT, "Тема", min = 100.0, max = 900.0),
        Def("swipeUp", "Свайп вверх", Kind.ENUM, "Жесты", enumPairs(GestureAction.entries.toTypedArray()) { it.title }),
        Def("swipeDown", "Свайп вниз", Kind.ENUM, "Жесты", enumPairs(GestureAction.entries.toTypedArray()) { it.title }),
        Def("doubleTap", "Двойное нажатие", Kind.ENUM, "Жесты", enumPairs(GestureAction.entries.toTypedArray()) { it.title }),
        Def("autoKeyboard", "Автоклавиатура в поиске", Kind.BOOL, "Жесты"),
        Def("autoRotate", "Автоповорот", Kind.BOOL, "Жесты"),
        Def("multiFingerDrag", "Перетаскивание несколькими пальцами", Kind.BOOL, "Жесты"),
        Def("highRefreshRate", "Высокая частота обновления", Kind.BOOL, "Система"),
        Def("digitOverlay", "Цифры поверх приложений", Kind.BOOL, "Система"),
        Def("digitMode", "Что показывают цифры", Kind.ENUM, "Система", enumPairs(DigitMode.entries.toTypedArray()) { it.title }),
        Def("digitAnchor", "Положение цифр", Kind.ENUM, "Система", enumPairs(DigitAnchor.entries.toTypedArray()) { it.title }),
        Def("digitSizeSp", "Размер цифр", Kind.INT, "Система", min = 24.0, max = 200.0),
        Def("digitAlpha", "Прозрачность цифр", Kind.FLOAT, "Система", min = 0.15, max = 1.0),
        Def("digitOutline", "Обводка цифр", Kind.BOOL, "Система"),
        Def("frameMeter", "Счётчик кадров", Kind.BOOL, "Система"),
        Def("iconPack", "Пакет иконок", Kind.STRING, "Тема"),
    )

    private val byKey: Map<String, Def> = defs.associateBy { it.key.lowercase() }
    private val aliases: Map<String, String> = mapOf(
        "allappsonhome" to "allAppsOnHome",
        "всеприложениянаглавном" to "allAppsOnHome",
        "pagefx" to "pageEffect",
        "animation" to "pageEffect",
        "анимация" to "pageEffect",
        "сетка" to "columns",
        "иконки" to "iconShape",
        "докиконки" to "dockStyle",
        "timezones" to "clockZones",
        "timeZone" to "clockZones",
        "zones" to "clockZones",
        "пояса" to "clockZones",
        "часы" to "clockStyle",
        "widget" to "clockWidget",
        "виджетчасов" to "clockWidget",
    )

    /** Compact list of keys + allowed values, for the model prompt. */
    fun schemaPrompt(): String = buildString {
        appendLine("Допустимые настройки (key и значение). Неизвестные просьбы клади в \"unsupported\", не применяй молча.")
        for (d in defs) {
            val range = when (d.kind) {
                Kind.BOOL -> "true|false"
                Kind.ENUM -> d.allowed.joinToString("|") { "${it.first} (${it.second})" }
                Kind.INT, Kind.FLOAT -> "${d.min ?: "?"}…${d.max ?: "?"}"
                Kind.TZ_LIST -> "список городов или IANA id, например [\"Москва\",\"Владивосток\"] или [\"Europe/Moscow\"]"
                Kind.THEME -> Presets.all.joinToString("|") { it.name }
                Kind.WIDGET -> "{spanX,spanY,page,col,row,style,add,remove}"
                Kind.STRING -> if (d.key.endsWith("Font")) FontIds.all.take(8).joinToString("|") + "…" else "строка"
            }
            appendLine("- ${d.key} (${d.label}): $range")
        }
    }
}
