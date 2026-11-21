package org.ivanivorontsov.launcher.core

import kotlin.math.min

/**
 * Icon, gap and label sizes for a home / dock / folder / drawer grid.
 * Derived from the live cell size so changing the column count or rotating
 * to landscape never leaves theme-sized icons touching or labels clipped.
 */
data class GridFit(
    val columns: Int,
    val cellWidthDp: Float,
    val iconDp: Float,
    val gapDp: Float,
    val labelWidthDp: Float,
    val labelScale: Float,
    val cellHeightDp: Float = cellWidthDp,
) {
    companion object {
        const val MIN_GAP_DP = 10f
        const val ICON_IN_CELL = 0.72f
        const val MIN_ICON_DP = 28f
        const val LABEL_BLOCK_DP = 18f
        const val DOCK_MAX_DP = 56f
        const val DOCK_MAX_FRACTION = 0.16f

        /**
         * [widthDp] is the width available to the grid (already after insets/padding).
         * [themeIconDp] is the theme's preferred icon size; it is only used as an upper bound.
         */
        fun of(columns: Int, widthDp: Float, themeIconDp: Float): GridFit =
            ofCells(columns, 1, widthDp, widthDp / columns.coerceAtLeast(1), themeIconDp, showLabels = false)

        fun ofCells(
            columns: Int,
            rows: Int,
            widthDp: Float,
            heightDp: Float,
            themeIconDp: Float,
            showLabels: Boolean = true,
        ): GridFit {
            val cols = columns.coerceAtLeast(1)
            val r = rows.coerceAtLeast(1)
            val cellW = (widthDp / cols).coerceAtLeast(1f)
            val cellH = (heightDp / r).coerceAtLeast(1f)
            val label = if (showLabels) LABEL_BLOCK_DP else 0f
            val cap = min(themeIconDp, min(cellW, cellH) * ICON_IN_CELL)
            val byWidth = cellW - MIN_GAP_DP
            val byHeight = cellH - label - (if (showLabels) 4f else MIN_GAP_DP)
            val icon = min(cap, min(byWidth, byHeight)).coerceAtLeast(min(MIN_ICON_DP, min(cellW, cellH) * 0.5f))
            val gap = (cellW - icon).coerceAtLeast(0f)
            val labelScale = when {
                cols >= 7 -> 0.84f
                cols >= 6 -> 0.92f
                else -> 1f
            }
            return GridFit(cols, cellW, icon, gap, cellW, labelScale, cellH)
        }

        /**
         * Portrait uses the user's column/row counts. Landscape derives a wider, shorter
         * grid so cells stay tall enough for the icon and label, caps the dock, and
         * signals that pages should be swiped vertically (tablet-style).
         */
        fun screen(
            widthDp: Float,
            heightDp: Float,
            portraitColumns: Int,
            portraitRows: Int,
            themeIconDp: Float,
            showLabels: Boolean = true,
        ): ScreenGrid {
            val landscape = widthDp > heightDp
            val pCols = portraitColumns.coerceAtLeast(1)
            val pRows = portraitRows.coerceAtLeast(1)
            val dockH = if (landscape) min(DOCK_MAX_DP, heightDp * DOCK_MAX_FRACTION).coerceAtLeast(36f)
            else (themeIconDp + 28f).coerceAtMost(heightDp * 0.28f)
            val gridH = (heightDp - dockH).coerceAtLeast(1f)
            val gridW = widthDp.coerceAtLeast(1f)
            val cols: Int
            val rows: Int
            if (landscape) {
                val label = if (showLabels) LABEL_BLOCK_DP else 0f
                val minCellH = MIN_ICON_DP + MIN_GAP_DP + label
                val minCellW = MIN_ICON_DP + MIN_GAP_DP
                var c = pRows.coerceAtLeast(pCols)
                var r = pCols.coerceAtLeast(1)
                while (r > 1 && gridH / r < minCellH) r--
                while (c > 2 && gridW / c < minCellW) c--
                val want = pCols * pRows
                while (c * r < want && gridW / (c + 1) >= minCellW) c++
                cols = c
                rows = r
            } else {
                cols = pCols
                rows = pRows
            }
            val fit = ofCells(cols, rows, gridW, gridH, themeIconDp, showLabels)
            val dockIcon = min(themeIconDp, min(fit.iconDp, dockH - 12f)).coerceAtLeast(min(MIN_ICON_DP, dockH * 0.6f))
            return ScreenGrid(
                landscape = landscape,
                verticalPager = landscape,
                columns = cols,
                rows = rows,
                fit = fit,
                cellHeightDp = fit.cellHeightDp,
                dockHeightDp = dockH,
                dockIconDp = dockIcon,
                showLabels = showLabels,
                gridWidthDp = gridW,
                gridHeightDp = gridH,
            )
        }
    }
}

data class ScreenGrid(
    val landscape: Boolean,
    val verticalPager: Boolean,
    val columns: Int,
    val rows: Int,
    val fit: GridFit,
    val cellHeightDp: Float,
    val dockHeightDp: Float,
    val dockIconDp: Float,
    val showLabels: Boolean,
    val gridWidthDp: Float,
    val gridHeightDp: Float,
)
