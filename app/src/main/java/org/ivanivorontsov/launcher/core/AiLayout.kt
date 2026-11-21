package org.ivanivorontsov.launcher.core

/**
 * Structured home-screen arrangement plans produced by a language model or by a local
 * fallback, then validated against the apps actually installed on the phone.
 *
 * Pure Kotlin: parsing, fuzzy matching, apply and undo are unit-tested on the JVM.
 */
object AiLayout {

    data class CatalogApp(
        val key: String,
        val label: String,
        val pkg: String,
        val category: String,
        val usage: Int = 0,
    )

    data class PlanFolder(val name: String, val apps: List<String> = emptyList())

    data class PlanItem(
        val type: String = "app",
        val name: String? = null,
        val pkg: String? = null,
        val folder: String? = null,
        val apps: List<String> = emptyList(),
        val page: Int? = null,
        val col: Int? = null,
        val row: Int? = null,
    )

    data class PlanPage(val items: List<PlanItem> = emptyList())

    data class Plan(
        val dock: List<String> = emptyList(),
        val folders: List<PlanFolder> = emptyList(),
        val pages: List<PlanPage> = emptyList(),
        val home: List<PlanItem> = emptyList(),
        val notes: String? = null,
    )

    data class Match(val query: String, val app: CatalogApp, val score: Int)

    data class Resolved(
        val dock: List<String>,
        val home: List<String>,
        val folders: Map<String, Folder>,
        val matches: List<Match>,
        val unmatched: List<String>,
    )

    data class Application(
        val layout: ScreenLayout,
        val resolved: Resolved,
        val previous: ScreenLayout,
    )

