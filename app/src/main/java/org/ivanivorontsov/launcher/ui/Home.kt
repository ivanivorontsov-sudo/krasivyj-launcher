package org.ivanivorontsov.launcher.ui

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
@Composable
fun rememberWallpaper(spec: ThemeSpec, wPx: Int, hPx: Int, tilePhoto: String?): WallImages? {
    val ctx = LocalContext.current
    val tall = spec.layout == HomeLayout.TILES && spec.tileFill == TileFill.TRANSPARENT
    val needs = Wallpapers.hasGenerated(spec) || tall
    val img by produceState<WallImages?>(null, spec.wallpaper, spec.wallpaperColors, spec.wallpaperDim, spec.id, wPx, hPx, tall, tilePhoto) {
        if (!needs || wPx <= 0 || hPx <= 0) { value = null; return@produceState }
        value = withContext(Dispatchers.Default) {
            val h = if (tall) (hPx * 1.45f).toInt() else hPx
            val bmp: Bitmap = (if (tall && tilePhoto != null) loadPhoto(ctx, Uri.parse(tilePhoto), wPx, h) else null) ?: Wallpapers.render(spec, wPx, h)
            WallImages(bmp.asImageBitmap(), Wallpapers.frosted(bmp).asImageBitmap(), tall, bmp)
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
    val apps by app.apps.apps.collectAsStateCompat()
    val animations = remember(ui.resumed) { org.ivanivorontsov.launcher.data.Device.animationsEnabled() }
    ui.drawer.animationsEnabled = animations
    val byKey = remember(apps) { apps.associateBy { it.key } }
    val visibleApps = remember(apps, config.hidden) { apps.filter { it.key !in config.hidden } }

    LauncherTheme(spec, animations) {
        val density = LocalDensity.current
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wPx = with(density) { maxWidth.roundToPx() }; val hPx = with(density) { maxHeight.roundToPx() }
            val wall = rememberWallpaper(spec, wPx, hPx, config.tileBackgroundUri)
            val colors = LocalColors.current

            // Background: code-drawn wallpaper, or the phone wallpaper shows through the transparent window.
            if (wall != null && !wall.tall) Image(wall.full, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else if (wall?.tall == true || spec.layout == HomeLayout.TILES) Box(Modifier.fillMaxSize().background(colors.background))

            LaunchedEffect(apps, spec.iconShape, spec.iconSizeDp) {
                if (apps.isNotEmpty()) app.icons.prefetch(apps, spec.iconShape, with(density) { spec.iconSizeDp.dp.roundToPx() })
            }

            val clockRows = if (spec.clockStyle == ClockStyle.NONE) 0 else 2
            val pageCount = if (spec.layout == HomeLayout.MINIMAL) 2
                else 1 + config.pagesFor(spec.columns * (spec.rows - clockRows), spec.columns * spec.rows).size
            val countState = androidx.compose.runtime.rememberUpdatedState(pageCount)
            val homePager = rememberPagerState(initialPage = 1) { countState.value }
            val homeModifier = Modifier.fillMaxSize().graphicsLayer {
                val p = ui.drawer.progress
                val e = ui.enter.value
                val s = (1f - 0.06f * p) * (0.92f + 0.08f * e)
                scaleX = s; scaleY = s
                alpha = (1f - p) * (0.35f + 0.65f * e)
            }
            Box(homeModifier.homeGestures(ui, config, hPx.toFloat(), spec.layout != HomeLayout.TILES)) {
                when (spec.layout) {
                    HomeLayout.GRID -> GridHome(ui, config, byKey, wall, homePager)
                    HomeLayout.MINIMAL -> MinimalHome(ui, config, byKey, homePager)
                    HomeLayout.TILES -> TilesHome(ui, config, byKey, visibleApps, wall)
                }
            }
            if (spec.layout != HomeLayout.TILES) DrawerLayer(ui, visibleApps, config, wall)

            OverlayEffect(spec.overlay)
            MenuOverlay(ui)
            RenameDialog(ui)
            if (ui.widgetPicker) WidgetPicker(onDismiss = { ui.widgetPicker = false })

            // HOME pressed while already home → close everything, go to the main page.
            LaunchedEffect(ui.homePresses) {
                if (ui.homePresses == 0) return@LaunchedEffect
                ui.menu = null; ui.tileEdit = null; ui.widgetEdit = false
                if (ui.drawer.progress > 0f) ui.drawer.close()
                else if (homePager.currentPage != 1 && spec.layout != HomeLayout.TILES) homePager.animateScrollToPage(1)
            }
            BackHandler(enabled = ui.menu != null || ui.drawer.progress > 0f || ui.tileEdit != null || ui.widgetEdit) {
                when {
                    ui.menu != null -> ui.menu = null
                    ui.tileEdit != null -> ui.tileEdit = null
                    ui.widgetEdit -> ui.widgetEdit = false
                    else -> ui.drawer.close()
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
        if (mode[0] == 1 && config.swipeUp == GestureAction.DRAWER) ui.drawer.dragBy(-delta / (height * 0.85f))
    }
    return this.draggable(
        state = state, orientation = Orientation.Vertical,
        onDragStarted = { mode[0] = 0; acc[0] = 0f },
        onDragStopped = { v ->
            when (mode[0]) {
                1 -> if (config.swipeUp == GestureAction.DRAWER) ui.drawer.settle(-v / (height * 0.85f))
                     else if (-acc[0] > thresholdPx || v < -1500f) ui.actions?.gesture(config.swipeUp)
                2 -> if (acc[0] > thresholdPx || v > 1500f) ui.actions?.gesture(config.swipeDown)
            }
            mode[0] = 0
        },
    )
}

// ---------------------------------------------------------------- GRID (Купертино-style and generic)

@Composable
fun GridHome(ui: UiState, config: LauncherConfig, byKey: Map<String, AppInfo>, wall: WallImages?, pager: PagerState) {
    val spec = LocalSpec.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val inset = maxWidth * 0.068f
        val clockRows = if (spec.clockStyle == ClockStyle.NONE) 0 else 2
        val firstCap = spec.columns * (spec.rows - clockRows)
        val pages = remember(config.home, firstCap, spec.columns, spec.rows) { config.pagesFor(firstCap, spec.columns * spec.rows) }
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 1, key = { it }) { page ->
                when {
                    page == 0 -> WidgetsPage(ui)
                    page - 1 < pages.size -> AppPage(pages[page - 1], byKey, first = page == 1, clockRows = clockRows, inset = inset)
                    else -> Box(Modifier.fillMaxSize())
                }
            }
            PageDots(pager, minOf(pager.pageCount, pages.size + 1))
            Dock(config.dock.mapNotNull { byKey[it] }, wall)
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun AppPage(keys: List<String>, byKey: Map<String, AppInfo>, first: Boolean, clockRows: Int, inset: androidx.compose.ui.unit.Dp) {
    val spec = LocalSpec.current
    Column(Modifier.fillMaxSize().padding(horizontal = inset)) {
        val rows = if (first) spec.rows - clockRows else spec.rows
        if (first && clockRows > 0) {
            Box(Modifier.fillMaxWidth().weight(clockRows.toFloat()), contentAlignment = Alignment.Center) { ClockHeader() }
        }
        val items = keys.mapNotNull { byKey[it] }
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                for (c in 0 until spec.columns) {
                    val a = items.getOrNull(r * spec.columns + c)
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (a != null) AppCell(a, spec.iconSizeDp.dp, spec.showLabels)
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
fun Dock(apps: List<AppInfo>, wall: WallImages?) {
    val spec = LocalSpec.current; val colors = LocalColors.current
    if (spec.dockStyle == DockStyle.NONE || apps.isEmpty()) return
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            apps.take(spec.columns).forEach { AppCell(it, spec.iconSizeDp.dp, showLabel = false) }
        }
    }
}


// ---------------------------------------------------------------- MINIMAL (text-only)

@Composable
fun MinimalHome(ui: UiState, config: LauncherConfig, byKey: Map<String, AppInfo>, pager: PagerState) {
    val spec = LocalSpec.current; val colors = LocalColors.current; val fonts = LocalFonts.current
    val actions = LocalActions.current
    HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
        if (page == 0) WidgetsPage(ui)
        else if (page == 1) Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 30.dp)) {
            Spacer(Modifier.height(36.dp))
            ClockHeader(Modifier.fillMaxWidth())
            Spacer(Modifier.weight(1f))
            val favs = (config.minimalFavorites.ifEmpty { config.dock + config.home }).mapNotNull { byKey[it] }.take(8)
            favs.forEach { a ->
                val ref = remember(a.key) { BoundsRef() }
                Text(
                    spec.labelPrefix + spec.withCase(a.label),
                    style = labelStyle(colors.onWallpaper, spec.labelSizeSp.sp).copy(fontFamily = fonts.heading),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 9.dp).trackBounds(ref)
                        .pressFeedback(onClick = { actions.launch(a, ref.bounds()) }, onLongClick = { actions.showMenu(a, null, ref.bounds() ?: Rect.Zero) }),
                )
            }
            Spacer(Modifier.weight(0.6f))
            Text(if (spec.labelPrefix.isNotEmpty()) "$ ls /apps  ↑" else "↑  все приложения", style = labelStyle(colors.onWallpaper.copy(alpha = 0.45f), 13.sp),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 18.dp).clickable(remember { MutableInteractionSource() }, null) { ui.drawer.open() })
        } else Box(Modifier.fillMaxSize())
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

/** Builds the long-press menu for an app or a tile. */
fun buildAppMenu(ui: UiState, app: AppInfo?, tile: TileItem?, spec: ThemeSpec, config: LauncherConfig, actions: LauncherActions, anchor: Rect): MenuState {
    val store = LauncherApp.instance.store
    val e = ArrayList<MenuEntry>()
    if (tile != null) {
        e += MenuEntry("Размер: " + tile.size.next().title) { store.update { it.resizeTile(tile.id) } }
        e += MenuEntry("Открепить от начального экрана") { store.update { it.unpinTile(tile.id) } }
    }
    if (app != null) {
        if (spec.layout == HomeLayout.TILES) {
            if (config.tiles.none { it.appKey == app.key }) e += MenuEntry("Закрепить на начальном экране") { store.update { it.pinTile(TileItem("t_" + app.key, TileKind.APP, app.key)) } }
        } else if (spec.layout == HomeLayout.MINIMAL) {
            if (app.key in config.minimalFavorites) e += MenuEntry("Убрать из избранного") { store.update { it.copy(minimalFavorites = it.minimalFavorites - app.key) } }
            else e += MenuEntry("В избранное на главном") { store.update { it.copy(minimalFavorites = (it.minimalFavorites + app.key).take(8)) } }
        } else {
            val onHome = app.key in config.home; val inDock = app.key in config.dock
            if (!onHome && !inDock) e += MenuEntry("На главный экран") { store.update { it.addToHome(app.key) } }
            if (!inDock && config.dock.size < spec.columns) e += MenuEntry("В док") { store.update { it.addToDock(app.key, spec.columns) } }
            if (onHome || inDock) {
                e += MenuEntry("Сдвинуть назад ←") { store.update { it.move(app.key, -1) } }
                e += MenuEntry("Сдвинуть вперёд →") { store.update { it.move(app.key, 1) } }
                e += MenuEntry(if (inDock) "Убрать из дока" else "Убрать с экрана") { store.update { it.removeEverywhere(app.key).let { c -> if (inDock) c.copy(home = c.home) else c } } }
            }
        }
        e += MenuEntry("Переименовать") { ui.renameTarget = app }
        e += MenuEntry(if (app.key in config.hidden) "Показывать в меню" else "Скрыть из меню") {
            store.update { c -> c.copy(hidden = if (app.key in c.hidden) c.hidden - app.key else c.hidden + app.key) }
        }
        e += MenuEntry("О приложении") { actions.appInfo(app, anchor) }
        e += MenuEntry("Удалить", danger = true) { actions.uninstall(app) }
    }
    return MenuState(anchor, app, tile, title = app?.label ?: tile?.kind?.name?.lowercase(), entries = e)
}
