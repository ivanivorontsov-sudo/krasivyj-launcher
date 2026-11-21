package org.ivanivorontsov.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GridFitTest {

    @Test fun gapAtLeast10dpForFourToSevenColumnsOnCommonPhones() {
        for (width in listOf(360f, 411f)) {
            for (cols in 4..7) {
                for (themeIcon in listOf(48f, 56f, 60f, 72f)) {
                    val f = GridFit.of(cols, width, themeIcon)
                    assertTrue(
                        "w=$width cols=$cols themeIcon=$themeIcon: gap ${f.gapDp}dp < 10, icon=${f.iconDp} cell=${f.cellWidthDp}",
                        f.gapDp >= GridFit.MIN_GAP_DP - 1e-4f,
                    )
                    assertTrue("icon larger than theme", f.iconDp <= themeIcon + 1e-4f)
                    assertTrue("icon larger than 72% of cell", f.iconDp <= f.cellWidthDp * GridFit.ICON_IN_CELL + 1e-4f)
                    assertEquals("label must use the full cell width", f.cellWidthDp, f.labelWidthDp, 1e-4f)
                    assertEquals(width / cols, f.cellWidthDp, 1e-4f)
                    assertEquals(f.cellWidthDp - f.iconDp, f.gapDp, 1e-4f)
                    if (cols >= 6) assertTrue("6–7 columns should use a slightly smaller label, got ${f.labelScale}", f.labelScale < 1f)
                    else assertEquals(1f, f.labelScale, 0f)
                }
            }
        }
    }

    @Test fun fiveColumnsOnGlassThemeNoLongerTouches() {
        // Стекло default icon is 60dp. With a 5-column grid on a 360dp phone and the
        // home inset (~6.8% each side) the raw 60dp icon left only a 2dp gap.
        val usable = 360f * (1f - 2f * 0.068f)
        val f = GridFit.of(5, usable, 60f)
        assertTrue("gap=${f.gapDp} icon=${f.iconDp} cell=${f.cellWidthDp}", f.gapDp >= 10f)
        assertTrue(f.iconDp < 60f)
        assertEquals(f.cellWidthDp, f.labelWidthDp, 0f)
    }

    @Test fun landscape800x360FitsIconsLabelsAndCappedDock() {
        val s = GridFit.screen(800f, 360f, 5, 6, 60f, showLabels = true)
        assertTrue("expected landscape", s.landscape && s.verticalPager)
        assertTrue("need at least one row/col", s.columns >= 1 && s.rows >= 1)
        assertTrue("cells too short for icon+label: h=${s.cellHeightDp} icon=${s.fit.iconDp}", s.cellHeightDp + 1e-3f >= s.fit.iconDp + GridFit.MIN_GAP_DP)
        assertTrue("gap ${s.fit.gapDp}", s.fit.gapDp >= GridFit.MIN_GAP_DP - 1e-4f)
        assertTrue("icon from min(cellW,cellH)", s.fit.iconDp <= minOf(s.fit.cellWidthDp, s.cellHeightDp) * GridFit.ICON_IN_CELL + 1e-3f)
        assertEquals(s.fit.cellWidthDp, s.fit.labelWidthDp, 1e-4f)
        assertTrue("dock ${s.dockHeightDp} too tall for 360dp", s.dockHeightDp <= GridFit.DOCK_MAX_DP + 0.1f)
        assertTrue(s.dockHeightDp < 360f * 0.25f)
        assertTrue(s.showLabels)
        assertTrue("grid plus dock should use the height", s.gridHeightDp + s.dockHeightDp <= 360f + 0.1f)
    }
}
