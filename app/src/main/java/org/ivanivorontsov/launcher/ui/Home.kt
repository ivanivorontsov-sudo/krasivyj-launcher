package org.ivanivorontsov.launcher.ui

import org.ivanivorontsov.launcher.core.veiled
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ivanivorontsov.launcher.LauncherApp
import org.ivanivorontsov.launcher.core.ClockStyle
import org.ivanivorontsov.launcher.core.DockStyle
import org.ivanivorontsov.launcher.core.GestureAction
import org.ivanivorontsov.launcher.core.HomeLayout
import org.ivanivorontsov.launcher.core.LauncherConfig
import org.ivanivorontsov.launcher.core.DropSpot
import org.ivanivorontsov.launcher.core.ScreenLayout
import org.ivanivorontsov.launcher.core.isFolderId
import org.ivanivorontsov.launcher.core.Motion
import org.ivanivorontsov.launcher.core.Overlay
import org.ivanivorontsov.launcher.core.PageIndicator
import org.ivanivorontsov.launcher.core.ThemeSpec
import org.ivanivorontsov.launcher.core.TileFill
import org.ivanivorontsov.launcher.core.TileItem
import org.ivanivorontsov.launcher.core.TileKind
import org.ivanivorontsov.launcher.data.AppInfo
import kotlin.math.abs
import kotlin.math.roundToInt

@Immutable
class WallImages(val full: ImageBitmap, val frosted: ImageBitmap, val tall: Boolean, val raw: Bitmap)

/** Renders (off the main thread) the theme's code-drawn wallpaper or the user's tile photo. */
/** Process-wide cache: the rendered wallpaper survives activity recreation; a JPEG copy survives process death. */
private object WallCache {
    @Volatile var key: String? = null
    @Volatile var images: WallImages? = null
}

@Composable
fun rememberWallpaper(spec: ThemeSpec, wPx: Int, hPx: Int, tilePhoto: String?): WallImages? {
    val ctx = LocalContext.current
    val tall = spec.layout == HomeLayout.TILES && spec.tileFill == TileFill.TRANSPARENT
    val needs = Wallpapers.hasGenerated(spec) || tall
    val key = "${spec.wallpaper}|${spec.wallpaperColors}|${spec.wallpaperDim}|${spec.id}|$wPx|$hPx|$tall|$tilePhoto|v2"
    val cached = if (WallCache.key == key) WallCache.images else null
    val img by produceState(cached, key, needs) {
        if (!needs || wPx <= 0 || hPx <= 0) { value = null; return@produceState }
        if (value != null) return@produceState
        value = withContext(Dispatchers.Default) {
            val h = if (tall) (hPx * 1.45f).toInt() else hPx
            val file = java.io.File(ctx.cacheDir, "wall_" + Integer.toHexString(key.hashCode()) + ".jpg")
            val fromDisk = try { if (file.exists()) BitmapFactory.decodeFile(file.path)?.takeIf { it.width == wPx && it.height == h } else null } catch (_: Throwable) { null }
            val bmp: Bitmap = fromDisk ?: ((if (tall && tilePhoto != null) loadPhoto(ctx, Uri.parse(tilePhoto), wPx, h) else null) ?: Wallpapers.render(spec, wPx, h)).also { b ->
                try {
                    ctx.cacheDir.listFiles { _, n -> n.startsWith("wall_") }?.forEach { it.delete() }
                    file.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 94, it) }
                } catch (_: Throwable) {}
            }
            WallImages(bmp.asImageBitmap(), Wallpapers.frosted(bmp).asImageBitmap(), tall, bmp).also { WallCache.key = key; WallCache.images = it }
        }
    }
    return img
}

private fun loadPhoto(ctx: android.content.Context, uri: Uri, w: Int, h: Int): Bitmap? = try {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
    var s = 1; while (o.outWidth / (s * 2) >= w && o.outHeight / (s * 2) >= h) s *= 2
    val src = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) }
    src?.let {
        val scale = maxOf(w / it.width.toFloat(), h / it.height.toFloat())
        val sw = (w / scale).toInt().coerceAtMost(it.width); val sh = (h / scale).toInt().coerceAtMost(it.height)
        val crop = Bitmap.createBitmap(it, (it.width - sw) / 2, (it.height - sh) / 2, sw, sh)
        Bitmap.createScaledBitmap(crop, w, h, true)
    }
} catch (_: Exception) { null }

