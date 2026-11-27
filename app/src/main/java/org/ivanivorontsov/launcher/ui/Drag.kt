package org.ivanivorontsov.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.ivanivorontsov.launcher.LauncherApp
import org.ivanivorontsov.launcher.core.AppCategory
import org.ivanivorontsov.launcher.core.DropSpot
import org.ivanivorontsov.launcher.core.Motion
import org.ivanivorontsov.launcher.core.ScreenLayout
import org.ivanivorontsov.launcher.core.TileItem
import org.ivanivorontsov.launcher.core.TileKind
import org.ivanivorontsov.launcher.core.TileSize
import org.ivanivorontsov.launcher.core.isFolderId
import org.ivanivorontsov.launcher.data.AppInfo
import kotlin.math.abs

/*
 * Drag and drop, modelled on Launcher3's DragController/DropTarget split:
 *  - the item under the finger only *starts* a drag (long press, then move past touch slop);
 *  - from then on a single root-level pointer tracker owns the gesture (window coordinates), so the
 *    drag survives the source being disposed (drawer closing, page change, folder closing);
 *  - the active home screen registers a [DropHost] that maps the finger to a [DropSpot];
 *  - spots are debounced (reorder ≈ 90 ms, "make a folder" ≈ 260 ms dwell) and rendered as a live
 *    preview `layout.place(item, spot)` — the other icons glide to their new slots;
 *  - a drop bar (remove / app info / uninstall) is the Launcher3 "ButtonDropTarget".
 * Everything time-based, so it looks the same at 60, 90, 120 or 144 Hz.
 */

enum class DragSource { HOME, DOCK, FOLDER, DRAWER, TILE, FAV, LIST }

enum class BarTarget(val title: String) { REMOVE("Удалить с экрана"), INFO("О приложении"), UNINSTALL("Удалить приложение") }

@Immutable
class DragItem(
    /** App key, folder id, or tile id (for [DragSource.TILE]). */
    val id: String,
    val app: AppInfo?,
    val source: DragSource,
    val tile: TileItem? = null,
    /** Size of the floating copy and where the finger holds it (px). */
    val size: Size,
    val grab: Offset,
    val asLabel: Boolean = false,
    val startBounds: Rect? = null,
)

/** Implemented by the home screen that is currently visible. */
interface DropHost {
    /** Spot under [p] (window px), or null if the finger is not over a drop area of this screen. */
    fun resolve(p: Offset, item: DragItem): DropSpot?
    /** Last chance to adjust the layout before the drop is applied (e.g. materialise default favourites). */
    fun prepare(l: ScreenLayout): ScreenLayout = l
    /** Every frame while dragging: edge auto-scroll etc. */
    fun onFrame(p: Offset, nanos: Long) {}
    val maxDock: Int get() = 5
}

@Stable
class DragState {
    var item by mutableStateOf<DragItem?>(null); internal set
    var pointer by mutableStateOf(Offset.Zero); internal set
    /** Reorder spot (Home/Dock/Fav/Tile) shown as a live preview. */
    var spot by mutableStateOf<DropSpot?>(null); internal set
    /** Icon/folder the item would merge with (folder creation), highlighted without reflowing. */
    var into by mutableStateOf<String?>(null); internal set
    var bar by mutableStateOf<BarTarget?>(null); internal set
    /** True while the released item flies to its slot. */
    var dropping by mutableStateOf(false); internal set
    val lift = Animatable(0f)
    /** 0..1 fly-to-slot progress after release. */
    val fly = Animatable(0f)
    var flyFrom: Rect = Rect.Zero; internal set
    var flyTo: Rect? = null; internal set
    var host: DropHost? = null
    val barRects = HashMap<BarTarget, Rect>()
    internal var candidate: DropSpot? = null
    internal var candidateSince = 0L
    val active: Boolean get() = item != null && !dropping
    fun floatingRect(): Rect { val i = item ?: return Rect.Zero; return Rect(pointer - i.grab, i.size) }
    fun hides(id: String): Boolean { val i = item ?: return false; return i.id == id || (i.source == DragSource.LIST && i.app != null && id == "t_" + i.app.key) }
}

