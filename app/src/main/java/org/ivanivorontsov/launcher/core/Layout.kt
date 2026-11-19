package org.ivanivorontsov.launcher.core

import kotlinx.serialization.Serializable

const val FOLDER_PREFIX = "folder:"
fun isFolderId(id: String): Boolean = id.startsWith(FOLDER_PREFIX)

@Serializable
data class Folder(val id: String, val name: String = "Папка", val apps: List<String> = emptyList())

/** Where a dragged item would land. Indices are positions in the target list *after* the item was lifted out. */
sealed interface DropSpot {
    data class Home(val index: Int) : DropSpot
    data class Dock(val index: Int) : DropSpot
    data class Into(val target: String) : DropSpot
    data class Fav(val index: Int) : DropSpot
    data class Tile(val index: Int) : DropSpot
}

/**
 * Everything placed on the home screen of one theme. [home] and [dock] hold app keys or folder ids
 * ("folder:<n>"). Pure and immutable so drag previews are just `layout.place(dragged, spot)`.
 */
@Serializable
data class ScreenLayout(
    val home: List<String> = emptyList(),
    val dock: List<String> = emptyList(),
    val folders: Map<String, Folder> = emptyMap(),
    val tiles: List<TileItem> = emptyList(),
    val favorites: List<String> = emptyList(),
) {
    fun folderOf(appKey: String): String? = folders.values.firstOrNull { appKey in it.apps }?.id

    /** Placed on a home page, in the dock or inside a folder. */
    fun isPlaced(key: String): Boolean = key in home || key in dock || folderOf(key) != null

    fun hasTileFor(appKey: String): Boolean = tiles.any { it.appKey == appKey }

    /** All app keys reachable from home/dock (folders expanded). */
    fun placedApps(): List<String> = (dock + home).flatMap { if (isFolderId(it)) folders[it]?.apps.orEmpty() else listOf(it) }

    /**
     * Lifts [id] out of home/dock/folders. A folder left with a single app dissolves into that app in place.
     * Folder ids keep their [folders] entry so the folder can be re-placed during a drag.
     */
    fun detach(id: String): ScreenLayout {
        var l = copy(home = home - id, dock = dock - id)
        if (!isFolderId(id)) {
            val f = folderOf(id)
            if (f != null) l = l.withFolderApps(f, folders.getValue(f).apps - id)
        }
        return l
    }

    private fun withFolderApps(folderId: String, apps: List<String>): ScreenLayout {
        val f = folders[folderId] ?: return this
        return when (apps.size) {
            0 -> copy(home = home - folderId, dock = dock - folderId, folders = folders - folderId)
            1 -> copy(home = home.map { if (it == folderId) apps[0] else it }, dock = dock.map { if (it == folderId) apps[0] else it },
                folders = folders - folderId)
            else -> copy(folders = folders + (folderId to f.copy(apps = apps)))
        }
    }

    fun nextFolderId(): String {
        var n = 1
        while (FOLDER_PREFIX + n in folders) n++
        return FOLDER_PREFIX + n
    }

    /**
     * Moves [id] (app key or folder id) to [spot]. Returns null when the drop is not allowed
     * (full dock, folder into folder, dropping onto itself, unknown target).
     */
    fun place(id: String, spot: DropSpot, maxDock: Int = 5, folderName: String = "Папка"): ScreenLayout? {
        val base = detach(id)
        return when (spot) {
            is DropSpot.Home -> base.copy(home = base.home.insertAt(spot.index, id))
            is DropSpot.Dock -> if (base.dock.size >= maxDock) null else base.copy(dock = base.dock.insertAt(spot.index, id))
            is DropSpot.Into -> base.mergeInto(id, spot.target, folderName)
            is DropSpot.Fav -> copy(favorites = (favorites - id).insertAt(spot.index, id))
            is DropSpot.Tile -> null
        }
    }

    private fun mergeInto(id: String, target: String, folderName: String): ScreenLayout? {
        if (id == target || isFolderId(id)) return null
        if (isFolderId(target)) {
            val f = folders[target] ?: return null
            if (target !in home && target !in dock) return null
            return copy(folders = folders + (target to f.copy(apps = (f.apps - id) + id)))
        }
        val inHome = home.indexOf(target); val inDock = dock.indexOf(target)
        if (inHome < 0 && inDock < 0) return null
        val fid = nextFolderId()
        val folder = Folder(fid, folderName, listOf(target, id))
        return copy(
            home = if (inHome >= 0) home.toMutableList().also { it[inHome] = fid } else home,
            dock = if (inHome < 0) dock.toMutableList().also { it[inDock] = fid } else dock,
            folders = folders + (fid to folder),
        )
    }


    /** "Удалить с экрана": removes an app (from home, dock, folders, favourites, tiles) or a whole folder. */
    fun remove(id: String): ScreenLayout =
        if (isFolderId(id)) copy(home = home - id, dock = dock - id, folders = folders - id)
        else detach(id).copy(favorites = favorites - id, tiles = tiles.filter { it.appKey != id })

    /** Replaces the folder by its apps, in place. */
    fun ungroup(id: String): ScreenLayout {
        val f = folders[id] ?: return this
        fun expand(l: List<String>) = l.flatMap { if (it == id) f.apps else listOf(it) }
        return copy(home = expand(home), dock = expand(dock), folders = folders - id)
    }

    fun renameFolder(id: String, name: String): ScreenLayout {
        val f = folders[id] ?: return this
        return copy(folders = folders + (id to f.copy(name = name.trim().ifEmpty { f.name })))
    }

    /**
     * Groups loose icons on the home grid into category folders.
     *
     * The dock, tiles and favourites are left untouched. Folders the user already created stay
     * where they are and are not unpacked — apps inside them are not moved. If a folder already
     * on the home screen has the same name (ignoring case), loose apps of that category are
     * merged into it. A category with a single loose app and no such folder stays an icon:
     * [normalized] dissolves folders of fewer than two apps.
     *
     * New folders are inserted where the first app of that category sat, so the grid does not
     * jump everything to the end.
     */
    fun groupHomeIntoFolders(folderName: (appKey: String) -> String): ScreenLayout {
        if (home.none { !isFolderId(it) }) return this
        val nameToId = LinkedHashMap<String, String>()
        for (id in home) {
            if (!isFolderId(id)) continue
            val folder = folders[id] ?: continue
            nameToId.putIfAbsent(folder.name.trim().lowercase(), id)
        }
        val looseByName = LinkedHashMap<String, MutableList<String>>()
        for (id in home) {
            if (isFolderId(id)) continue
            val raw = folderName(id).trim().ifEmpty { "Прочее" }
            looseByName.getOrPut(raw) { ArrayList() }.add(id)
        }
        var foldersAcc = folders
        fun alloc(): String {
            var n = 1
            var id = FOLDER_PREFIX + n
            while (id in foldersAcc) {
                n++
                id = FOLDER_PREFIX + n
            }
            return id
        }
        data class Plan(val folderId: String, val create: Boolean)
        val plans = LinkedHashMap<String, Plan>()
        for ((raw, keys) in looseByName) {
            val existing = nameToId[raw.lowercase()]
            if (existing != null) {
                val folder = foldersAcc.getValue(existing)
                foldersAcc = foldersAcc + (existing to folder.copy(apps = (folder.apps + keys).distinct()))
                plans[raw] = Plan(existing, create = false)
            } else if (keys.size >= 2) {
                val id = alloc()
                foldersAcc = foldersAcc + (id to Folder(id, raw, keys.distinct()))
                nameToId[raw.lowercase()] = id
                plans[raw] = Plan(id, create = true)
            }
        }
        if (plans.isEmpty()) return this
        val placedNew = HashSet<String>()
        val newHome = ArrayList<String>(home.size)
        for (id in home) {
            if (isFolderId(id)) {
                newHome += id
                continue
            }
            val raw = folderName(id).trim().ifEmpty { "Прочее" }
            val plan = plans[raw]
            if (plan == null) {
                newHome += id
                continue
            }
            if (!plan.create) continue
            if (placedNew.add(plan.folderId)) newHome += plan.folderId
        }
        return copy(home = newHome, folders = foldersAcc).normalized()
    }

    fun appendHome(key: String): ScreenLayout = if (isPlaced(key)) this else copy(home = home + key)

    fun addToDock(key: String, max: Int): ScreenLayout = if (key in dock || dock.size >= max) this else detach(key).let { it.copy(dock = it.dock + key) }

    fun addFavorite(key: String, max: Int = 8): ScreenLayout = if (key in favorites || favorites.size >= max) this else copy(favorites = favorites + key)

    fun removeFavorite(key: String): ScreenLayout = copy(favorites = favorites - key)

    fun pinTile(item: TileItem, at: Int? = null): ScreenLayout {
        val dup = if (item.kind == TileKind.APP) tiles.any { it.appKey == item.appKey } else tiles.any { it.kind == item.kind }
        if (dup) return this
        return copy(tiles = if (at == null) tiles + item else tiles.insertAt(at, item))
    }

    fun resizeTile(id: String, size: TileSize? = null): ScreenLayout =
        copy(tiles = tiles.map { if (it.id == id) it.copy(size = size ?: it.size.next()) else it })

    fun unpinTile(id: String): ScreenLayout = copy(tiles = tiles.filter { it.id != id })

    fun moveTile(id: String, to: Int): ScreenLayout {
        val from = tiles.indexOfFirst { it.id == id }
        if (from < 0) return this
        val l = tiles.toMutableList(); val t = l.removeAt(from); l.add(to.coerceIn(0, l.size), t)
        return copy(tiles = l)
    }

    /** Splits the flat home list into pages; the first page may hold fewer items (clock/widget area). */
    fun pages(firstPageCapacity: Int, pageCapacity: Int): List<List<String>> {
        val out = ArrayList<List<String>>()
        val first = home.take(firstPageCapacity.coerceAtLeast(0))
        out += first
        var rest = home.drop(first.size)
        val cap = pageCapacity.coerceAtLeast(1)
        while (rest.isNotEmpty()) { out += rest.take(cap); rest = rest.drop(cap) }
        return out
    }

    /** Index in [home] where page [page] (0-based app page) starts. */
    fun pageStart(page: Int, firstPageCapacity: Int, pageCapacity: Int): Int =
        if (page <= 0) 0 else firstPageCapacity.coerceAtLeast(0) + (page - 1) * pageCapacity.coerceAtLeast(1)

    /** Removes unknown apps, duplicates, dangling folder refs; dissolves 0/1-app folders. */
    fun normalized(existing: Set<String>? = null): ScreenLayout {
        fun ok(k: String) = existing == null || k in existing
        val seen = HashSet<String>()
        val fs = LinkedHashMap<String, Folder>()
        fun clean(list: List<String>): List<String> = list.mapNotNull { id ->
            if (!seen.add(id)) return@mapNotNull null
            if (isFolderId(id)) {
                val f = folders[id] ?: return@mapNotNull null
                val apps = f.apps.filter { ok(it) && it !in seen }.distinct()
                seen += apps
                when (apps.size) { 0 -> null; 1 -> apps[0]; else -> { fs[id] = f.copy(apps = apps); id } }
            } else if (ok(id)) id else null
        }
        val d = clean(dock)
        val h = clean(home)
        return ScreenLayout(h, d, fs, tiles.filter { (it.kind != TileKind.APP || (it.appKey != null && ok(it.appKey))) }.distinctBy { it.id },
            favorites.filter { ok(it) }.distinct())
    }

    /** A newly installed app goes to the first free slot of every kind of home screen. */
    fun withNewApp(key: String, tileId: String = "t_$key"): ScreenLayout {
        var l = appendHome(key)
        l = l.pinTile(TileItem(tileId, TileKind.APP, key, TileSize.SMALL))
        if (l.favorites.isNotEmpty()) l = l.addFavorite(key)
        return l
    }
}

