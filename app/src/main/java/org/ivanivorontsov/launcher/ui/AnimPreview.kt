package org.ivanivorontsov.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.ivanivorontsov.launcher.core.BackAnim
import org.ivanivorontsov.launcher.core.HomeAnim
import org.ivanivorontsov.launcher.core.ReturnStyle
import org.ivanivorontsov.launcher.core.UnlockAnim
import org.ivanivorontsov.launcher.core.resolveReturn
import org.ivanivorontsov.launcher.core.stiffnessFor

private val previewIcons = listOf(0xFFE57373, 0xFF64B5F6, 0xFF81C784, 0xFFFFB74D, 0xFFBA68C8, 0xFF4DD0E1, 0xFFF06292, 0xFFAED581, 0xFF9575CD, 0xFFFF8A65, 0xFF4FC3F7, 0xFFDCE775)

/** 1.3.6: looping preview of the return-into-icon animation, same drawing code as the real overlay. */
@Composable
fun ReturnPreview(anim: HomeAnim, durationMs: Int, modifier: Modifier = Modifier) {
    val style = resolveReturn(anim, BackAnim.SAME, fromBack = false)
    val icon = remember {
        androidx.compose.ui.graphics.ImageBitmap(96, 96).also { b ->
            val c = androidx.compose.ui.graphics.Canvas(b); val paint = androidx.compose.ui.graphics.Paint()
            paint.color = Color(previewIcons[8]); c.drawRoundRect(0f, 0f, 96f, 96f, 28f, 28f, paint)
            paint.color = Color.White; c.drawCircle(Offset(48f, 30f), 14f, paint); c.drawRect(38f, 50f, 58f, 80f, paint)
        }
    }
    val t = remember { Animatable(0f) }; val tail = remember { Animatable(0f) }
    LaunchedEffect(style, durationMs) {
        while (true) {
            t.snapTo(0f); tail.snapTo(0f); delay(350)
            when {
                !style.card -> t.snapTo(1f)
                style == ReturnStyle.SPRING -> t.animateTo(1f, spring(0.72f, stiffnessFor(durationMs, 0.72f), 0.01f))
                else -> t.animateTo(1f, tween(durationMs, easing = MotionSpecs.fastOut))
            }
            if (style == ReturnStyle.RINGS || style == ReturnStyle.DROP) tail.animateTo(1f, tween((durationMs * 1.2f).toInt().coerceIn(220, 700), easing = LinearOutSlowInEasing))
            delay(900)
        }
    }
    Canvas(modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF263238))) {
        val cell = size.width / 6f; val ic = cell * 0.62f
        for (i in 0 until 12) {
            val c = Offset(cell * (i % 6 + 0.5f), size.height * 0.30f + (i / 6) * cell)
            if (i == 8 && style.card && t.value < 0.97f) continue
            drawRoundRect(Color(previewIcons[i]), Offset(c.x - ic / 2, c.y - ic / 2), Size(ic, ic), CornerRadius(ic * 0.3f))
        }
        val c = Offset(cell * (8 % 6 + 0.5f), size.height * 0.30f + cell)
        val end = Rect(c.x - ic / 2, c.y - ic / 2, c.x + ic / 2, c.y + ic / 2)
        if (style.card && t.value < 1f || tail.value in 0.001f..0.999f)
            drawReturnFrame(style, t.value, tail.value, end, Color(previewIcons[8]), icon, 18.dp.toPx(), ic * 0.3f)
    }
}

/** 1.3.6: looping preview of the unlock animation on a mock grid. */
@Composable
fun UnlockPreview(anim: UnlockAnim, modifier: Modifier = Modifier) {
    val e = remember { Animatable(1f) }
    LaunchedEffect(anim) {
        while (true) {
            e.snapTo(if (anim == UnlockAnim.NONE) 1f else 0f); delay(250)
            when (anim) {
                UnlockAnim.NONE -> {}
                UnlockAnim.SPRING -> e.animateTo(1f, spring(0.6f, 380f, 0.001f))
                UnlockAnim.WAVE, UnlockAnim.STAGGER -> e.animateTo(1f, tween(520, easing = LinearEasing))
                else -> e.animateTo(1f, tween(300, easing = MotionSpecs.fastOut))
            }
            delay(1100)
        }
    }
    Canvas(modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF1B2A35))) {
        val v = e.value; val ec = v.coerceIn(0f, 1f)
        val cell = size.width / 6f; val ic = cell * 0.62f
        val ctr = Offset(size.width / 2, size.height / 2)
        val pts = (0 until 18).map { Offset(cell * (it % 6 + 0.5f), size.height * 0.2f + (it / 6) * cell * 0.95f) }
        val maxD = pts.maxOf { (it - ctr).getDistance() }
        pts.forEachIndexed { i, p ->
            var a = ec; var s = 1f; var dy = 0f
            when (anim) {
                UnlockAnim.FADE -> s = 0.97f + 0.03f * ec
                UnlockAnim.RISE -> dy = size.height * 0.14f * (1 - ec)
                UnlockAnim.SPRING -> { s = 0.82f + 0.18f * v; a = (ec * 2.5f).coerceAtMost(1f) }
                UnlockAnim.WAVE -> { val l = unlockLocal(ec, (p - ctr).getDistance() / maxD); a = l; s = 0.55f + 0.45f * l }
                UnlockAnim.STAGGER -> { val l = unlockLocal(ec, i / 17f); a = l; dy = (1 - l) * 28.dp.toPx() }
                UnlockAnim.NONE -> a = 1f
            }
            val w = ic * s
            drawRoundRect(Color(previewIcons[i % 12]).copy(alpha = a.coerceIn(0f, 1f)), Offset(p.x - w / 2, p.y - w / 2 + dy), Size(w, w), CornerRadius(w * 0.3f))
        }
    }
}
