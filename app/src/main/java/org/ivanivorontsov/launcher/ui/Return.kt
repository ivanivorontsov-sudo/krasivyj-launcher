package org.ivanivorontsov.launcher.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.ivanivorontsov.launcher.core.ScreenLayout
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
    Canvas(Modifier.fillMaxSize()) {
        // Spring progress: may overshoot slightly past 1 (card dips under the icon size, then settles).
        val t = ui.returnProgress.value
        val e = t.coerceAtLeast(0f)
        val screen = Rect(Offset.Zero, size)
        fun onScreen(b: Rect?) = b != null && b.width > 0f && b.overlaps(screen) && screen.contains(b.center)
        val ids = listOfNotNull(r.key, layout.folderOf(r.key), "t_" + r.key)
        val target = ids.firstNotNullOfOrNull { ui.iconBounds[it]?.bounds()?.takeIf(::onScreen) } ?: r.fallback?.takeIf(::onScreen)
        val end = target ?: Rect(Offset(size.width / 2f - 30.dp.toPx(), size.height - 140.dp.toPx()), Size(60.dp.toPx(), 60.dp.toPx()))
        // The card begins slightly inset (like an app window that has started to shrink) — no full-screen pop.
        val startRect = Rect(size.width * 0.04f, size.height * 0.03f, size.width * 0.96f, size.height * 0.97f)
        val rect = Rect(
            lerp(startRect.topLeft, end.topLeft, e),
            Size(startRect.width + (end.width - startRect.width) * e, startRect.height + (end.height - startRect.height) * e),
        )
        val endCorner = when (spec.iconShape) {
            org.ivanivorontsov.launcher.core.IconShape.CIRCLE, org.ivanivorontsov.launcher.core.IconShape.PEBBLE, org.ivanivorontsov.launcher.core.IconShape.COOKIE -> end.minDimension / 2f
            org.ivanivorontsov.launcher.core.IconShape.SQUARE -> 0f
            org.ivanivorontsov.launcher.core.IconShape.ROUNDED -> end.minDimension * 0.22f
            else -> end.minDimension * 0.32f
        }
        val corner = (screenCorner + (endCorner - screenCorner) * e.coerceAtMost(1f)).coerceAtMost(rect.minDimension / 2f)
        val fade = if (target == null) (1f - e).coerceIn(0f, 1f) else 1f
        // Fade in during the first 10 %, hand over to the real icon after 60 %.
        val cardAlpha = (minOf(t / 0.1f, if (t < 0.6f) 1f else 1f - (t - 0.6f) / 0.35f)).coerceIn(0f, 1f) * fade
        drawRoundRect(base.copy(alpha = cardAlpha), rect.topLeft, rect.size, CornerRadius(corner))
        if (icon != null) {
            val side = minOf(rect.width, rect.height).coerceAtMost(end.width * (1f + 1.6f * (1f - e).coerceAtLeast(0f)))
            val a = ((t - 0.15f) / 0.5f).coerceIn(0f, 1f) * fade
            drawImage(icon, dstOffset = IntOffset((rect.center.x - side / 2).roundToInt(), (rect.center.y - side / 2).roundToInt()),
                dstSize = IntSize(side.roundToInt().coerceAtLeast(1), side.roundToInt().coerceAtLeast(1)), alpha = a)
        }
    }
}