@Composable
fun LauncherRoot(ui: UiState) {
    val app = LauncherApp.instance
    val spec by app.store.theme.collectAsStateCompat()
    val config by app.store.config.collectAsStateCompat()
    val allApps by app.apps.apps.collectAsStateCompat()
    // «Стоп-экран»: forbidden apps simply do not exist for the launcher (home, dock, folders, drawer, search, tiles).
    val stop by org.ivanivorontsov.launcher.stop.StopGuard.view.collectAsStateCompat()
    val apps = remember(allApps, stop.hidden) { if (stop.hidden.isEmpty()) allApps else allApps.filter { it.pkg !in stop.hidden } }
    val animations = ui.animations
    ui.drawer.animationsEnabled = animations
    ui.themeId = spec.id
    val byKey = remember(apps) { apps.associateBy { it.key } }
    val visibleApps = remember(apps, config.hidden) { apps.filter { it.key !in config.hidden } }
    // Keys of forbidden apps: veiled in the layout (no gaps), edits keep their places for later.
    val veil = remember(allApps, stop.hidden) { if (stop.hidden.isEmpty()) emptySet() else allApps.filter { it.pkg in stop.hidden }.map { it.key }.toSet() }
    org.ivanivorontsov.launcher.core.LayoutVeil.keys = veil
    val layout = remember(config, spec.id, veil) { config.layoutFor(spec.id).let { if (veil.isEmpty()) it else it.veiled(veil) } }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    ui.onFolderHover = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove) }

    LauncherTheme(spec, animations) {
      CompositionLocalProvider(LocalUi provides ui, LocalAppsByKey provides byKey, LocalPageEffect provides config.pageEffectFor(spec.id, spec.layout)) {
        val density = LocalDensity.current
        BoxWithConstraints(Modifier.fillMaxSize().dragTracker(ui)) {
            val wPx = with(density) { maxWidth.roundToPx() }; val hPx = with(density) { maxHeight.roundToPx() }
            val wall = rememberWallpaper(spec, wPx, hPx, config.tileBackgroundUri)
            val colors = LocalColors.current

            // Background: code-drawn wallpaper, or the phone wallpaper shows through the transparent window.
            if (wall != null && !wall.tall) Image(wall.full, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else if (wall?.tall == true || spec.layout == HomeLayout.TILES) Box(Modifier.fillMaxSize().background(colors.background))

            // Icons of placed apps first (from the disk cache → instant), then everything else for the drawer.
            LaunchedEffect(apps, spec.iconShape, spec.iconSizeDp) {
                if (apps.isNotEmpty()) {
                    val placed = layout.placedApps().toHashSet()
                    app.icons.prefetch(apps.sortedBy { if (it.key in placed) 0 else 1 }, spec.iconShape, with(density) { spec.iconSizeDp.dp.roundToPx() })
                }
            }

            val clockRows = if (spec.clockStyle == ClockStyle.NONE) 0 else 2
            val dragging = ui.drag.item != null
            val pageCount = if (spec.layout == HomeLayout.MINIMAL) 2
                else 1 + layout.pages(spec.columns * (spec.rows - clockRows), spec.columns * spec.rows).size + (if (dragging) 1 else 0)
            val countState = androidx.compose.runtime.rememberUpdatedState(pageCount)
            val homePager = rememberPagerState(initialPage = 1) { countState.value }
            val homeModifier = Modifier.fillMaxSize().graphicsLayer {
                val p = ui.drawer.progress
                // 1.2: while an app folds back into its icon, the grid zooms in from 94 % (like Pixel).
                val z = ui.homeZoom.value
                val s = (1f - 0.06f * p) * (0.94f + 0.06f * z)
                scaleX = s; scaleY = s
                alpha = 1f - p
            }
            Box(homeModifier.homeGestures(ui, config, hPx.toFloat(), spec.layout != HomeLayout.TILES)) {
                when (spec.layout) {
                    HomeLayout.GRID -> GridHome(ui, layout, byKey, wall, homePager)
                    HomeLayout.MINIMAL -> MinimalHome(ui, layout, byKey, homePager, config)
                    HomeLayout.TILES -> TilesHome(ui, config, layout, byKey, visibleApps, wall)
                }
            }
            if (spec.layout != HomeLayout.TILES && !config.allAppsOnHome) DrawerLayer(ui, visibleApps, config, wall)

            OverlayEffect(spec.overlay)
            FolderOverlay(ui, layout)
            ReturnOverlay(ui, layout)
            MenuOverlay(ui)
            DragLayer(ui)
            StopBanner(ui, stop)
            RenameDialog(ui)
            FolderRenameDialog(ui, layout)
            if (ui.widgetPicker) WidgetPicker(onDismiss = { ui.widgetPicker = false })

            // HOME pressed while already home → close everything, go to the main page.
            LaunchedEffect(ui.revealPage, ui.revealFolder) {
                val page = ui.revealPage ?: return@LaunchedEffect
                ui.revealFolder?.let { ui.openFolder = it }
                if (spec.layout != HomeLayout.TILES && homePager.currentPage != page) homePager.scrollToPage(page)
                ui.revealPage = null
            }
            LaunchedEffect(ui.homePresses) {
                if (ui.homePresses == 0) return@LaunchedEffect
                ui.menu = null; ui.tileEdit = null; ui.widgetEdit = false; ui.cancelDrag()
                if (ui.openFolder != null) ui.openFolder = null
                else if (ui.drawer.progress > 0f) ui.drawer.close()
                else if (homePager.currentPage != 1 && spec.layout != HomeLayout.TILES) homePager.animateScrollToPage(1)
            }
            BackHandler(enabled = ui.menu != null || ui.drawer.progress > 0f || ui.tileEdit != null || ui.widgetEdit || ui.openFolder != null || ui.drag.item != null) {
                when {
                    ui.drag.item != null -> ui.cancelDrag()
                    ui.menu != null -> ui.menu = null
                    ui.openFolder != null -> ui.openFolder = null
                    ui.tileEdit != null -> ui.tileEdit = null
                    ui.widgetEdit -> ui.widgetEdit = false
                    else -> ui.drawer.close()
                }
            }
        }
      }
    }
}

