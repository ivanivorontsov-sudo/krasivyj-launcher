package org.ivanivorontsov.launcher.ui

import android.content.pm.ShortcutInfo
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.ivanivorontsov.launcher.LauncherApp
import org.ivanivorontsov.launcher.core.GestureAction
import org.ivanivorontsov.launcher.core.IconShape
import org.ivanivorontsov.launcher.core.Motion
import org.ivanivorontsov.launcher.core.TileItem
import org.ivanivorontsov.launcher.data.AppInfo
import org.ivanivorontsov.launcher.data.IconCache
import org.ivanivorontsov.launcher.service.NotificationCounter

/** Everything UI needs to ask the activity to do. */
interface LauncherActions {
    fun launch(app: AppInfo, bounds: Rect?, folderId: String? = null)
    fun launchShortcut(app: AppInfo, info: ShortcutInfo, bounds: Rect?)
    fun appInfo(app: AppInfo, bounds: Rect?)
    fun uninstall(app: AppInfo)
    fun openSettings(section: String? = null)
    fun addWidget()
    fun requestPhotos()
    fun requestCalendar()
    fun gesture(action: GestureAction)
    fun showMenu(app: AppInfo?, tile: TileItem?, anchor: Rect)
    fun homeMenu(anchor: Rect)
    fun pinSpecialTile(kind: org.ivanivorontsov.launcher.core.TileKind)
}

val LocalActions = staticCompositionLocalOf<LauncherActions> { error("no actions") }
/** Launcher UI state (null in the settings previews → no drag and drop there). */
val LocalUi = staticCompositionLocalOf<UiState?> { null }
val LocalAppsByKey = androidx.compose.runtime.compositionLocalOf<Map<String, AppInfo>> { emptyMap() }

/** Registers an icon's on-screen bounds under [id] (drop animation, return animation, gesture contract). */
@Composable
fun RegisterIcon(ui: UiState?, id: String, ref: BoundsRef, enabled: Boolean = true) {
    if (ui == null || !enabled) return
    androidx.compose.runtime.DisposableEffect(id, ref) {
        ui.iconBounds[id] = ref
        onDispose { if (ui.iconBounds[id] === ref) ui.iconBounds.remove(id) }
    }
}

/** Bounds of an icon drawn inside an open folder, used when that app is closed back into the folder. */
@Composable
fun RegisterFolderIcon(ui: UiState?, id: String, ref: BoundsRef) {
    if (ui == null) return
    androidx.compose.runtime.DisposableEffect(id, ref) {
        ui.folderIconBounds[id] = ref
        onDispose { if (ui.folderIconBounds[id] === ref) ui.folderIconBounds.remove(id) }
    }
}

/** Hidden while dragged (its slot is the placeholder) and while the return animation lands on it. */
fun Modifier.launcherItemVisibility(ui: UiState?, id: String): Modifier =
    if (ui == null) this else this.graphicsLayer {
        alpha = when {
            ui.drag.hides(id) -> 0f
            ui.returning?.key == id && ui.returnProgress.value < 0.97f -> 0f
            else -> 1f
        }
        val p = if (ui.popKey == id) ui.pop.value else 0f
        if (p != 0f) { val s = 1f + 0.12f * p; scaleX = s; scaleY = s }
    }

/** Remembers an element's position so app-open animations can start from the exact icon. */
@Stable
class BoundsRef { var coords: LayoutCoordinates? = null; fun bounds(): Rect? = coords?.takeIf { it.isAttached }?.boundsInWindow() }

fun Modifier.trackBounds(ref: BoundsRef) = this.onPlaced { ref.coords = it }

@Composable
fun rememberIcon(app: AppInfo, sizePx: Int, shape: IconShape, mono: Boolean = false): ImageBitmap? {
    val cache: IconCache = LauncherApp.instance.icons
    val req = remember(app.key, app.stamp, sizePx, shape, mono) { IconCache.Req(app, shape, sizePx, mono) }
    val img by produceState(initialValue = cache.peek(req), req, cache.iconPack) { if (value == null) value = cache.load(req) }
    return img
}

@Composable
fun AppIcon(app: AppInfo, size: Dp, modifier: Modifier = Modifier, shape: IconShape = LocalSpec.current.iconShape, mono: Boolean = false, pixelated: Boolean = false) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val img = rememberIcon(app, px, shape, mono)
    if (img != null) {
        Image(img, contentDescription = app.label, modifier = modifier.size(size), filterQuality = if (pixelated) FilterQuality.None else FilterQuality.Low)
    } else {
        Box(modifier.size(size).clip(PathShape(if (shape == IconShape.ORIGINAL) IconShape.CIRCLE else shape)).background(Color.White.copy(alpha = 0.12f)))
    }
}

/** Notification count for an app (0 if the optional listener is off). */
@Composable
fun badgeCount(pkg: String): Int {
    val counts by NotificationCounter.counts.collectAsStateCompat()
    return counts[pkg] ?: 0
}

@Composable
fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectAsStateCompat(): androidx.compose.runtime.State<T> = collectAsStateWithLifecycle()

@Composable
fun labelStyle(color: Color, size: TextUnit = LocalSpec.current.labelSizeSp.sp): TextStyle {
    val spec = LocalSpec.current
    val fonts = LocalFonts.current
    val shadow = when {
        spec.glow -> Shadow(color.copy(alpha = 0.9f), blurRadius = 14f)
        color.red + color.green + color.blue > 2.2f -> Shadow(Color.Black.copy(alpha = 0.45f), blurRadius = 6f)
        else -> null
    }
    return TextStyle(fontFamily = fonts.body, fontSize = size, color = color, shadow = shadow,
        letterSpacing = if (spec.letterSpacing != 0f) (spec.letterSpacing * 10).sp * 0.1f else TextUnit.Unspecified)
}

