package org.ivanivorontsov.launcher.core

import kotlinx.serialization.Serializable

/**
 * 1.3.9: widgets placed right on the home pages, on the icon grid.
 *
 * A widget is an item of [ScreenLayout.home] with the id `widget:<n>` (system widget, n = AppWidget id)
 * or [CLOCK_WIDGET] (the launcher's own clock). Its size in cells lives in [ScreenLayout.widgets].
 * Pages are produced by [packPages]: items keep their order (reading order), each one goes to the first
 * free place of the current page that fits its span; when it no longer fits, a new page starts.
 */
const val WIDGET_PREFIX = "widget:"
const val CLOCK_WIDGET = "widget:clock"
fun isWidgetId(id: String): Boolean = id.startsWith(WIDGET_PREFIX)
fun systemWidgetKey(appWidgetId: Int): String = WIDGET_PREFIX + appWidgetId

@Serializable enum class HomeWidgetKind { SYSTEM, CLOCK }

@Serializable
data class HomeWidget(
    val id: String,
    val kind: HomeWidgetKind = HomeWidgetKind.SYSTEM,
    val appWidgetId: Int = -1,
    val spanX: Int = 4,
    val spanY: Int = 2,
    /** Clock only: own look; null = the theme's clock style. */
    val clockStyle: ClockStyle? = null,
)

/** One item placed on a page: cell position and span. [index] is its position in [ScreenLayout.home]. */
data class Placed(val id: String, val col: Int, val row: Int, val w: Int, val h: Int, val index: Int)

class PackedPages(val cols: Int, val rows: Int, val pages: List<List<Placed>>, val total: Int) {
    val size: Int get() = pages.size
    fun page(i: Int): List<Placed> = pages.getOrNull(i).orEmpty()

    /** Item covering the cell, or null. */
    fun at(page: Int, col: Int, row: Int): Placed? = page(page).firstOrNull { col >= it.col && col < it.col + it.w && row >= it.row && row < it.row + it.h }

    /**
     * Insertion index in `home` for a drop on (page, col, row): the item under the finger, else the first item
     * placed after that cell (reading order), else right after the page's last item.
     */
    fun indexAt(page: Int, col: Int, row: Int): Int {
        at(page, col, row)?.let { return it.index }
        val cell = row * cols + col
        val items = page(page)
        items.filter { it.row * cols + it.col > cell }.minByOrNull { it.index }?.let { return it.index }
        items.maxByOrNull { it.index }?.let { return it.index + 1 }
        // Empty page (e.g. the extra page shown while dragging): append after everything before it.
        return pages.take(page.coerceAtLeast(0)).flatten().maxOfOrNull { it.index + 1 } ?: total
    }
}

fun ScreenLayout.spanOf(id: String, cols: Int, rows: Int): Pair<Int, Int> {
    val w = widgets[id] ?: return 1 to 1
    return w.spanX.coerceIn(1, cols.coerceAtLeast(1)) to w.spanY.coerceIn(1, rows.coerceAtLeast(1))
}

/** Packs [ScreenLayout.home] into pages of [cols]×[rows] cells (always at least one page). */
fun ScreenLayout.packPages(cols: Int, rows: Int): PackedPages {
    val c = cols.coerceAtLeast(1); val r = rows.coerceAtLeast(1)
    val pages = ArrayList<List<Placed>>()
    var cur = ArrayList<Placed>()
    var occ = BooleanArray(c * r)
    fun fits(x: Int, y: Int, w: Int, h: Int): Boolean {
        if (x + w > c || y + h > r) return false
        for (yy in y until y + h) for (xx in x until x + w) if (occ[yy * c + xx]) return false
        return true
    }
    fun firstFit(w: Int, h: Int): Pair<Int, Int>? {
        for (y in 0..r - h) for (x in 0..c - w) if (fits(x, y, w, h)) return x to y
        return null
    }
    home.forEachIndexed { i, id ->
        val (w, h) = spanOf(id, c, r)
        var pos = firstFit(w, h)
        if (pos == null) { pages += cur; cur = ArrayList(); occ = BooleanArray(c * r); pos = firstFit(w, h) ?: (0 to 0) }
        val (x, y) = pos
        for (yy in y until y + h) for (xx in x until x + w) occ[yy * c + xx] = true
        cur += Placed(id, x, y, w, h, i)
    }
    pages += cur
    return PackedPages(c, r, pages, home.size)
}

/** Adds a widget at [index] of `home` (default: end). */
fun ScreenLayout.addWidget(w: HomeWidget, index: Int? = null): ScreenLayout {
    val h = home - w.id
    return copy(home = if (index == null) h + w.id else h.insertAt(index, w.id), widgets = widgets + (w.id to w), clockPlaced = clockPlaced || w.kind == HomeWidgetKind.CLOCK)
}

fun ScreenLayout.resizeWidget(id: String, spanX: Int, spanY: Int, cols: Int, rows: Int): ScreenLayout {
    val w = widgets[id] ?: return this
    val nw = w.copy(spanX = spanX.coerceIn(1, cols), spanY = spanY.coerceIn(1, rows))
    return if (nw == w) this else copy(widgets = widgets + (id to nw))
}

fun ScreenLayout.updateWidget(id: String, f: (HomeWidget) -> HomeWidget): ScreenLayout {
    val w = widgets[id] ?: return this
    return copy(widgets = widgets + (id to f(w)))
}

/** System AppWidget ids used by this layout (the host must keep them). */
fun ScreenLayout.appWidgetIds(): Set<Int> = widgets.values.filter { it.kind == HomeWidgetKind.SYSTEM && it.id in home }.map { it.appWidgetId }.toSet()

/**
 * Before 1.3.9 the clock was a fixed 2-row header on the first page of grid themes. It is now a normal widget:
 * placed once at the top of the first page, then the user can move, resize or remove it.
 */
fun ScreenLayout.withClockWidget(cols: Int): ScreenLayout =
    if (clockPlaced) this else addWidget(HomeWidget(CLOCK_WIDGET, HomeWidgetKind.CLOCK, spanX = cols, spanY = 2), 0)

/** Default span of a system widget from its minimum size in dp (Launcher3-like ~70 dp per cell). */
fun cellsFor(dp: Int, max: Int): Int = ((dp + 30) / 70).coerceIn(1, max.coerceAtLeast(1))

/**
 * 1.4.6: grid size change (Launcher3 GridSizeMigrationUtil idea, simplified for our ordered layout).
 * Item order, folders and dock are kept as is; only widget spans are remapped: a widget that filled the
 * old width/height keeps filling the new one, others scale proportionally (rounded, at least 1 cell).
 */
fun ScreenLayout.remapGrid(oldCols: Int, oldRows: Int, newCols: Int, newRows: Int): ScreenLayout {
    if ((oldCols == newCols && oldRows == newRows) || widgets.isEmpty()) return this
    fun scale(span: Int, old: Int, new: Int): Int {
        val o = old.coerceAtLeast(1); val n = new.coerceAtLeast(1)
        if (span >= o) return n
        return Math.round(span.toFloat() * n / o).coerceIn(1, n)
    }
    val next = widgets.mapValues { (_, w) ->
        w.copy(spanX = scale(w.spanX, oldCols, newCols), spanY = scale(w.spanY, oldRows, newRows))
    }
    return if (next == widgets) this else copy(widgets = next)
}