/** Swipe up = drawer (follows the finger), swipe down = shade, double tap = lock, long press = home menu. */
@Composable
private fun Modifier.homeGestures(ui: UiState, config: LauncherConfig, height: Float, vertical: Boolean): Modifier = this.then(
    Modifier.pointerInput(config.doubleTap) {
        detectTapGestures(
            onDoubleTap = { if (config.doubleTap != GestureAction.NONE) ui.actions?.gesture(config.doubleTap) },
            onLongPress = { pos -> ui.actions?.homeMenu(Rect(pos, pos)) },
        )
    }
).then(if (!vertical) Modifier else Modifier.composedDrag(ui, config, height))

@Composable
private fun Modifier.composedDrag(ui: UiState, configNow: LauncherConfig, height: Float): Modifier {
    val config by androidx.compose.runtime.rememberUpdatedState(configNow)
    val mode = remember { intArrayOf(0) }
    val acc = remember { floatArrayOf(0f) }
    val thresholdPx = with(LocalDensity.current) { 72.dp.toPx() }
    val state = rememberDraggableState { delta ->
        if (mode[0] == 0) mode[0] = if (delta < 0) 1 else 2
        acc[0] += delta
        if (mode[0] == 1 && config.swipeUp == GestureAction.DRAWER && !config.allAppsOnHome) ui.drawer.dragBy(-delta / (height * 0.85f))
    }
    return this.draggable(
        state = state, orientation = Orientation.Vertical,
        onDragStarted = { mode[0] = 0; acc[0] = 0f },
        onDragStopped = { v ->
            when (mode[0]) {
                1 -> if (config.swipeUp == GestureAction.DRAWER && !config.allAppsOnHome) ui.drawer.settle(-v / (height * 0.85f))
                     else if (-acc[0] > thresholdPx || v < -1500f) {
                         if (!(config.allAppsOnHome && config.swipeUp == GestureAction.DRAWER)) ui.actions?.gesture(config.swipeUp)
                     }
                2 -> if (acc[0] > thresholdPx || v > 1500f) ui.actions?.gesture(config.swipeDown)
            }
            mode[0] = 0
        },
    )
}

// ---------------------------------------------------------------- GRID (Купертино-style, «Письмо» and generic)

/** Maps the finger to a reorder slot / dock slot / folder target on the icon grid; edge auto-scroll. */
private class GridHost(val ui: UiState) : DropHost {
    var layout = ScreenLayout(); var preview = ScreenLayout()
    lateinit var pager: PagerState
    lateinit var scope: kotlinx.coroutines.CoroutineScope
    var cols = 4; var rows = 6; var clockRows = 2; var firstCap = 16; var cap = 24; var dockMax = 4; var dockEnabled = true
    var screenW = 1f
    val pageGrid = HashMap<Int, BoundsRef>()
    val dockRef = BoundsRef()
    private var edgeDir = 0; private var edgeSince = 0L
    override val maxDock: Int get() = dockMax