/** Grid cell: shaped icon + label, press feedback, long-press menu, long-press + move = drag. */
@Composable
fun AppCell(
    app: AppInfo, iconSize: Dp, showLabel: Boolean, modifier: Modifier = Modifier, labelColor: Color = LocalColors.current.onWallpaper,
    source: DragSource = DragSource.DRAWER,
    folderId: String? = null,
) {
    val spec = LocalSpec.current
    val actions = LocalActions.current
    val ui = LocalUi.current
    val ref = remember { BoundsRef() }
    val iconRef = remember { BoundsRef() }
    val badge = badgeCount(app.pkg)
    val iconPx = with(LocalDensity.current) { iconSize.toPx() }
    RegisterIcon(ui, app.key, iconRef, enabled = source == DragSource.HOME || source == DragSource.DOCK)
    if (source == DragSource.FOLDER) RegisterFolderIcon(ui, app.key, iconRef)
    Column(
        modifier
            .trackBounds(ref)
            .launcherItem(
                ui, ref,
                onClick = { actions.launch(app, iconRef.bounds() ?: ref.bounds(), folderId) },
                onLongPress = { actions.showMenu(app, null, ref.bounds() ?: Rect.Zero) },
                dragItem = { local ->
                    DragItem(app.key, app, source, size = androidx.compose.ui.geometry.Size(iconPx, iconPx),
                        grab = iconGrab(local, ref.bounds()?.width ?: iconPx, iconPx), startBounds = iconRef.bounds())
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.trackBounds(iconRef)) {
            AppIcon(app, iconSize, pixelated = spec.motion == Motion.PIXEL_STEP)
            if (badge > 0) Badge(badge, Modifier.align(Alignment.TopEnd).padding(0.dp))
        }
        if (showLabel) {
            Spacer(Modifier.height(5.dp))
            Text(
                spec.labelPrefix + spec.withCase(app.label), style = labelStyle(labelColor), maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center, modifier = Modifier.width(iconSize + 22.dp),
            )
        }
    }
}

@Composable
fun Badge(n: Int, modifier: Modifier = Modifier) {
    Box(modifier.size(18.dp).background(Color(0xFFFF3B30), CircleShape), contentAlignment = Alignment.Center) {
        Text(if (n > 99) "99" else n.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Press feedback that does not consume events (clicks/long-press still work):
 * SPRING/SMOOTH → darken + slight scale (springy), TURNSTILE → 3D tilt toward the finger, PIXEL → stepped scale.
 */
fun Modifier.pressFeedback(onClick: () -> Unit, onLongClick: (() -> Unit)?, tiltOverride: Boolean? = null): Modifier = composed {
    val spec = LocalSpec.current
    val anim = LocalAnimations.current
    val tiltMode = tiltOverride ?: (spec.motion == Motion.TURNSTILE || spec.iconTilt)
    val scale = remember { Animatable(1f) }
    val rx = remember { Animatable(0f) }
    val ry = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    this
        .graphicsLayer {
            scaleX = scale.value; scaleY = scale.value
            rotationX = rx.value; rotationY = ry.value
            cameraDistance = 14f * density
        }
        .pointerInput(tiltMode, anim) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val w = size.width.toFloat().coerceAtLeast(1f); val h = size.height.toFloat().coerceAtLeast(1f)
                scope.launch {
                    if (tiltMode) {
                        val nx = (down.position.x / w - 0.5f) * 2f; val ny = (down.position.y / h - 0.5f) * 2f
                        val center = kotlin.math.abs(nx) < 0.35f && kotlin.math.abs(ny) < 0.35f
                        launch { rx.animateTo(if (center) 0f else -ny * 9f, MotionSpecs.press(spec.motion, anim)) }
                        launch { ry.animateTo(if (center) 0f else nx * 9f, MotionSpecs.press(spec.motion, anim)) }
                        scale.animateTo(if (center) 0.96f else 0.985f, MotionSpecs.press(spec.motion, anim))
                    } else {
                        scale.animateTo(0.9f, MotionSpecs.press(spec.motion, anim))
                    }
                }
                waitForUpOrCancellation()
                scope.launch {
                    launch { rx.animateTo(0f, MotionSpecs.press(spec.motion, anim)) }
                    launch { ry.animateTo(0f, MotionSpecs.press(spec.motion, anim)) }
                    scale.animateTo(1f, MotionSpecs.press(spec.motion, anim))
                }
            }
        }
        .pointerInput(onClick, onLongClick) {
            detectTapGestures(
                onTap = { onClick() },
                onLongPress = onLongClick?.let { lc -> { _: androidx.compose.ui.geometry.Offset -> haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); lc() } },
            )
        }
}

/** Subtle “glass” card used by widgets/menus. */
fun Modifier.glass(shapeRadius: Dp, colors: LauncherColors, alpha: Float = 0.22f) =
    this.clip(androidx.compose.foundation.shape.RoundedCornerShape(shapeRadius))
        .background(if (colors.dark) Color.Black.copy(alpha = alpha + 0.08f) else Color.White.copy(alpha = alpha + 0.3f))

@Composable
fun rememberToggle(initial: Boolean = false) = remember { mutableStateOf(initial) }

@Composable
fun OnceEffect(key: Any?, block: suspend () -> Unit) = LaunchedEffect(key) { block() }