    /** Pull a JSON object out of a model reply (raw JSON, or a ```json fence, or prose around `{...}`). */
    fun extractJson(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val fence = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE).find(trimmed)
        val body = fence?.groupValues?.get(1)?.trim() ?: trimmed
        if (body.startsWith("{") && body.endsWith("}")) return body
        val start = body.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inStr = false
        var escape = false
        for (i in start until body.length) {
            val ch = body[i]
            when {
                escape -> escape = false
                ch == '\\' && inStr -> escape = true
                ch == '"' -> inStr = !inStr
                inStr -> {}
                ch == '{' -> depth++
                ch == '}' -> {
                    depth--
                    if (depth == 0) return body.substring(start, i + 1)
                }
            }
        }
        return null
    }

    fun parse(json: String): Plan {
        val src = extractJson(json) ?: throw IllegalArgumentException("В ответе нет JSON")
        val root = JsonMini.parseObject(src)
        val dock = root.stringList("dock")
        val folders = root.objList("folders").map {
            PlanFolder(name = it.str("name") ?: it.str("title") ?: "Папка", apps = it.stringList("apps") + it.stringList("items"))
        }
        fun item(o: JsonMini.Obj): PlanItem {
            val type = (o.str("type") ?: if (o.list("apps") != null || o.str("folder") != null) "folder" else "app").lowercase()
            val apps = o.stringList("apps") + o.stringList("items")
            return PlanItem(
                type = type,
                name = o.str("name") ?: o.str("label") ?: o.str("title") ?: o.str("app"),
                pkg = o.str("pkg") ?: o.str("package") ?: o.str("packageName"),
                folder = o.str("folder") ?: o.str("folderName"),
                apps = apps,
                page = o.int("page"),
                col = o.int("col") ?: o.int("column") ?: o.int("x"),
                row = o.int("row") ?: o.int("y"),
            )
        }
        val pages = root.objList("pages").map { page ->
            val items = page.objList("items") + page.objList("apps")
            PlanPage(items = if (items.isNotEmpty()) items.map(::item) else page.stringList("items").map { PlanItem(name = it) })
        }
        val home = root.objList("home").map(::item).ifEmpty {
            root.stringList("home").map { PlanItem(name = it) }
        }
        return Plan(dock = dock, folders = folders, pages = pages, home = home, notes = root.str("notes"))
    }

    fun match(query: String, apps: List<CatalogApp>): Match? {
        val q = query.trim()
        if (q.isEmpty() || apps.isEmpty()) return null
        val nq = normalize(q)
        val qPkg = q.lowercase()
        apps.firstOrNull { it.pkg.equals(q, ignoreCase = true) || it.key.equals(q, ignoreCase = true) }?.let {
            return Match(query, it, 10_000)
        }
        apps.firstOrNull { it.pkg.equals(qPkg, ignoreCase = true) }?.let { return Match(query, it, 9_500) }
        apps.firstOrNull { normalize(it.label) == nq }?.let { return Match(query, it, 9_000) }

        val aliasHits = aliases[nq].orEmpty()
        if (aliasHits.isNotEmpty()) {
            val hit = apps.filter { app ->
                val hay = (app.pkg + " " + app.label).lowercase()
                aliasHits.any { hay.contains(it) }
            }.maxByOrNull { it.usage }
            if (hit != null) return Match(query, hit, 8_000)
        }

        data class Scored(val app: CatalogApp, val score: Int)
        val scored = ArrayList<Scored>(apps.size)
        for (app in apps) {
            val key = SearchKey(app.label, app.pkg)
            var s = SearchEngine.score(key, nq)
            if (s <= 0 && nq.length >= 3 && (normalize(app.label).contains(nq) || app.pkg.lowercase().contains(nq))) s = 40
            if (s <= 0 && nq.length >= 4) {
                val d = levenshtein(nq, normalize(app.label))
                if (d in 1..2) s = 30 - d
            }
            if (s > 0) scored += Scored(app, s)
        }
        if (scored.isEmpty()) return null
        scored.sortByDescending { it.score }
        val best = scored[0]
        val second = scored.getOrNull(1)
        // Ambiguous: two installed apps score almost the same.
        if (second != null && best.score < 80 && second.score >= best.score - 5 && best.app.key != second.app.key) return null
        return Match(query, best.app, best.score)
    }

    fun resolve(plan: Plan, apps: List<CatalogApp>, maxDock: Int = 5, columns: Int = 4, rows: Int = 6): Resolved {
        val used = LinkedHashSet<String>()
        val matches = ArrayList<Match>()
        val unmatched = ArrayList<String>()

        fun take(query: String): CatalogApp? {
            val m = match(query, apps) ?: run { unmatched += query; return null }
            if (m.app.key in used) return m.app
            used += m.app.key
            matches += m
            return m.app
        }
        fun takeAll(queries: List<String>): List<String> = queries.mapNotNull { take(it)?.key }.distinct()

        val dock = takeAll(plan.dock).distinct().take(maxDock.coerceAtLeast(1))

        val folders = LinkedHashMap<String, Folder>()
        var folderSeq = 1
        fun allocId(): String {
            var id = FOLDER_PREFIX + folderSeq
            while (id in folders) { folderSeq++; id = FOLDER_PREFIX + folderSeq }
            folderSeq++
            return id
        }
        fun folderOf(name: String, queries: List<String>): String? {
            val keys = takeAll(queries).filter { it !in dock }
            if (keys.size < 2) {
                // A one-app "folder" becomes a loose icon; zero apps are dropped.
                return keys.singleOrNull()
            }
            val existing = folders.entries.firstOrNull { it.value.name.equals(name.trim(), ignoreCase = true) }
            if (existing != null) {
                folders[existing.key] = existing.value.copy(apps = (existing.value.apps + keys).distinct())
                return existing.key
            }
            val id = allocId()
            folders[id] = Folder(id, name.trim().ifEmpty { "Папка" }, keys)
            return id
        }

        for (f in plan.folders) folderOf(f.name, f.apps)

        val home = ArrayList<String>()
        fun addHome(id: String?) { if (id != null && id !in home && id !in dock) home += id }

        fun placeItem(item: PlanItem) {
            when (item.type) {
                "folder" -> addHome(folderOf(item.folder ?: item.name ?: "Папка", item.apps))
                else -> {
                    val q = item.pkg ?: item.name ?: return
                    addHome(take(q)?.key)
                }
            }
        }

        if (plan.pages.isNotEmpty()) {
            val cap = (columns * rows).coerceAtLeast(1)
            for (page in plan.pages) {
                val cells = arrayOfNulls<String>(cap)
                var cursor = 0
                fun nextFree(): Int {
                    while (cursor < cap && cells[cursor] != null) cursor++
                    return cursor
                }
                for (item in page.items) {
                    val before = home.size + cells.count { it != null }
                    val startHome = home.size
                    placeItem(item)
                    // If the item was appended to [home] by placeItem, move the last addition into the grid.
                    if (item.col != null && item.row != null) {
                        val idx = item.row!!.coerceAtLeast(0) * columns + item.col!!.coerceAtLeast(0)
                        if (idx in 0 until cap) {
                            val added = home.drop(startHome)
                            if (added.isNotEmpty()) {
                                val id = added.last()
                                repeat(added.size) { if (home.isNotEmpty()) home.removeAt(home.lastIndex) }
                                if (cells[idx] == null && id !in cells && id !in home) cells[idx] = id
                                else added.forEach { addHome(it) }
                            }
                            continue
                        }
                    }
                    if (home.size > startHome) {
                        val added = home.subList(startHome, home.size).toList()
                        repeat(added.size) { home.removeAt(home.lastIndex) }
                        for (id in added) {
                            val i = nextFree()
                            if (i < cap) cells[i] = id else addHome(id)
                        }
                    } else {
                        @Suppress("UNUSED_VARIABLE") val _b = before
                    }
                }
                cells.forEach { if (it != null) addHome(it) }
            }
        } else {
            plan.home.forEach(::placeItem)
        }

        // Folders named in [plan.folders] that were never placed on the home grid go at the end.
        for (id in folders.keys) addHome(id)

        return Resolved(dock = dock, home = home, folders = folders, matches = matches, unmatched = unmatched.distinct())
    }

    /**
     * Applies [resolved] to [current]. Widgets already on the home screen stay where they were
     * (shifted to the start of the first page). Tiles and favourites are left untouched.
     * Dock and regular icons/folders are replaced by the plan.
     */
    fun apply(current: ScreenLayout, resolved: Resolved): ScreenLayout {
        val widgets = current.home.filter { isWidgetId(it) && it in current.widgets }
        val layout = current.copy(
            home = widgets + resolved.home.filter { it !in widgets },
            dock = resolved.dock,
            folders = resolved.folders,
        )
        return layout.normalized()
    }

    fun applyPlan(current: ScreenLayout, plan: Plan, apps: List<CatalogApp>, maxDock: Int = 5, columns: Int = 4, rows: Int = 6): Application {
        val resolved = resolve(plan, apps, maxDock, columns, rows)
        return Application(layout = apply(current, resolved), resolved = resolved, previous = current)
    }

    fun undo(current: ScreenLayout, previous: ScreenLayout): ScreenLayout = previous

    /**
     * Deterministic arrangement used when the model is unavailable or its reply cannot be parsed.
     * Understands a few Russian phrases (док, папки, категории, алфавит); otherwise groups by
     * [HomeCategories] and puts the most-used apps in the dock.
     */
    fun localFallback(
        apps: List<CatalogApp>,
        current: ScreenLayout,
        maxDock: Int = 5,
        columns: Int = 4,
        rows: Int = 6,
        prompt: String = "",
    ): Application {
        val parsed = parsePromptHints(prompt, apps)
        val plan = if (parsed != null && (parsed.dock.isNotEmpty() || parsed.folders.isNotEmpty() || parsed.home.isNotEmpty() || parsed.pages.isNotEmpty())) {
            parsed
        } else {
            defaultCategoryPlan(apps, current, maxDock)
        }
        return applyPlan(current, plan, apps, maxDock, columns, rows)
    }

    fun defaultCategoryPlan(apps: List<CatalogApp>, current: ScreenLayout, maxDock: Int): Plan {
        val byKey = apps.associateBy { it.key }
        val dock = (current.dock.mapNotNull { byKey[it] } + apps.sortedByDescending { it.usage })
            .distinctBy { it.key }
            .take(maxDock.coerceAtLeast(0))
            .map { it.label }
        val placed = current.placedApps().mapNotNull { byKey[it] }.ifEmpty { apps }
        val groups = LinkedHashMap<String, MutableList<String>>()
        for (app in placed) {
            if (dock.any { it.equals(app.label, ignoreCase = true) }) continue
            val name = app.category.ifBlank { HomeCategories.OTHER }
            groups.getOrPut(name) { ArrayList() }.add(app.label)
        }
        val folders = groups.filter { it.value.size >= 2 }.map { PlanFolder(it.key, it.value) }
        val singles = groups.filter { it.value.size == 1 }.map { PlanItem(name = it.value.first()) }
        return Plan(dock = dock, folders = folders, home = folders.map { PlanItem(type = "folder", folder = it.name, apps = it.apps) } + singles)
    }

    /** Lightweight reading of "на док X, Y" / "папка Игры: A, B" from a Russian prompt. */
    fun parsePromptHints(prompt: String, apps: List<CatalogApp>): Plan? {
        if (prompt.isBlank()) return null
        val text = prompt.trim()
        val dock = ArrayList<String>()
        val folders = ArrayList<PlanFolder>()
        val dockMatch = Regex("(?:на\\s+док[аеу]?|в\\s+док[еу]|док)\\s*[:\\-–]?\\s*(.+?)(?:\\.|$|;|папк)", RegexOption.IGNORE_CASE).find(text)
        if (dockMatch != null) dock += splitNames(dockMatch.groupValues[1])
        val folderRx = Regex("папк[ауие]\\s+[«\"“]?([^«»\":\\n]+)[»\"”]?\\s*[:\\-–]\\s*([^\\n;]+)", RegexOption.IGNORE_CASE)
        for (m in folderRx.findAll(text)) folders += PlanFolder(m.groupValues[1].trim(), splitNames(m.groupValues[2]))
        val wantsCategories = Regex("по\\s+категор|по\\s+папк|разлож", RegexOption.IGNORE_CASE).containsMatchIn(text)
        if (dock.isEmpty() && folders.isEmpty() && !wantsCategories) return null
        if (wantsCategories && folders.isEmpty()) {
            return defaultCategoryPlan(apps, ScreenLayout(), dock.size.coerceAtLeast(4)).let {
                it.copy(dock = dock.ifEmpty { it.dock })
            }
        }
        return Plan(dock = dock, folders = folders, home = folders.map { PlanItem(type = "folder", folder = it.name, apps = it.apps) })
    }

    fun buildPrompt(apps: List<CatalogApp>, request: String, columns: Int, rows: Int, maxDock: Int): String {
        val list = apps.sortedByDescending { it.usage }.take(160).joinToString("\n") { app ->
            "- ${app.label} | ${app.pkg} | ${app.category}"
        }
        return """
Ты расставляешь приложения на домашнем экране Android-лаунчера.
Сетка: $columns столбцов × $rows рядов на страницу, в доке не больше $maxDock иконок.

Просьба пользователя:
$request

Установленные приложения (название | пакет | категория):
$list

Ответь ТОЛЬКО одним JSON-объектом, без пояснений, такой формы:
{
  "dock": ["Название или package"],
  "folders": [{"name": "Имя папки", "apps": ["Название или package"]}],
  "pages": [
    {"items": [
      {"type": "app", "name": "Название", "pkg": "optional.package", "col": 0, "row": 0},
      {"type": "folder", "folder": "Имя папки", "apps": ["A", "B"], "col": 1, "row": 0}
    ]}
  ]
}
Используй только приложения из списка. Не выдумывай пакеты. Если чего-то нет — пропусти.
        """.trim()
    }

    private fun splitNames(raw: String): List<String> =
        raw.split(Regex("(?:,|;|\\s+и\\s+|\\n)+"))
            .map { it.trim().trim('«', '»', '"', '“', '”', '.', ' ') }
            .filter { it.length >= 2 }

    private val aliases: Map<String, List<String>> = mapOf(
        "телефон" to listOf("dialer", "phone", "contacts", "com.android.contacts", "incallui"),
        "звонки" to listOf("dialer", "phone", "incallui"),
        "сообщения" to listOf("mms", "messaging", "sms", "messages"),
        "смс" to listOf("mms", "messaging", "sms", "messages"),
        "камера" to listOf("camera"),
        "галерея" to listOf("gallery", "photos", "album"),
        "настройки" to listOf("settings", "oemsettings"),
        "часы" to listOf("deskclock", "clock", "alarmclock"),
        "календарь" to listOf("calendar"),
        "браузер" to listOf("browser", "chrome", "firefox", "opera", "yandex.browser"),
        "почта" to listOf("mail", "gmail", "email"),
        "карты" to listOf("maps", "2gis", "dgis"),
        "музыка" to listOf("music", "youtube.music", "spotify", "zvuk"),
        "youtube" to listOf("youtube"),
        "ютуб" to listOf("youtube"),
        "телеграм" to listOf("telegram", "org.telegram"),
        "whatsapp" to listOf("whatsapp"),
        "ватсап" to listOf("whatsapp"),
    )

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        if (kotlin.math.abs(a.length - b.length) > 2) return 99
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            for (j in prev.indices) prev[j] = cur[j]
        }
        return prev[b.length]
    }
}

