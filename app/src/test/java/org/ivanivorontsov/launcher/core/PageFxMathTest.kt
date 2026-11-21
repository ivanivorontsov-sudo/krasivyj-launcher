package org.ivanivorontsov.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test fun settledOffsetsShowExactlyOneIdentityPage() {
        val w = 1080f
        for (effect in PageEffect.entries) {
            val at0 = PageFxMath.pageTransform(effect, 0f, w)
            val atP1 = PageFxMath.pageTransform(effect, 1f, w)
            val atM1 = PageFxMath.pageTransform(effect, -1f, w)
            assertTrue("$effect at 0 must be visible identity, got $at0", at0.visible && at0.identity)
            assertFalse("$effect at +1 must be hidden, got $atP1", atP1.visible)
            assertFalse("$effect at -1 must be hidden, got $atM1", atM1.visible)
            assertEquals("$effect +1 alpha", 0f, atP1.alpha, 0f)
            assertEquals("$effect -1 alpha", 0f, atM1.alpha, 0f)
            assertTrue("$effect +1 translation off-screen: ${atP1.translationX}", atP1.translationX >= w)
            assertTrue("$effect -1 translation off-screen: ${atM1.translationX}", atM1.translationX <= -w)
            assertEquals(PageFxMath.Identity, PageFxMath.pageTransform(effect, 0f, w))
            // Leftovers after a cancelled fling / spring overshoot still collapse to one page.
            assertFalse(PageFxMath.pageTransform(effect, 1f - PageFxMath.SETTLE_EPS / 2f, w).visible)
            assertFalse(PageFxMath.pageTransform(effect, -1f + PageFxMath.SETTLE_EPS / 2f, w).visible)
            val near0 = PageFxMath.pageTransform(effect, PageFxMath.SETTLE_EPS / 2f, w)
            assertTrue("$effect near 0 must be identity, got $near0", near0.identity && near0.visible)
            assertTrue(PageFxMath.zIndex(effect, 1f) < PageFxMath.zIndex(effect, 0f))
            assertTrue(PageFxMath.zIndex(effect, -1f) < PageFxMath.zIndex(effect, 0f))
        }
    }

    @Test fun stackMidSwipeKeepsUnderCardVisibleThenHidesAtOne() {
        val w = 1080f
        val mid = PageFxMath.pageTransform(PageEffect.STACK, 0.5f, w)
        assertTrue(mid.visible)
        assertTrue("under-card stays centred, tx=${mid.translationX}", kotlin.math.abs(mid.translationX + 0.5f * w) < 0.5f)
        val top = PageFxMath.pageTransform(PageEffect.STACK, -0.5f, w)
        assertTrue(top.visible && top.identity)
        assertFalse(PageFxMath.pageTransform(PageEffect.STACK, 1f, w).visible)
        assertFalse(PageFxMath.pageTransform(PageEffect.STACK, -1f, w).visible)
        assertFalse(PageFxMath.pageTransform(PageEffect.STACK, 2f, w).visible)
    }

    @Test fun verticalSettledOffsetsShowExactlyOneIdentityPage() {
        val w = 800f
        val h = 360f
        for (effect in PageEffect.entries) {
            val at0 = PageFxMath.pageTransform(effect, 0f, w, h, vertical = true)
            val atP1 = PageFxMath.pageTransform(effect, 1f, w, h, vertical = true)
            val atM1 = PageFxMath.pageTransform(effect, -1f, w, h, vertical = true)
            assertTrue("$effect vertical 0 must be identity, got $at0", at0.visible && at0.identity)
            assertFalse("$effect vertical +1 visible $atP1", atP1.visible)
            assertFalse("$effect vertical -1 visible $atM1", atM1.visible)
            assertEquals(0f, atP1.alpha, 0f)
            assertTrue("$effect +1 must move off along Y, ty=${atP1.translationY}", atP1.translationY >= h)
            assertTrue("$effect -1 must move off along Y, ty=${atM1.translationY}", atM1.translationY <= -h)
            assertEquals(0f, atP1.translationX, 0f)
            assertEquals(0f, atM1.translationX, 0f)
        }
    }

    @Test fun verticalStackAndSlideUsePageHeight() {
        val w = 800f
        val h = 360f
        val mid = PageFxMath.pageTransform(PageEffect.STACK, 0.5f, w, h, vertical = true)
        assertTrue(mid.visible)
        assertEquals(0f, mid.translationX, 0f)
        assertTrue("under-card stays centred on Y, ty=${mid.translationY}", kotlin.math.abs(mid.translationY + 0.5f * h) < 0.5f)
        val top = PageFxMath.pageTransform(PageEffect.STACK, -0.5f, w, h, vertical = true)
        assertTrue(top.visible && top.identity)
        val slide = PageFxMath.pageTransform(PageEffect.CYLINDER, 0.4f, w, h, vertical = true)
        assertEquals(0f, slide.translationX, 0f)
        assertTrue("cylinder falls back to a vertical slide, ty=${slide.translationY}", kotlin.math.abs(slide.translationY + 0.4f * h) < 0.5f)
    }
}
