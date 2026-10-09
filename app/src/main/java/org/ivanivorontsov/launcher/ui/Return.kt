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
    Canvas(Modifier.fillMaxSize()) {
        val t = ui.returnProgress.value
        val e = FastOutSlowInEasing.transform(t.coerceIn(0f, 1f))
        val screen = Rect(Offset.Zero, size)
        fun onScreen(b: Rect?) = b != null && b.width > 0f && b.overlaps(screen) && screen.contains(b.center)
        val ids = listOfNotNull(r.key, layout.folderOf(r.key), "t_" + r.key)
        val target = ids.firstNotNullOfOrNull { ui.iconBounds[it]?.bounds()?.takeIf(::onScreen) } ?: r.fallback?.takeIf(::onScreen)
        val end = target
        if (end == null) return@Canvas
        val rect = Rect(lerp(screen.topLeft, end.topLeft, e), Size(size.width + (end.width - size.width) * e, size.height + (end.height - size.height) * e))
        val corner = 34.dp.toPx() + (end.minDimension * 0.3f - 34.dp.toPx()) * e
        val fade = if (target == null) 1f - e else 1f
        val cardAlpha = (if (t < 0.6f) 1f else 1f - (t - 0.6f) / 0.4f).coerceIn(0f, 1f) * fade
        drawRoundRect(base.copy(alpha = cardAlpha), rect.topLeft, rect.size, CornerRadius(corner))
        if (icon != null) {
            val side = minOf(rect.width, rect.height).coerceAtMost(end.width * (1f + 1.6f * (1f - e)))
            val a = ((t - 0.15f) / 0.5f).coerceIn(0f, 1f) * fade
            drawImage(icon, dstOffset = IntOffset((rect.center.x - side / 2).roundToInt(), (rect.center.y - side / 2).roundToInt()),
                dstSize = IntSize(side.roundToInt().coerceAtLeast(1), side.roundToInt().coerceAtLeast(1)), alpha = a)
        }
    }
}