fun folderNameFor(a: AppInfo?, b: AppInfo?): String {
    val c = a?.category
    return if (c != null && c == b?.category && c != AppCategory.OTHER && c != AppCategory.SUGGESTED) c.title else "Папка"
}

/** Applies a drop to a layout (pure; null = not allowed). */
fun applyDrop(l: ScreenLayout, item: DragItem, spot: DropSpot, maxDock: Int): ScreenLayout? = when (spot) {
    is DropSpot.Tile -> {
        val existing = item.tile?.id ?: item.app?.let { a -> l.tiles.firstOrNull { it.appKey == a.key }?.id }
        if (existing != null && l.tiles.any { it.id == existing }) l.moveTile(existing, spot.index)
        else item.app?.let { l.pinTile(TileItem("t_" + it.key, TileKind.APP, it.key, TileSize.SMALL), spot.index) }
    }
    is DropSpot.Fav -> if (item.app == null || isFolderId(item.id)) null
        else if (item.id !in l.favorites && l.favorites.size >= 8) null else l.place(item.id, spot, maxDock)
    is DropSpot.Into -> {
        val repo = LauncherApp.instance.apps
        l.place(item.id, spot, maxDock, folderNameFor(item.app, repo.find(spot.target)))
    }
    else -> l.place(item.id, spot, maxDock)
}

/** Preview of the arrangement while dragging. */
fun DragState.preview(l: ScreenLayout): ScreenLayout {
    val i = item ?: return l; val s = spot ?: return l
    if (s is DropSpot.Into) return l
    if (dropping) return l
    val h = host
    return applyDrop(h?.prepare(l) ?: l, i, s, h?.maxDock ?: 5) ?: l
}

// ------------------------------------------------------------------ controller

fun UiState.startDrag(item: DragItem, windowPos: Offset) {
    if (drag.item != null) return
    menu = null; tileEdit = null
    drag.item = item; drag.pointer = windowPos; drag.spot = null; drag.into = null; drag.bar = null; drag.candidate = null; drag.dropping = false
    when (item.source) {
        DragSource.DRAWER -> drawer.close()
        DragSource.LIST -> showStartPage?.invoke()
        DragSource.FOLDER -> openFolder = null
        else -> {}
    }
    scope.launch { drag.lift.snapTo(0f); drag.lift.animateTo(1f, if (animations) spring(0.6f, 500f) else tween(0)) }
    scope.launch {
        while (drag.active) withFrameNanos { t ->
            try { dragFrame(t) } catch (_: Exception) { }
        }
    }
}

private fun UiState.dragFrame(t: Long) {
    val d = drag; val item = d.item ?: return; val p = d.pointer
    val bar = d.barRects.entries.firstOrNull { (k, r) -> r.inflate(6f).contains(p) && barAllowed(item, k) }?.key
    if (bar != d.bar) d.bar = bar
    if (bar != null) { d.candidate = null; return }
    val host = d.host
    val c = host?.resolve(p, item)
    if (c != d.candidate) { d.candidate = c; d.candidateSince = t }
    if (c != null && t - d.candidateSince >= (if (c is DropSpot.Into) 260_000_000L else 90_000_000L)) {
        if (c is DropSpot.Into) {
            if (d.into != c.target) {
                val layout = LauncherApp.instance.store.config.value.shownLayout(themeId)
                if (applyDrop(host.prepare(layout), item, c, host.maxDock) != null) { d.into = c.target; onFolderHover?.invoke() }
            }
        } else if (c != d.spot || d.into != null) {
            val layout = LauncherApp.instance.store.config.value.shownLayout(themeId)
            if (applyDrop(host.prepare(layout), item, c, host.maxDock) != null) { d.spot = c; d.into = null }
        }
    }
    host?.onFrame(p, t)
}

fun barAllowed(item: DragItem, t: BarTarget): Boolean = when (t) {
    BarTarget.REMOVE -> item.source != DragSource.DRAWER && item.source != DragSource.LIST
    BarTarget.INFO, BarTarget.UNINSTALL -> item.app != null
}

fun UiState.cancelDrag() {
    drag.item = null; drag.spot = null; drag.into = null; drag.bar = null; drag.dropping = false; drag.candidate = null
}