    override fun resolve(p: Offset, item: DragItem): DropSpot? {
        if (ui.drawer.progress > 0.3f || ui.openFolder != null) return null
        val folderable = item.app != null && !isFolderId(item.id)
        if (dockEnabled) dockRef.bounds()?.let { r ->
            if (p.y >= r.top - 12f && p.y <= r.bottom + 12f) {
                val shown = preview.dock
                if (shown.isNotEmpty() && folderable) {
                    val slot = r.width / shown.size
                    val i = ((p.x - r.left) / slot).toInt().coerceIn(0, shown.size - 1)
                    val cx = r.left + (i + 0.5f) * slot
                    if (abs(p.x - cx) < slot * 0.22f && shown[i] != item.id) return DropSpot.Into(shown[i])
                }
                val n = layout.detach(item.id).dock.size
                return DropSpot.Dock(((p.x - r.left) / r.width * (n + 1)).toInt().coerceIn(0, n))
            }
        }
        val page = pager.currentPage - 1
        if (page < 0) return null
        val g = pageGrid[page]?.bounds() ?: return null
        if (!g.contains(p)) return null
        val r = if (page == 0) rows - clockRows else rows
        val cw = g.width / cols; val ch = g.height / r
        val col = ((p.x - g.left) / cw).toInt().coerceIn(0, cols - 1)
        val row = ((p.y - g.top) / ch).toInt().coerceIn(0, r - 1)
        val idx = layout.pageStart(page, firstCap, cap) + row * cols + col
        val shown = preview.home.getOrNull(idx)
        val cx = g.left + (col + 0.5f) * cw; val cy = g.top + (row + 0.5f) * ch
        if (folderable && shown != null && shown != item.id && abs(p.x - cx) < cw * 0.26f && abs(p.y - cy) < ch * 0.26f) return DropSpot.Into(shown)
        return DropSpot.Home(idx.coerceAtMost(layout.detach(item.id).home.size))
    }

    override fun onFrame(p: Offset, nanos: Long) {
        if (ui.drawer.progress > 0.3f) return
        val edge = screenW * 0.07f
        val dir = when { p.x < edge -> -1; p.x > screenW - edge -> 1; else -> 0 }
        if (dir != edgeDir) { edgeDir = dir; edgeSince = nanos }
        if (dir != 0 && nanos - edgeSince > 450_000_000L && !pager.isScrollInProgress) {
            val target = pager.currentPage + dir
            if (target >= 1 && target < pager.pageCount) scope.launch { pager.animateScrollToPage(target) }
            edgeSince = nanos + 350_000_000L
        }
    }
}

@Composable
fun GridHome(ui: UiState, layout: ScreenLayout, byKey: Map<String, AppInfo>, wall: WallImages?, pager: PagerState) {
    val spec = LocalSpec.current
    val scope = rememberCoroutineScope()
    val host = remember { GridHost(ui) }
    val preview = ui.drag.preview(layout)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val inset = maxWidth * 0.068f
        val clockRows = if (spec.clockStyle == ClockStyle.NONE) 0 else 2
        val firstCap = spec.columns * (spec.rows - clockRows)
        val cap = spec.columns * spec.rows
        val pages = remember(preview.home, firstCap, cap) { preview.pages(firstCap, cap) }
        host.layout = layout; host.preview = preview; host.pager = pager; host.scope = scope
        host.cols = spec.columns; host.rows = spec.rows; host.clockRows = clockRows; host.firstCap = firstCap; host.cap = cap
        host.dockMax = spec.columns; host.dockEnabled = spec.dockStyle != DockStyle.NONE
        host.screenW = constraints.maxWidth.toFloat()
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            val effect = LocalPageEffect.current
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 1, key = { it }) { page ->
                val fx = remember(effect, pager, page) { PageFx(effect, pager, page) }
                Box(Modifier.fillMaxSize().pageEffect(effect, pager, page, itemsDoIt = page > 0)) {
                    if (page == 0) WidgetsPage(ui)
                    else androidx.compose.runtime.CompositionLocalProvider(LocalPageFx provides fx) {
                        val ref = remember(page) { BoundsRef() }
                        androidx.compose.runtime.DisposableEffect(page, ref) { host.pageGrid[page - 1] = ref; onDispose { if (host.pageGrid[page - 1] === ref) host.pageGrid.remove(page - 1) } }
                        AppPage(ui, pages.getOrNull(page - 1).orEmpty(), preview, byKey, first = page == 1, clockRows = clockRows, inset = inset, gridRef = ref)
                    }
                }
            }
            PageDots(pager, minOf(pager.pageCount, pages.size + 1 + if (ui.drag.item != null) 1 else 0))
            Dock(ui, preview, byKey, wall, host.dockRef)
            Spacer(Modifier.navigationBarsPadding())
        }
    }
    androidx.compose.runtime.DisposableEffect(host) { ui.drag.host = host; onDispose { if (ui.drag.host === host) ui.drag.host = null } }
}

/** One home item (app or folder) with drag visibility, folder-target highlight and animated reflow. */
@Composable
private fun HomeItem(ui: UiState, id: String, layout: ScreenLayout, byKey: Map<String, AppInfo>, source: DragSource, showLabel: Boolean, modifier: Modifier = Modifier) {
    val spec = LocalSpec.current
    Box(modifier.animatePlacement(ui.animations).launcherItemVisibility(ui, id), contentAlignment = Alignment.Center) {
        Box(Modifier.folderTarget(ui, id)) {
            if (isFolderId(id)) layout.folders[id]?.let { FolderCell(it, spec.iconSizeDp.dp, showLabel, source) }
            else byKey[id]?.let { AppCell(it, spec.iconSizeDp.dp, showLabel, source = source) }
        }
    }
}