internal fun <T> List<T>.insertAt(index: Int, item: T): List<T> {
    val l = toMutableList(); l.add(index.coerceIn(0, l.size), item); return l
}

/**
 * «Стоп-экран» (1.2): forbidden apps are veiled — the UI and every edit see the layout without them, but their
 * places are remembered, so turning the mode off brings everything back exactly where it was.
 */
object LayoutVeil {
    @Volatile var keys: Set<String> = emptySet()
}

/** The layout as shown while [hidden] apps are forbidden: no gaps, folders keep only visible apps. */
fun ScreenLayout.veiled(hidden: Set<String>): ScreenLayout {
    if (hidden.isEmpty()) return this
    val fs = folders.mapValues { (_, f) -> f.copy(apps = f.apps.filter { it !in hidden }) }
    fun vis(id: String) = if (isFolderId(id)) (fs[id]?.apps?.isNotEmpty() == true) else id !in hidden
    return copy(home = home.filter(::vis), dock = dock.filter(::vis), folders = fs,
        tiles = tiles.filter { it.appKey == null || it.appKey !in hidden }, favorites = favorites.filter { it !in hidden })
}

/** Puts the veiled apps of [orig] back into [edited] (a result of editing `orig.veiled(hidden)`). Nothing is lost. */
fun ScreenLayout.unveiled(orig: ScreenLayout, hidden: Set<String>): ScreenLayout {
    if (hidden.isEmpty()) return this
    fun hiddenId(id: String) = if (isFolderId(id)) orig.folders[id]?.apps?.all { it in hidden } == true else id in hidden
    fun reinsert(list: List<String>, src: List<String>): List<String> {
        val out = list.toMutableList()
        src.forEachIndexed { i, id -> if (hiddenId(id) && id !in out) out.add(i.coerceAtMost(out.size), id) }
        return out
    }
    var home = reinsert(home, orig.home)
    val dock = reinsert(dock, orig.dock)
    val fs = LinkedHashMap(folders)
    for ((id, f) in orig.folders) {
        val hid = f.apps.filter { it in hidden }
        if (hid.isEmpty()) continue
        val cur = fs[id]
        if (cur != null) fs[id] = cur.copy(apps = cur.apps + hid.filter { it !in cur.apps })
        else if (id in home || id in dock) fs[id] = f
    }
    val l = ScreenLayout(home, dock, fs,
        tiles + orig.tiles.filter { t -> t.appKey != null && t.appKey in hidden && tiles.none { it.id == t.id } },
        reinsert(favorites, orig.favorites))
    // Whatever still went missing (e.g. its folder was taken apart) goes to the end of the home screen.
    val placed = l.placedApps().toHashSet()
    val lost = orig.placedApps().filter { it in hidden && it !in placed }
    if (lost.isNotEmpty()) home = home + lost
    return l.copy(home = home)
}
