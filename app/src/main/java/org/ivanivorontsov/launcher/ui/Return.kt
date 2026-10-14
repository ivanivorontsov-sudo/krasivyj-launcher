package org.ivanivorontsov.launcher.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import org.ivanivorontsov.launcher.core.IconShape
import org.ivanivorontsov.launcher.core.ReturnStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.ivanivorontsov.launcher.core.ReturnSpot
import org.ivanivorontsov.launcher.core.ScreenLayout
import org.ivanivorontsov.launcher.core.chooseReturnSpot
import kotlin.math.roundToInt

/**
 * In-launcher "app folds back into its icon": a card the colour of the app's icon starts as the whole
 * screen and shrinks into the icon's current position while the icon fades in inside it. A third-party
 * launcher cannot animate another app's window (that needs the signature-level
 * CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS permission), so this is drawn in our own window right after
 * the system's closing transition — on top of the home screen, underneath the leaving app.
 */
@Composable
fun ReturnOverlay(ui: UiState, layout: ScreenLayout) {
    val r = ui.returning ?: return
    if (!ui.returnStyle.card) return
    val app = LocalAppsByKey.current[r.key]
    val spec = LocalSpec.current; val colors = LocalColors.current
    val density = LocalDensity.current
    val iconPx = with(density) { spec.iconSizeDp.dp.roundToPx() }
    val icon = app?.let { rememberIcon(it, iconPx, spec.iconShape) }
    val base = r.color?.let { Color(it) } ?: colors.surface
    val view = androidx.compose.ui.platform.LocalView.current
    // Start radius = the display's own rounded corner (Android 12+), so the card looks like the app window.
    val screenCorner = androidx.compose.runtime.remember(view) {
        if (android.os.Build.VERSION.SDK_INT >= 31) view.rootWindowInsets?.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat() else null
    } ?: with(density) { 28.dp.toPx() }
    val endCache = androidx.compose.runtime.remember(r) { arrayOfNulls<Rect>(1) }
    // Own hardware layer: per-frame redraws of the card do not invalidate the home grid underneath.
    Canvas(Modifier.fillMaxSize().graphicsLayer()) {
        // Spring progress: may overshoot slightly past 1 (card dips under the icon size, then settles).
        val t = ui.returnProgress.value
        val screen = Rect(Offset.Zero, size)
        fun onScreen(b: Rect?) = b != null && b.width > 0f && b.overlaps(screen) && screen.contains(b.center)
        val cached = endCache[0]
        val end = cached ?: run {
        val folderId = r.folderId
        val inner = folderId?.let { ui.folderIconBounds[r.key]?.bounds()?.takeIf(::onScreen) }
        val launch = r.fallback?.takeIf(::onScreen)
        val gridFolder = folderId?.let { ui.iconBounds[it]?.bounds()?.takeIf(::onScreen) }
        val home = if (folderId == null) {
            listOfNotNull(r.key, layout.folderOf(r.key), "t_" + r.key)
                .firstNotNullOfOrNull { ui.iconBounds[it]?.bounds()?.takeIf(::onScreen) }
        } else null
        when (chooseReturnSpot(
            launchedFromFolder = folderId != null,
            folderOpen = folderId != null && ui.openFolder == folderId,
            hasInner = inner != null,
            hasLaunchBounds = launch != null,
            hasGridFolder = gridFolder != null,
            hasHomeIcon = home != null,
        )) {
            ReturnSpot.INNER -> inner
            ReturnSpot.LAUNCH_BOUNDS -> launch
            ReturnSpot.GRID_FOLDER -> gridFolder
            ReturnSpot.HOME_ICON -> home
            null -> null
        } }?.also { endCache[0] = it } ?: return@Canvas
        drawReturnFrame(ui.returnStyle, t, ui.returnTail.value, end, base, icon, screenCorner, endCornerFor(spec.iconShape, end))
    }
}

fun endCornerFor(shape: IconShape, end: Rect): Float = when (shape) {
    IconShape.CIRCLE, IconShape.PEBBLE, IconShape.COOKIE -> end.minDimension / 2f
    IconShape.SQUARE -> 0f
    IconShape.ROUNDED -> end.minDimension * 0.22f
    else -> end.minDimension * 0.32f
}

/**
 * One frame of the "fold into icon" animation. Pure drawing, no state reads: used by the overlay
 * and by the settings preview. [t] = card progress 0..1 (springs may overshoot), [tail] = 0..1 after landing.
 */