@Composable
private fun AppPage(ui: UiState, keys: List<String>, layout: ScreenLayout, byKey: Map<String, AppInfo>, first: Boolean, clockRows: Int, inset: androidx.compose.ui.unit.Dp, gridRef: BoundsRef) {
    val spec = LocalSpec.current
    val fx = LocalPageFx.current?.takeIf { it.effect.perItem() }
    val insetPx = with(LocalDensity.current) { inset.toPx() }
    Column(Modifier.fillMaxSize().padding(horizontal = inset)) {
        val rows = if (first) spec.rows - clockRows else spec.rows
        if (first && clockRows > 0) {
            Box(Modifier.fillMaxWidth().weight(clockRows.toFloat())
                .then(if (fx == null) Modifier else Modifier.graphicsLayer { itemPageLayer(fx, 0f, 0f, size.width + 2 * insetPx, size.height) }),
                contentAlignment = Alignment.Center) { ClockHeader() }
        }
        val cols = spec.columns
        Layout(
            content = { keys.forEach { id -> androidx.compose.runtime.key(id) { HomeItem(ui, id, layout, byKey, DragSource.HOME, spec.showLabels) } } },
            modifier = Modifier.fillMaxWidth().weight(rows.toFloat().coerceAtLeast(1f)).trackBounds(gridRef),
        ) { measurables, c ->
            val cw = c.maxWidth / cols; val ch = c.maxHeight / rows.coerceAtLeast(1)
            val placeables = measurables.map { it.measure(Constraints(maxWidth = cw, maxHeight = ch)) }
            layout(c.maxWidth, c.maxHeight) {
                val pageW = c.maxWidth + 2 * insetPx
                placeables.forEachIndexed { i, p ->
                    val col = i % cols; val row = i / cols
                    val x = col * cw + (cw - p.width) / 2; val y = row * ch + (ch - p.height) / 2
                    if (fx == null) p.place(x, y)
                    else {
                        // Column centre relative to the page centre: the cylinder/wave bends each column separately.
                        val cx = insetPx + col * cw + cw / 2f - pageW / 2f
                        p.placeWithLayer(x, y) { itemPageLayer(fx, cx, 0f, pageW, c.maxHeight.toFloat()) }
                    }
                }
            }
        }
    }
}

@Composable
fun PageDots(pager: PagerState, count: Int) {
    val spec = LocalSpec.current; val colors = LocalColors.current
    if (spec.pageIndicator == PageIndicator.NONE || count <= 1) { Spacer(Modifier.height(10.dp)); return }
    val dot = 7.dp; val gap = 9.dp
    Box(Modifier.fillMaxWidth().height(26.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.width((dot + gap) * count).height(dot)) {
            val pos = pager.currentPage + pager.currentPageOffsetFraction
            val d = dot.toPx(); val g = gap.toPx()
            val c = colors.onWallpaper
            if (spec.pageIndicator == PageIndicator.LINE) {
                drawRoundRect(c.copy(alpha = 0.25f), size = size.copy(height = d / 2), topLeft = Offset(0f, d / 4), cornerRadius = androidx.compose.ui.geometry.CornerRadius(d))
                val seg = size.width / count
                drawRoundRect(colors.accent, topLeft = Offset(seg * pos, 0f), size = androidx.compose.ui.geometry.Size(seg, d), cornerRadius = androidx.compose.ui.geometry.CornerRadius(d))
            } else for (i in 0 until count) {
                val a = (1f - abs(pos - i)).coerceIn(0f, 1f)
                drawCircle(c.copy(alpha = 0.35f + 0.65f * a), radius = d / 2, center = Offset(g / 2 + i * (d + g) + d / 2, d / 2))
            }
        }
    }
}

