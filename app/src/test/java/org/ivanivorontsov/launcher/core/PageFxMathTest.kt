package org.ivanivorontsov.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageFxMathTest {

    @Test fun bendIsZeroAtRestAndOneAtHalfway() {
        assertEquals(0f, PageFxMath.bend(0f), 1e-5f)
        assertEquals(0f, PageFxMath.bend(2f), 1e-5f)
        assertEquals(1f, PageFxMath.bend(0.5f), 1e-5f)
        assertEquals(1f, PageFxMath.bend(1.5f), 1e-5f)
        assertTrue(PageFxMath.bend(0.1f) < PageFxMath.bend(0.25f))
    }

    @Test fun stackZPutsIncomingPageOnTopWhenSwipingBack() {
        // Going from page 1 back to page 0: position 0.5, page 0 has o = -0.5, page 1 has o = +0.5.
        val incoming = PageFxMath.zIndex(PageEffect.STACK, -0.5f)
        val leaving = PageFxMath.zIndex(PageEffect.STACK, 0.5f)
        assertTrue("incoming card must be above the leaving one, got $incoming vs $leaving", incoming > leaving)
        // Forward swipe: leaving page 0 has o = -0.5, incoming page 1 has o = +0.5 — same rule, top card follows the finger.
        assertTrue(PageFxMath.zIndex(PageEffect.STACK, -0.5f) > PageFxMath.zIndex(PageEffect.STACK, 0.5f))
        // Default effects still prefer the closest page, both sides equal at mid-swipe.
        assertEquals(PageFxMath.zIndex(PageEffect.CUBE, -0.5f), PageFxMath.zIndex(PageEffect.CUBE, 0.5f), 0f)
    }

    @Test fun depthUsesTheSameDirectionalRuleAsStack() {
        assertTrue(PageFxMath.zIndex(PageEffect.DEPTH, -0.4f) > PageFxMath.zIndex(PageEffect.DEPTH, 0.4f))
    }

    @Test fun cylinderFrontColumnIsCloserThanEdge() {
        val mid = PageFxMath.cylinderDepth(0f, 0f, 1000f, 1f)
        val edge = PageFxMath.cylinderDepth(400f, 0f, 1000f, 1f)
        assertTrue("front=$mid edge=$edge", mid > edge)
        assertEquals(0f, PageFxMath.cylinderDepth(0f, 0f, 1000f, 0f), 0f)
    }

    @Test fun seamScaleAddsOverlap() {
        assertEquals(1.02f, PageFxMath.seamScale(1f), 1e-5f)
        assertTrue(PageFxMath.seamScale(0.8f) > 0.8f)
    }
}