/**
 * Tiny JSON reader for the objects the model is asked to emit.
 * Avoids bringing a JSON library into unit tests or depending on Android's org.json.
 */
internal object JsonMini {
    class Obj(private val map: Map<String, Any?>) {
        fun str(key: String): String? = when (val v = map[key]) {
            is String -> v
            is Number, is Boolean -> v.toString()
            else -> null
        }
        fun int(key: String): Int? = when (val v = map[key]) {
            is Int -> v
            is Long -> v.toInt()
            is Double -> v.toInt()
            is String -> v.toIntOrNull()
            else -> null
        }
        fun list(key: String): List<Any?>? = map[key] as? List<*>
        fun stringList(key: String): List<String> = (map[key] as? List<*>)?.mapNotNull {
            when (it) {
                is String -> it
                is Map<*, *> -> (it["name"] ?: it["label"] ?: it["pkg"] ?: it["package"]) as? String
                else -> it?.toString()
            }?.takeIf { s -> s.isNotBlank() }
        } ?: emptyList()
        fun objList(key: String): List<Obj> = (map[key] as? List<*>)?.mapNotNull {
            @Suppress("UNCHECKED_CAST") (it as? Map<String, Any?>)?.let { m -> Obj(m) }
        } ?: emptyList()
    }

    fun parseObject(src: String): Obj {
        val v = Parser(src).parseValue()
        @Suppress("UNCHECKED_CAST")
        val map = v as? Map<String, Any?> ?: throw IllegalArgumentException("Ожидался JSON-объект")
        return Obj(map)
    }