@Composable
fun Dock(ui: UiState, layout: ScreenLayout, byKey: Map<String, AppInfo>, wall: WallImages?, rowRef: BoundsRef) {
    val spec = LocalSpec.current; val colors = LocalColors.current
    if (spec.dockStyle == DockStyle.NONE || (layout.dock.isEmpty() && ui.drag.item == null)) return
    val radius = spec.cornerDp.dp
    val posRef = remember { BoundsRef() }
    val bg: Modifier = when (spec.dockStyle) {
        DockStyle.GLASS -> Modifier.clip(RoundedCornerShape(radius)).drawBehind {
            val coords = posRef.coords
            if (wall != null && spec.blur && coords != null && coords.isAttached) {
                val p = coords.positionInWindow()
                val sx = wall.frosted.width / (wall.full.width.toFloat()); val sy = wall.frosted.height / (wall.full.height.toFloat())
                drawImage(wall.frosted, srcOffset = IntOffset((p.x * sx).roundToInt().coerceAtLeast(0), (p.y * sy).roundToInt().coerceAtLeast(0)),
                    srcSize = IntSize((size.width * sx).roundToInt().coerceAtLeast(1), (size.height * sy).roundToInt().coerceAtLeast(1)),
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = androidx.compose.ui.graphics.FilterQuality.Low)
                drawRect(Color.White.copy(alpha = if (colors.dark) 0.16f else 0.35f))
            } else drawRect(if (colors.dark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.5f))
        }
        DockStyle.SOLID -> Modifier.clip(RoundedCornerShape(radius)).background(colors.surface.copy(alpha = 0.88f))
        else -> Modifier
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp)
        .trackBounds(posRef).then(bg).padding(vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth().height(spec.iconSizeDp.dp).trackBounds(rowRef), verticalAlignment = Alignment.CenterVertically) {
            layout.dock.take(spec.columns).forEach { id ->
                androidx.compose.runtime.key(id) { HomeItem(ui, id, layout, byKey, DragSource.DOCK, showLabel = false, modifier = Modifier.weight(1f)) }
            }
        }
    }
}


// ---------------------------------------------------------------- MINIMAL (text-only)

private class MinimalHost(val ui: UiState) : DropHost {
    var shown: List<String> = emptyList()
    var defaults: List<String> = emptyList()
    val rows = HashMap<String, BoundsRef>()
    val area = BoundsRef()
    var pagerPage = 1
    override fun prepare(l: ScreenLayout): ScreenLayout = if (l.favorites.isEmpty()) l.copy(favorites = defaults) else l
    override fun resolve(p: Offset, item: DragItem): DropSpot? {
        if (ui.drawer.progress > 0.3f || pagerPage != 1 || item.app == null) return null
        val a = area.bounds() ?: return null
        if (p.y < a.top - 160f || p.y > a.bottom + 160f) return null
        var idx = 0
        for (k in shown) {
            if (k == item.id) continue
            val r = rows[k]?.bounds() ?: continue
            if (r.center.y < p.y) idx++
        }
        return DropSpot.Fav(idx)
    }
}

@Composable
fun MinimalHome(ui: UiState, layout: ScreenLayout, byKey: Map<String, AppInfo>, pager: PagerState, config: LauncherConfig) {
    val spec = LocalSpec.current; val colors = LocalColors.current; val fonts = LocalFonts.current
    val actions = LocalActions.current
    val host = remember { MinimalHost(ui) }
    val defaults = remember(layout) { (layout.dock + layout.home).filter { !isFolderId(it) }.take(8) }
    host.defaults = defaults
    host.pagerPage = pager.currentPage
    val preview = ui.drag.preview(layout)
    val favKeys = preview.favorites.ifEmpty { defaults }.filter { it in byKey }.let { if (config.allAppsOnHome) it else it.take(8) }
    host.shown = favKeys
    androidx.compose.runtime.DisposableEffect(host) { ui.drag.host = host; onDispose { if (ui.drag.host === host) ui.drag.host = null } }
    val effect = LocalPageEffect.current
    HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
      Box(Modifier.fillMaxSize().pageEffect(effect, pager, page)) {
        if (page == 0) WidgetsPage(ui)
        else if (page == 1) Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 30.dp)
            .then(if (config.allAppsOnHome) Modifier.verticalScroll(rememberScrollState()) else Modifier)) {
            Spacer(Modifier.height(36.dp))
            ClockHeader(Modifier.fillMaxWidth())
            if (config.allAppsOnHome) Spacer(Modifier.height(24.dp)) else Spacer(Modifier.weight(1f))
            Column(Modifier.fillMaxWidth().trackBounds(host.area)) {
                favKeys.forEach { k ->
                    val a = byKey[k] ?: return@forEach
                    androidx.compose.runtime.key(k) {
                        val ref = remember { BoundsRef() }
                        androidx.compose.runtime.DisposableEffect(k, ref) { host.rows[k] = ref; ui.iconBounds[k] = ref; onDispose { if (host.rows[k] === ref) host.rows.remove(k); if (ui.iconBounds[k] === ref) ui.iconBounds.remove(k) } }
                        Text(
                            spec.labelPrefix + spec.withCase(a.label),
                            style = labelStyle(colors.onWallpaper, spec.labelSizeSp.sp).copy(fontFamily = fonts.heading),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.animatePlacement(ui.animations).launcherItemVisibility(ui, k).padding(vertical = 9.dp).trackBounds(ref)
                                .launcherItem(ui, ref,
                                    onClick = { actions.launch(a, ref.bounds()) },
                                    onLongPress = { actions.showMenu(a, null, ref.bounds() ?: Rect.Zero) },
                                    dragItem = { local -> DragItem(k, a, DragSource.FAV, size = androidx.compose.ui.geometry.Size(ref.bounds()?.width ?: 1f, ref.bounds()?.height ?: 1f), grab = local, asLabel = true, startBounds = ref.bounds()) }),
                        )
                    }
                }
            }
            if (config.allAppsOnHome) Spacer(Modifier.height(20.dp)) else {
                Spacer(Modifier.weight(0.6f))
                Text(if (spec.labelPrefix.isNotEmpty()) "$ ls /apps  ↑" else "↑  все приложения", style = labelStyle(colors.onWallpaper.copy(alpha = 0.45f), 13.sp),
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 18.dp).clickable(remember { MutableInteractionSource() }, null) { ui.drawer.open() })
            }
        } else Box(Modifier.fillMaxSize())
      }
    }
}