fun UiState.endDrag(haptic: (() -> Unit)? = null) {
    val d = drag; val item = d.item ?: return
    if (d.dropping) return
    val store = LauncherApp.instance.store
    val bar = d.bar
    if (bar != null) {
        haptic?.invoke()
        val app = item.app
        when (bar) {
            BarTarget.REMOVE -> store.update { c ->
                c.editLayout(themeId) { l -> if (item.source == DragSource.TILE) l.unpinTile(item.id) else l.remove(item.id) }
            }
            BarTarget.INFO -> app?.let { actions?.appInfo(it, item.startBounds) }
            BarTarget.UNINSTALL -> app?.let { actions?.uninstall(it) }
        }
        cancelDrag(); return
    }
    val spot = d.into?.let { DropSpot.Into(it) } ?: d.spot
    var landing: String? = null
    if (spot != null) {
        val host = d.host
        var applied = false
        store.update { c ->
            c.editLayout(themeId) { l ->
                val r = applyDrop(host?.prepare(l) ?: l, item, spot, host?.maxDock ?: 5)
                if (r != null) {
                    applied = true
                    landing = when {
                        spot is DropSpot.Into -> r.folderOf(item.id) ?: spot.target
                        spot is DropSpot.Tile -> item.tile?.id ?: r.tiles.firstOrNull { it.appKey == item.app?.key }?.id
                        else -> item.id
                    }
                }
                r
            }
        }
        if (applied) haptic?.invoke()
    }
    if (item.source == DragSource.TILE || item.asLabel) { cancelDrag(); return }
    // Fly the floating icon into its slot (or back where it came from), then reveal the real icon.
    // Always clear the overlay: a stalled frame (concave icon shadow) used to leave it floating.
    d.dropping = true
    d.spot = null; d.into = null
    d.flyFrom = d.floatingRect()
    val token = item
    scope.launch {
        try {
            withTimeoutOrNull(700) {
                withFrameNanos { }
                val target = (landing ?: item.id.takeIf { item.source != DragSource.DRAWER })?.let { iconBounds[it]?.bounds() }
                    ?: item.startBounds?.takeIf { item.source != DragSource.DRAWER && landing == null }
                d.flyTo = target
                d.fly.snapTo(0f)
                if (animations) d.fly.animateTo(1f, tween(if (target != null) 230 else 160, easing = FastOutSlowInEasing))
            }
        } finally {
            if (d.item === token) {
                cancelDrag()
                d.flyTo = null
            }
        }
    }
}

/** Root-level tracker: once a drag started it consumes the pointer stream in the Initial pass. */
fun Modifier.dragTracker(ui: UiState): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                if (!ui.drag.active) continue
                val ch = e.changes.firstOrNull() ?: continue
                val prevBar = ui.drag.bar
                ui.drag.pointer = ch.position
                e.changes.forEach { it.consume() }
                if (e.changes.none { it.pressed }) ui.endDrag { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
                else if (prevBar == null && ui.drag.bar != null) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }
}

// ------------------------------------------------------------------ item gesture

/**
 * Tap / long press (menu) / long press + move (drag) on one launcher item, with the theme's press
 * feedback. Pass [dragItem] = null for items that can't be dragged.
 */