    private class Parser(private val s: String) {
        private var i = 0
        fun parseValue(): Any? {
            skip()
            if (i >= s.length) throw IllegalArgumentException("Обрыв JSON")
            return when (s[i]) {
                '{' -> parseObj()
                '[' -> parseArr()
                '"' -> parseStr()
                't' -> { expect("true"); true }
                'f' -> { expect("false"); false }
                'n' -> { expect("null"); null }
                else -> parseNum()
            }
        }
        private fun parseObj(): Map<String, Any?> {
            expect("{"); skip()
            val map = LinkedHashMap<String, Any?>()
            if (peek('}')) { i++; return map }
            while (true) {
                skip()
                val key = parseStr()
                skip(); expect(":"); skip()
                map[key] = parseValue()
                skip()
                when {
                    peek(',') -> { i++; continue }
                    peek('}') -> { i++; return map }
                    else -> throw IllegalArgumentException("Ожидалась запятая в объекте")
                }
            }
        }
        private fun parseArr(): List<Any?> {
            expect("["); skip()
            val out = ArrayList<Any?>()
            if (peek(']')) { i++; return out }
            while (true) {
                out += parseValue()
                skip()
                when {
                    peek(',') -> { i++; continue }
                    peek(']') -> { i++; return out }
                    else -> throw IllegalArgumentException("Ожидалась запятая в массиве")
                }
            }
        }
        private fun parseStr(): String {
            expect("\"")
            val sb = StringBuilder()
            while (i < s.length) {
                val ch = s[i++]
                when (ch) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= s.length) break
                        when (val e = s[i++]) {
                            '"', '\\', '/' -> sb.append(e)
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                val hex = s.substring(i, (i + 4).coerceAtMost(s.length))
                                sb.append(hex.toInt(16).toChar()); i += hex.length
                            }
                            else -> sb.append(e)
                        }
                    }
                    else -> sb.append(ch)
                }
            }
            throw IllegalArgumentException("Незакрытая строка")
        }
        private fun parseNum(): Number {
            val start = i
            if (peek('-')) i++
            while (i < s.length && s[i] in '0'..'9') i++
            var frac = false
            if (peek('.')) { frac = true; i++; while (i < s.length && s[i] in '0'..'9') i++ }
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                frac = true; i++
                if (peek('+') || peek('-')) i++
                while (i < s.length && s[i] in '0'..'9') i++
            }
            val raw = s.substring(start, i)
            return if (frac) raw.toDouble() else raw.toLong().let { if (it in Int.MIN_VALUE..Int.MAX_VALUE) it.toInt() else it }
        }
        private fun skip() { while (i < s.length && s[i].isWhitespace()) i++ }
        private fun peek(ch: Char) = i < s.length && s[i] == ch
        private fun expect(tok: String) {
            skip()
            if (!s.startsWith(tok, i)) throw IllegalArgumentException("Ожидалось $tok")
            i += tok.length
        }
    }
}