// ---------------------------------------------------------------- overlays

@Composable
fun OverlayEffect(o: Overlay) {
    if (o == Overlay.NONE) return
    Box(Modifier.fillMaxSize().graphicsLayer().drawWithCache {
        val lines = when (o) { Overlay.SCANLINES -> (size.height / 3.dp.toPx()).toInt(); else -> 0 }
        val rnd = kotlin.random.Random(7)
        val grain = if (o == Overlay.GRAIN) List(1800) { Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height) } else emptyList()
        val vignette = Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = if (o == Overlay.VIGNETTE) 0.32f else 0.38f)), center = Offset(size.width / 2f, size.height / 2f), radius = size.maxDimension * 0.75f)
        onDrawBehind {
            if (o == Overlay.SCANLINES) {
                val step = 3.dp.toPx()
                for (i in 0 until lines) drawRect(Color.Black.copy(alpha = 0.16f), topLeft = Offset(0f, i * step), size = androidx.compose.ui.geometry.Size(size.width, step / 2.5f))
                drawRect(vignette)
            }
            if (o == Overlay.GRAIN) grain.forEach { drawRect(Color(0x14402010), topLeft = it, size = androidx.compose.ui.geometry.Size(2f, 2f)) }
            if (o == Overlay.VIGNETTE) drawRect(vignette)
        }
    })
}

/** Context menu card positioned at the pressed item, animated with the theme's motion. */
@Composable
fun MenuOverlay(ui: UiState) {
    val m = ui.menu ?: return
    val spec = LocalSpec.current; val colors = LocalColors.current
    val anim = LocalAnimations.current
    val appear = remember(m) { Animatable(0f) }
    LaunchedEffect(m) { appear.animateTo(1f, MotionSpecs.enter(spec.motion, anim)) }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f * appear.value.coerceIn(0f, 1f)))
        .pointerInput(m) { detectTapGestures { ui.menu = null } }) {
        val radius = if (spec.motion == Motion.TURNSTILE) 0.dp else (spec.cornerDp.coerceAtMost(26)).dp
        Layout(content = {
            Column(
                Modifier.widthIn(min = 220.dp, max = 300.dp)
                    .graphicsLayer {
                        val a = appear.value
                        alpha = a.coerceIn(0f, 1f)
                        if (spec.motion == Motion.TURNSTILE) { rotationX = (1f - a) * -60f; transformOrigin = TransformOrigin(0.5f, 0f); cameraDistance = 12f * density }
                        else { scaleX = 0.85f + 0.15f * a; scaleY = 0.85f + 0.15f * a }
                    }
                    .clip(RoundedCornerShape(radius))
                    .background(if (spec.motion == Motion.TURNSTILE) (if (colors.dark) Color(0xFF1F1F1F) else Color.White) else colors.surface.copy(alpha = 0.97f))
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(vertical = 6.dp)
            ) {
                m.title?.let { Text(spec.withCase(it), style = labelStyle(colors.onSurface.copy(alpha = 0.6f), 13.sp).copy(shadow = null), modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                if (m.app != null) ShortcutRows(m.app) { ui.menu = null }
                m.entries.forEachIndexed { i, e ->
                    if (i == 0 && m.app != null) HorizontalDivider(color = colors.onSurface.copy(alpha = 0.1f))
                    Text(spec.withCase(e.label), style = labelStyle(if (e.danger) Color(0xFFFF453A) else colors.onSurface, 16.sp).copy(shadow = null, fontFamily = LocalFonts.current.body),
                        modifier = Modifier.fillMaxWidth().clickable { ui.menu = null; e.onClick() }.padding(horizontal = 18.dp, vertical = 12.dp))
                }
            }
        }) { measurables, constraints ->
            val p = measurables.first().measure(Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight))
            val margin = 12.dp.roundToPx()
            val below = m.anchor.bottom + p.height + margin < constraints.maxHeight
            val y = if (below) m.anchor.bottom.roundToInt() + margin / 2 else (m.anchor.top.roundToInt() - p.height - margin / 2).coerceAtLeast(margin)
            val x = (m.anchor.center.x.roundToInt() - p.width / 2).coerceIn(margin, (constraints.maxWidth - p.width - margin).coerceAtLeast(margin))
            layout(constraints.maxWidth, constraints.maxHeight) { p.place(x, y) }
        }
    }
}