fun Modifier.launcherItem(
    ui: UiState?,
    ref: BoundsRef,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)?,
    dragItem: ((local: Offset) -> DragItem?)? = null,
    tiltOverride: Boolean? = null,
): Modifier = composed {
    val spec = LocalSpec.current
    val anim = LocalAnimations.current
    val tiltMode = tiltOverride ?: (spec.motion == Motion.TURNSTILE || spec.iconTilt)
    val scale = remember { Animatable(1f) }
    val rx = remember { Animatable(0f) }
    val ry = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    // Callbacks change on every recomposition; the gesture loop must not restart mid-press.
    val clickState = androidx.compose.runtime.rememberUpdatedState(onClick)
    val longState = androidx.compose.runtime.rememberUpdatedState(onLongPress)
    val dragState = androidx.compose.runtime.rememberUpdatedState(dragItem)
    this
        .graphicsLayer {
            scaleX = scale.value; scaleY = scale.value
            rotationX = rx.value; rotationY = ry.value
            cameraDistance = 14f * density
        }
        .pointerInput(tiltMode, anim, ui) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                // Like detectTapGestures: claim the down so the home screen's own long-press (home menu)
                // and double-tap don't fire too. Scrolling containers still work (they ignore consumed downs).
                down.consume()
                val w = size.width.toFloat().coerceAtLeast(1f); val h = size.height.toFloat().coerceAtLeast(1f)
                fun release() = scope.launch {
                    launch { rx.animateTo(0f, MotionSpecs.press(spec.motion, anim)) }
                    launch { ry.animateTo(0f, MotionSpecs.press(spec.motion, anim)) }
                    scale.animateTo(1f, MotionSpecs.press(spec.motion, anim))
                }
                scope.launch {
                    if (tiltMode) {
                        val nx = (down.position.x / w - 0.5f) * 2f; val ny = (down.position.y / h - 0.5f) * 2f
                        val center = abs(nx) < 0.35f && abs(ny) < 0.35f
                        launch { rx.animateTo(if (center) 0f else -ny * 9f, MotionSpecs.press(spec.motion, anim)) }
                        launch { ry.animateTo(if (center) 0f else nx * 9f, MotionSpecs.press(spec.motion, anim)) }
                        scale.animateTo(if (center) 0.96f else 0.985f, MotionSpecs.press(spec.motion, anim))
                    } else scale.animateTo(0.9f, MotionSpecs.press(spec.motion, anim))
                }
                val slop = viewConfiguration.touchSlop
                var released = false
                var cancelled = false
                val timedOut = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val ev = awaitPointerEvent()
                        val c = ev.changes.firstOrNull { it.id == down.id }
                        if (c == null || c.isConsumed) { cancelled = true; break }
                        if (!c.pressed) { released = true; c.consume(); break }
                        if ((c.position - down.position).getDistance() > slop) { cancelled = true; break }
                    }
                } == null
                if (!timedOut) {
                    release()
                    if (released && !cancelled) clickState.value()
                    return@awaitEachGesture
                }
                val onLongPress = longState.value
                val dragItem = dragState.value
                if (onLongPress == null && dragItem == null) { release(); return@awaitEachGesture }
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                scope.launch { launch { rx.animateTo(0f) }; launch { ry.animateTo(0f) }; scale.animateTo(1.06f, spring(0.55f, Spring.StiffnessMedium)) }
                onLongPress?.invoke()
                while (true) {
                    val ev = awaitPointerEvent()
                    val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (!c.pressed) { c.consume(); break }
                    if (ui != null && dragItem != null && (c.position - down.position).getDistance() > slop * 1.5f) {
                        val item = dragItem(c.position)
                        val origin = ref.bounds()
                        if (item != null && origin != null) {
                            c.consume()
                            scope.launch { scale.snapTo(1f) }
                            ui.startDrag(item, origin.topLeft + c.position)
                            break
                        }
                    }
                    c.consume()
                }
                release()
            }
        }
}

/** Grab point clamped into an icon square of [iconPx] centred horizontally in a cell of [cellW]. */
fun iconGrab(local: Offset, cellW: Float, iconPx: Float, iconTop: Float = 0f): Offset =
    Offset((local.x - (cellW - iconPx) / 2f).coerceIn(0f, iconPx), (local.y - iconTop).coerceIn(0f, iconPx))

// ------------------------------------------------------------------ smooth reflow

/** Animates a child to its new place whenever its layout position changes (layout phase only → cheap). */
fun Modifier.animatePlacement(enabled: Boolean = true): Modifier = composed {
    if (!enabled) return@composed this
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf<IntOffset?>(null) }
    val anim = remember { mutableStateOf<Animatable<IntOffset, AnimationVector2D>?>(null) }
    this
        .onPlaced { target = it.positionInParent().round() }
        .graphicsLayer {
            val t = target ?: return@graphicsLayer
            val a = anim.value ?: Animatable(t, IntOffset.VectorConverter).also { anim.value = it }
            if (a.targetValue != t) scope.launch { a.animateTo(t, spring(dampingRatio = 0.82f, stiffness = 520f)) }
            val off = a.value - t
            translationX = off.x.toFloat(); translationY = off.y.toFloat()
        }
}

// ------------------------------------------------------------------ overlays