fun DrawScope.drawReturnFrame(style: ReturnStyle, t: Float, tail: Float, end: Rect, base: Color, icon: ImageBitmap?, screenCorner: Float, endCorner: Float) {
    val e = t.coerceAtLeast(0f)
    val startRect = Rect(size.width * 0.02f, size.height * 0.015f, size.width * 0.98f, size.height * 0.985f)
    // Comet: the card travels along a curve (overshoots sideways) instead of a straight line.
    val bend = if (style == ReturnStyle.COMET) kotlin.math.sin(e.coerceIn(0f, 1f) * Math.PI).toFloat() * size.width * 0.12f else 0f
    fun rectAt(k: Float): Rect {
        val tl = lerp(startRect.topLeft, end.topLeft, k)
        return Rect(Offset(tl.x + (if (style == ReturnStyle.COMET) kotlin.math.sin(k.coerceIn(0f, 1f) * Math.PI).toFloat() * size.width * 0.12f else 0f), tl.y),
            Size(startRect.width + (end.width - startRect.width) * k, startRect.height + (end.height - startRect.height) * k))
    }
    val rect = rectAt(e)
    // Drop: corners round off to a circle quickly, so the window becomes a falling drop.
    val cornerK = if (style == ReturnStyle.DROP) (e * 1.8f).coerceAtMost(1f) else e.coerceAtMost(1f)
    val target = if (style == ReturnStyle.DROP) rect.minDimension / 2f else endCorner
    val corner = (screenCorner + (target - screenCorner) * cornerK).coerceAtMost(rect.minDimension / 2f)
    val cardAlpha = (minOf(t / 0.1f, if (t < 0.6f) 1f else 1f - (t - 0.6f) / 0.35f)).coerceIn(0f, 1f)
    if (style == ReturnStyle.COMET && bend != 0f) {
        // Trail: three fading ghosts behind the card.
        for (i in 1..3) {
            val g = rectAt((e - i * 0.07f).coerceAtLeast(0f))
            drawRoundRect(base.copy(alpha = cardAlpha * (0.28f - i * 0.07f)), g.topLeft, g.size, CornerRadius(corner))
        }
    }
    drawRoundRect(base.copy(alpha = cardAlpha), rect.topLeft, rect.size, CornerRadius(corner))
    if (icon != null) {
        val side = minOf(rect.width, rect.height).coerceAtMost(end.width * (1f + 1.6f * (1f - e).coerceAtLeast(0f)))
        val a = ((t - 0.15f) / 0.5f).coerceIn(0f, 1f)
        val c = rect.center
        val deg = when (style) {
            // Spin: one full turn around its own axis, finishing exactly at landing.
            ReturnStyle.SPIN -> 360f * (1f - e.coerceIn(0f, 1f)).let { it * it }
            else -> 0f
        }
        // Drop: squash on impact and bounce back during the tail.
        val sq = if (style == ReturnStyle.DROP && tail > 0f) kotlin.math.sin(tail * Math.PI).toFloat() * 0.14f else 0f
        rotate(deg, c) {
            scale(1f + sq, 1f - sq, c) {
                drawImage(icon, dstOffset = IntOffset((c.x - side / 2).roundToInt(), (c.y - side / 2).roundToInt()),
                    dstSize = IntSize(side.roundToInt().coerceAtLeast(1), side.roundToInt().coerceAtLeast(1)), alpha = a)
            }
        }
    }
    if (style == ReturnStyle.RINGS) {
        // Ripples: start just before landing and keep spreading during the tail.
        val phase = ((e - 0.7f) / 0.3f).coerceIn(0f, 1f) * 0.35f + tail * 0.65f
        if (phase > 0f) for (i in 0 until 3) {
            val p = (phase * 1.25f - i * 0.18f).coerceIn(0f, 1f)
            if (p <= 0f || p >= 1f) continue
            val radius = end.minDimension * (0.55f + 1.4f * p)
            drawCircle(base.copy(alpha = (1f - p) * 0.75f), radius, end.center, style = Stroke(width = end.minDimension * 0.06f * (1f - p) + 1.5f))
        }
    }
}

/**
 * 1.3.6: invisible warm-up. Draws every return style once at near-zero alpha in a tiny box for a few frames
 * after start, so the first real "fold into icon" does not pay for JIT / shader compilation (the first return
 * after opening the launcher was the worst one).
 */
@Composable
fun ReturnWarmUp() {
    val fs = androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    if (fs.intValue > 12) return
    val styles = androidx.compose.runtime.remember { ReturnStyle.entries.filter { it.card } }
    val icon = androidx.compose.runtime.remember { ImageBitmap(48, 48) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (fs.intValue <= 12) androidx.compose.runtime.withFrameNanos { fs.intValue++ }
    }
    Canvas(Modifier.size(4.dp).graphicsLayer { alpha = 0.01f }) {
        val f = fs.intValue
        val end = Rect(0f, 0f, size.width / 2, size.height / 2)
        for (s in styles) drawReturnFrame(s, (f % 10) / 10f, (f % 5) / 5f, end, Color.Gray, icon, 2f, 1f)
    }
}