@Composable
private fun ShortcutRows(app: AppInfo, dismiss: () -> Unit) {
    val repo = LauncherApp.instance.apps
    val colors = LocalColors.current; val spec = LocalSpec.current
    val actions = LocalActions.current
    val density = LocalDensity.current
    val list by produceState(emptyList<android.content.pm.ShortcutInfo>(), app.key) { value = withContext(Dispatchers.IO) { repo.shortcuts(app) } }
    list.forEach { s ->
        val icon by produceState<ImageBitmap?>(null, s.id) { value = withContext(Dispatchers.IO) { LauncherApp.instance.icons.shortcutIcon(s, with(density) { 28.dp.roundToPx() }) } }
        Row(Modifier.fillMaxWidth().clickable { dismiss(); actions.launchShortcut(app, s, null) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            icon?.let { Image(it, null, Modifier.width(26.dp).height(26.dp)) } ?: Spacer(Modifier.width(26.dp))
            Spacer(Modifier.width(14.dp))
            Text(spec.withCase((s.shortLabel ?: s.longLabel ?: "").toString()), style = labelStyle(colors.onSurface, 15.sp).copy(shadow = null), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun RenameDialog(ui: UiState) {
    val a = ui.renameTarget ?: return
    var text by remember(a.key) { mutableStateOf(a.label) }
    AlertDialog(
        onDismissRequest = { ui.renameTarget = null },
        title = { Text("Переименовать") },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, label = { Text(a.originalLabel) }) },
        confirmButton = {
            TextButton(onClick = {
                val t = text.trim()
                LauncherApp.instance.store.update { c -> c.copy(renamed = if (t.isEmpty() || t == a.originalLabel) c.renamed - a.key else c.renamed + (a.key to t)) }
                ui.renameTarget = null
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = { ui.renameTarget = null }) { Text("Отмена") } },
    )
}

/** Builds the long-press menu for an app or a tile (moving things is done by dragging). */
fun buildAppMenu(ui: UiState, app: AppInfo?, tile: TileItem?, spec: ThemeSpec, config: LauncherConfig, actions: LauncherActions, anchor: Rect): MenuState {
    val store = LauncherApp.instance.store
    val theme = spec.id
    val layout = config.shownLayout(theme)
    fun edit(f: (ScreenLayout) -> ScreenLayout?) = store.update { it.editLayout(theme, f) }
    val e = ArrayList<MenuEntry>()
    if (tile != null) {
        e += MenuEntry("Размер: " + tile.size.next().title) { edit { it.resizeTile(tile.id) } }
        e += MenuEntry("Открепить от начального экрана") { edit { it.unpinTile(tile.id) } }
    }
    if (app != null) {
        if (spec.layout == HomeLayout.TILES) {
            if (!layout.hasTileFor(app.key)) e += MenuEntry("Закрепить на начальном экране") { edit { it.pinTile(TileItem("t_" + app.key, TileKind.APP, app.key)) } }
        } else if (spec.layout == HomeLayout.MINIMAL) {
            val defaults = (layout.dock + layout.home).filter { !isFolderId(it) }.take(8)
            val favs = layout.favorites.ifEmpty { defaults }
            if (app.key in favs) e += MenuEntry("Убрать из избранного") { edit { l -> l.copy(favorites = favs - app.key) } }
            else if (favs.size < 8) e += MenuEntry("В избранное на главном") { edit { l -> l.copy(favorites = favs + app.key) } }
        } else {
            val placed = layout.isPlaced(app.key); val inDock = app.key in layout.dock
            if (!placed) e += MenuEntry("На главный экран") { edit { it.appendHome(app.key) } }
            if (!inDock && layout.dock.size < spec.columns) e += MenuEntry("В док") { edit { it.addToDock(app.key, spec.columns) } }
            if (placed) e += MenuEntry(if (inDock) "Убрать из дока" else if (layout.folderOf(app.key) != null) "Убрать из папки" else "Убрать с экрана") { edit { it.detach(app.key) } }
        }
        e += MenuEntry("Переименовать") { ui.renameTarget = app }
        e += MenuEntry(if (app.key in config.hidden) "Показывать в меню" else "Скрыть из меню") {
            store.update { c -> c.copy(hidden = if (app.key in c.hidden) c.hidden - app.key else c.hidden + app.key) }
        }
        e += MenuEntry("О приложении") { actions.appInfo(app, anchor) }
        e += MenuEntry("Удалить приложение", danger = true) { actions.uninstall(app) }
    }
    return MenuState(anchor, app, tile, title = app?.label ?: tile?.kind?.name?.lowercase(), entries = e)
}