/** Floating copy under the finger + drop bar. */
@Composable
fun DragLayer(ui: UiState) {
    val d = ui.drag
    val item = d.item ?: return
    val colors = LocalColors.current; val spec = LocalSpec.current
    val density = LocalDensity.current
    if (!d.dropping) DropBar(ui, item)
    if (item.source == DragSource.TILE) return
    val sizeDp = with(density) { item.size.width.toDp() }
    Box(
        Modifier
            .graphicsLayer {
                val from = if (d.dropping) d.flyFrom else d.floatingRect()
                val to = d.flyTo
                val f = d.fly.value
                val r = if (d.dropping && to != null) Rect(lerp(from.topLeft, to.topLeft, f), Size(from.width + (to.width - from.width) * f, from.height + (to.height - from.height) * f)) else from
                translationX = r.left; translationY = r.top
                val lifted = 1f + 0.12f * d.lift.value.coerceIn(0f, 1.2f)
                val s = if (d.dropping) (if (to != null) lifted + (1f - lifted) * f else lifted * (1f - 0.4f * f)) * (r.width / from.width.coerceAtLeast(1f)) else lifted
                scaleX = s; scaleY = s
                alpha = if (d.dropping && to == null) 1f - f else 1f
                shadowElevation = (if (d.dropping) 1f - f else 1f) * 14.dp.toPx()
                // A concave outline (cookie, pebble) makes the elevation renderer stall on
                // every frame, so the drop animation never finishes and the icon stays floating.
                // The bitmap is already masked; the shadow only needs a convex shape.
                shape = if (item.asLabel) RoundedCornerShape(12.dp) else CircleShape
                clip = false
            }
    ) {
        when {
            item.asLabel && item.app != null -> Box(Modifier.background(colors.surface.copy(alpha = 0.92f), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text(spec.withCase(item.app.label), style = labelStyle(colors.onSurface, spec.labelSizeSp.sp).copy(shadow = null), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            isFolderId(item.id) -> {
                val cfg = LauncherApp.instance.store.config.value
                val f = cfg.shownLayout(ui.themeId).folders[item.id]
                if (f != null) FolderIcon(f, sizeDp)
            }
            item.app != null -> AppIcon(item.app, sizeDp)
        }
    }
}

@Composable
private fun DropBar(ui: UiState, item: DragItem) {
    val d = ui.drag
    val colors = LocalColors.current
    val targets = BarTarget.entries.filter { barAllowed(item, it) }
    val appear = remember { Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) { appear.animateTo(1f, tween(if (ui.animations) 160 else 0)) }
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp)
            .graphicsLayer { alpha = appear.value; translationY = (1f - appear.value) * -40.dp.toPx() },
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        targets.forEach { t ->
            val hot = d.bar == t
            val danger = t == BarTarget.UNINSTALL
            Box(
                Modifier.weight(1f)
                    .onPlaced { c -> d.barRects[t] = Rect(c.localToWindow(Offset.Zero), Size(c.size.width.toFloat(), c.size.height.toFloat())) }
                    .graphicsLayer { val s = if (hot) 1.06f else 1f; scaleX = s; scaleY = s }
                    .clip(RoundedCornerShape(14.dp))
                    .background(when {
                        hot && danger -> Color(0xFFE5484D)
                        hot -> colors.accent
                        else -> Color.Black.copy(alpha = 0.45f)
                    })
                    .padding(vertical = 12.dp, horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(t.title, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = labelStyle(Color.White, 13.sp).copy(shadow = null))
            }
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { d.barRects.clear() } }
}

/** Folder-creation highlight: the target icon swells and gets a soft disc behind it (animated). */
fun Modifier.folderTarget(ui: UiState, id: String): Modifier = composed {
    val active = ui.drag.into == id
    val t by androidx.compose.animation.core.animateFloatAsState(if (active) 1f else 0f, if (ui.animations) spring(0.6f, 600f) else tween(0), label = "folderTarget")
    val colors = LocalColors.current
    this.drawBehind {
        if (t > 0.01f) drawCircle(colors.onWallpaper.copy(alpha = 0.28f * t), radius = size.minDimension * (0.5f + 0.18f * t))
    }.graphicsLayer { val s = 1f + 0.14f * t; scaleX = s; scaleY = s }
}
