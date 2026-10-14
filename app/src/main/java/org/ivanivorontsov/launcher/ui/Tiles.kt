package org.ivanivorontsov.launcher.ui

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import org.ivanivorontsov.launcher.LauncherApp
import org.ivanivorontsov.launcher.core.GestureAction
import org.ivanivorontsov.launcher.core.LauncherConfig
import org.ivanivorontsov.launcher.core.DropSpot
import org.ivanivorontsov.launcher.core.ScreenLayout
import org.ivanivorontsov.launcher.core.TileFill
import org.ivanivorontsov.launcher.core.TileItem
import org.ivanivorontsov.launcher.core.TileKind
import org.ivanivorontsov.launcher.core.TilePacker
import org.ivanivorontsov.launcher.core.TileSize
import org.ivanivorontsov.launcher.data.AppInfo
import org.ivanivorontsov.launcher.data.Device
import org.ivanivorontsov.launcher.service.NotificationCounter
import kotlin.math.roundToInt
import kotlin.random.Random

private data class Tick(val index: Int, val nonce: Int)

/** Start screen of tiles + (swipe left) the alphabetical app list, like the classic tiles phone. */
@Composable
fun TilesHome(ui: UiState, config: LauncherConfig, layout: ScreenLayout, byKey: Map<String, AppInfo>, apps: List<AppInfo>, wall: WallImages?) {
    val pager = rememberPagerState { 2 }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(ui.homePresses) { if (ui.homePresses > 0 && pager.currentPage != 0) pager.animateScrollToPage(0) }
    androidx.compose.runtime.DisposableEffect(pager) {
        val f: () -> Unit = { scope.launch { pager.animateScrollToPage(0) } }
        ui.showStartPage = f
        onDispose { if (ui.showStartPage === f) ui.showStartPage = null }
    }
    HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1, userScrollEnabled = ui.drag.item == null) { page ->
        if (page == 0) Box(Modifier.fillMaxSize().pageEffect(LocalPageEffect.current, pager, 0)) {
            TileStart(ui, config, layout, byKey, wall, isCurrent = { pager.currentPage == 0 }, toList = { scope.launch { pager.animateScrollToPage(1) } })
        }
        else Column(Modifier.fillMaxSize().statusBarsPadding().graphicsLayer {
            // the list swings in from the right edge as the page arrives
            val f = (pager.currentPage - 1 + pager.currentPageOffsetFraction).coerceIn(-1f, 0f)
            rotationY = f * -25f; transformOrigin = TransformOrigin(0f, 0.5f); cameraDistance = 16f * density
        }) {
            var q by remember { mutableStateOf("") }
            val focus = remember { androidx.compose.ui.focus.FocusRequester() }
            val results = remember(q, apps, config.usage) { if (q.isBlank()) apps else org.ivanivorontsov.launcher.core.SearchEngine.search(apps, q, { it.search }, { config.usage[it.key] ?: 0 }) }
            SearchField(q, { q = it }, focus, "поиск", onGo = { results.firstOrNull()?.let { ui.actions?.launch(it, null) } })
            val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            if (q.isBlank()) TilesListBody(apps, bottom)
            else androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
                items(results.size, key = { results[it].key }) { i -> AppRow(results[i], source = DragSource.LIST) }
            }
        }
    }
}

/** Finger → insertion index among the (preview-)packed tiles; vertical auto-scroll near the edges. */
private class TilesHost(val ui: UiState) : DropHost {
    var preview = ScreenLayout()
    var placed: List<TilePacker.Placed> = emptyList()
    var unit = 1f; var gap = 0f; var margin = 0f; var topPad = 0f
    val content = BoundsRef(); val viewport = BoundsRef()
    lateinit var scroll: androidx.compose.foundation.ScrollState
    var current: () -> Boolean = { true }
    var edgePx = 200f
    private var lastFrame = 0L

    override fun resolve(p: Offset, item: DragItem): DropSpot? {
        if (!current()) return null
        val c = content.bounds() ?: return null
        val v = viewport.bounds() ?: return null
        if (!v.contains(p)) return null
        val cx = p.x - c.left; val cy = p.y - c.top
        val tiles = preview.tiles
        val k = tiles.indexOfFirst { ui.drag.hides(it.id) }
        for (pl in placed) {
            val t = tiles.getOrNull(pl.index) ?: continue
            if (ui.drag.hides(t.id)) continue
            val x = margin + pl.col * (unit + gap); val y = topPad + pl.row * (unit + gap)
            if (cx >= x && cx <= x + pl.w * unit + (pl.w - 1) * gap && cy >= y && cy <= y + pl.h * unit + (pl.h - 1) * gap) return DropSpot.Tile(pl.index)
        }
        val bottom = topPad + TilePacker.rows(placed) * (unit + gap)
        if (cy > bottom) return DropSpot.Tile(tiles.size - (if (k >= 0) 1 else 0))
        return null
    }

    override fun onFrame(p: Offset, nanos: Long) {
        val dt = if (lastFrame == 0L) 0f else ((nanos - lastFrame) / 1_000_000f).coerceAtMost(40f)
        lastFrame = nanos
        val v = viewport.bounds() ?: return
        val speed = 1.1f * dt // px per ms → same speed at any refresh rate
        when {
            p.y > v.bottom - edgePx -> scroll.dispatchRawDelta(speed * ((p.y - (v.bottom - edgePx)) / edgePx).coerceIn(0.2f, 1.5f))
            p.y < v.top + edgePx * 0.6f -> scroll.dispatchRawDelta(-speed * (((v.top + edgePx * 0.6f) - p.y) / (edgePx * 0.6f)).coerceIn(0.2f, 1.5f))
        }
    }
}

@Composable
private fun TileStart(ui: UiState, config: LauncherConfig, layout: ScreenLayout, byKey: Map<String, AppInfo>, wall: WallImages?, isCurrent: () -> Boolean, toList: () -> Unit) {
    val spec = LocalSpec.current; val colors = LocalColors.current
    val anim = LocalAnimations.current
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    val host = remember { TilesHost(ui) }
    val preview = ui.drag.preview(layout)
    val tiles = preview.tiles
    val powerSave by LauncherApp.instance.live.powerSave.collectAsStateCompat()
    val counts by NotificationCounter.counts.collectAsStateCompat()
    val tick = remember { mutableStateOf(Tick(-1, 0)) }
    val pullAcc = remember { floatArrayOf(0f) }
    val pullConn = remember(config.swipeDown) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0) {
                    pullAcc[0] += available.y
                    if (pullAcc[0] > 220f) { pullAcc[0] = -100000f; ui.actions?.gesture(config.swipeDown) }
                }
                return Offset.Zero
            }
            override suspend fun onPostFling(consumed: androidx.compose.ui.unit.Velocity, available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity { pullAcc[0] = 0f; return androidx.compose.ui.unit.Velocity.Zero }
        }
    }
    androidx.compose.runtime.DisposableEffect(host) { ui.drag.host = host; onDispose { if (ui.drag.host === host) ui.drag.host = null } }
    BoxWithConstraints(Modifier.fillMaxSize().trackBounds(host.viewport)) {
        val wPx = constraints.maxWidth.toFloat(); val viewportH = constraints.maxHeight.toFloat()
        val margin = wPx * 0.05f
        val gap = with(density) { spec.tileGapDp.dp.toPx() } * 2f
        val cols = spec.tileColumns
        val unit = (wPx - 2 * margin - (cols - 1) * gap) / cols
        val placed = remember(tiles.map { it.size }, cols) { TilePacker.pack(tiles.map { it.size }, cols) }
        val rows = TilePacker.rows(placed)
        val topPad = with(density) { 44.dp.toPx() }
        val contentH = topPad + rows * (unit + gap)
        host.preview = preview; host.placed = placed; host.unit = unit; host.gap = gap; host.margin = margin; host.topPad = topPad
        host.scroll = scroll; host.current = isCurrent; host.edgePx = with(density) { 90.dp.toPx() }

        // Live tiles: one shared, randomly-timed ticker flips a visible candidate. Paused when the page is
        // hidden, the app is not resumed, battery saver is on, or animations are disabled.
        LaunchedEffect(tiles, placed, ui.resumed, powerSave, anim) {
            if (!ui.resumed || powerSave || !anim) return@LaunchedEffect
            val rnd = Random(System.nanoTime())
            var nonce = 0
            while (true) {
                delay(2200L + rnd.nextLong(2600L))
                if (!isCurrent() || ui.tileEdit != null || ui.drag.item != null) continue
                val s = scroll.value
                val candidates = placed.filter { p ->
                    val t = tiles.getOrNull(p.index) ?: return@filter false
                    val top = topPad + p.row * (unit + gap) - s; val h = p.h * unit
                    val visible = top + h > 0 && top < viewportH
                    val live = when (t.kind) {
                        TileKind.CLOCK, TileKind.CALENDAR -> true
                        TileKind.PHOTOS -> false // has its own Ken Burns cycle
                        TileKind.APP -> t.size != TileSize.SMALL && ((counts[byKey[t.appKey]?.pkg] ?: 0) > 0 || (t.id.hashCode() and 3) == 0)
                    }
                    visible && live
                }
                if (candidates.isNotEmpty()) tick.value = Tick(candidates[rnd.nextInt(candidates.size)].index, ++nonce)
            }
        }
        Box(Modifier.fillMaxSize().nestedScroll(pullConn).verticalScroll(scroll)
            .pointerInput(ui.tileEdit) { if (ui.tileEdit != null) detectTapGestures { ui.tileEdit = null } }) {
            Box(Modifier.fillMaxWidth().trackBounds(host.content).height(with(density) { (contentH + unit).toDp() } + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())) {
                placed.forEach { p ->
                    val t = tiles[p.index]
                    key(t.id) {
                        TileView(
                            ui = ui, tile = t, index = p.index, app = t.appKey?.let { byKey[it] },
                            x = margin + p.col * (unit + gap), y = topPad + p.row * (unit + gap),
                            w = p.w * unit + (p.w - 1) * gap, h = p.h * unit + (p.h - 1) * gap,
                            row = p.row, scrollValue = { scroll.value }, viewportH = viewportH, wall = wall,
                            tick = tick, badge = counts[byKey[t.appKey]?.pkg] ?: 0, unit = unit, gap = gap, margin = margin, contentRef = host.content,
                        )
                    }
                }
                // Small circled arrow after the last tile → app list.
                Box(Modifier.offset { IntOffset((wPx - margin - with(density) { 44.dp.toPx() }).roundToInt(), (contentH + gap).roundToInt()) }
                    .size(40.dp).drawBehind {
                        drawCircle(colors.onSurface, style = Stroke(2.dp.toPx()))
                        val p = Path().apply { moveTo(size.width * 0.3f, size.height / 2); lineTo(size.width * 0.7f, size.height / 2); moveTo(size.width * 0.52f, size.height * 0.32f); lineTo(size.width * 0.7f, size.height / 2); lineTo(size.width * 0.52f, size.height * 0.68f) }
                        drawPath(p, colors.onSurface, style = Stroke(2.dp.toPx()))
                    }.clickable(remember { MutableInteractionSource() }, null) { toList() })
            }
        }
        if (tiles.isEmpty()) Text("долгое нажатие на пустом месте → добавить плитки", color = colors.onSurface.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.Center).padding(32.dp))
    }
}

@Composable
private fun TileView(
    ui: UiState, tile: TileItem, index: Int, app: AppInfo?, x: Float, y: Float, w: Float, h: Float, row: Int,
    scrollValue: () -> Int, viewportH: Float, wall: WallImages?, tick: androidx.compose.runtime.State<Tick>, badge: Int,
    unit: Float, gap: Float, margin: Float, contentRef: BoundsRef,
) {
    val spec = LocalSpec.current; val colors = LocalColors.current
    val actions = LocalActions.current
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val ref = remember { BoundsRef() }
    RegisterIcon(ui, tile.id, ref)
    val flip = remember { Animatable(0f) }
    val myTick by remember { derivedStateOf { val t = tick.value; if (t.index == index) t.nonce else -1 } }
    LaunchedEffect(myTick) {
        if (myTick > 0) flip.animateTo(if (flip.value < 0.5f) 1f else 0f, tween(700, easing = MotionSpecs.turnstileEase))
    }
    val back by remember { derivedStateOf { flip.value >= 0.5f } }
    val editing = ui.tileEdit
    val selected = editing == tile.id
    val d = ui.drag
    val dragged = d.item?.let { it.source == DragSource.TILE && it.id == tile.id } == true
    val ghost = !dragged && d.hides(tile.id)

    // Position and size glide when tiles reflow (drag preview, resize, unpin); the dragged tile follows the finger.
    val ax = remember { Animatable(x) }; val ay = remember { Animatable(y) }
    val aw = remember { Animatable(w) }; val ah = remember { Animatable(h) }
    val last = remember { floatArrayOf(x, y) }
    val wasDragged = remember { booleanArrayOf(false) }
    LaunchedEffect(x, y, w, h, dragged) {
        if (dragged) { wasDragged[0] = true; return@LaunchedEffect }
        if (wasDragged[0]) { wasDragged[0] = false; ax.snapTo(last[0]); ay.snapTo(last[1]) }
        val sp = if (ui.animations) androidx.compose.animation.core.spring<Float>(dampingRatio = 0.8f, stiffness = 420f) else androidx.compose.animation.core.snap()
        launch { ax.animateTo(x, sp) }; launch { ay.animateTo(y, sp) }; launch { aw.animateTo(w, sp) }; launch { ah.animateTo(h, sp) }
    }
    val visible by remember { derivedStateOf { val top = ay.value - scrollValue(); top + ah.value > 0 && top < viewportH } }

    val iconColor = app?.let { LauncherApp.instance.icons.color(it.key) }
    val fill = when (spec.tileFill) {
        TileFill.ACCENT -> colors.accent
        TileFill.ICON_COLOR -> iconColor?.let { Color(it) } ?: colors.accent
        TileFill.TRANSPARENT -> Color.Transparent
    }
    val wDp = with(density) { aw.value.toDp() }; val hDp = with(density) { ah.value.toDp() }
    Box(
        Modifier
            .zIndex(if (dragged) 2f else 0f)
            .offset {
                if (dragged) {
                    val c = contentRef.bounds()
                    val px = d.pointer.x - (c?.left ?: 0f) - d.item!!.grab.x; val py = d.pointer.y - (c?.top ?: 0f) - d.item!!.grab.y
                    last[0] = px; last[1] = py
                    IntOffset(px.roundToInt(), py.roundToInt())
                } else IntOffset(ax.value.roundToInt(), ay.value.roundToInt())
            }
            .size(wDp, hDp)
            .graphicsLayer {
                // turnstile: tiles swing around the left screen edge, staggered top → bottom
                val t = ui.turnstile.value
                val local = ((t * 1.8f) - row * 0.07f).coerceIn(0f, 1f)
                rotationY = local * -85f
                transformOrigin = TransformOrigin(-ax.value / aw.value.coerceAtLeast(1f), 0.5f)
                cameraDistance = 18f * density.density
                val returning = ui.returning?.let { it.key == tile.appKey && ui.returnStyle.card && ui.returnProgress.value < 0.97f } == true
                alpha = (1f - local * local) * when { ui.returning?.key == tile.appKey && ui.returnStyle == org.ivanivorontsov.launcher.core.ReturnStyle.CONTRACT -> ui.returnProgress.value.coerceIn(0f, 1f); returning -> 0f; ghost -> 0.35f; dragged -> 0.92f; editing == null -> 1f; selected -> 1f; else -> 0.6f }
                val lift = if (dragged) 1f + 0.05f * d.lift.value else 1f
                val s = lift * when { editing == null || dragged -> 1f; selected -> 1.04f; else -> 0.94f }
                scaleX = s; scaleY = s
                rotationX = flip.value * 180f
                shadowElevation = if (dragged) 16.dp.toPx() else 0f
            }
            .trackBounds(ref)
            .launcherItem(
                ui, ref,
                onClick = {
                    when {
                        editing != null -> ui.tileEdit = if (selected) null else tile.id
                        else -> when (tile.kind) {
                            TileKind.APP -> app?.let { actions.launch(it, ref.bounds()) }
                            TileKind.CLOCK -> Device.openClock(ctx)
                            TileKind.PHOTOS -> if (LauncherApp.instance.live.hasPhotoAccess()) Device.openGallery(ctx) else actions.requestPhotos()
                            TileKind.CALENDAR -> if (LauncherApp.instance.live.hasCalendarAccess()) Device.openCalendar(ctx) else actions.requestCalendar()
                        }
                    }
                },
                onLongPress = { ui.tileEdit = tile.id },
                dragItem = { local -> DragItem(tile.id, app, DragSource.TILE, tile, size = androidx.compose.ui.geometry.Size(w, h), grab = local, startBounds = ref.bounds()) },
                tiltOverride = true,
            )
            .clipToBounds()
            .drawBehind {
                if (ghost) { drawRect(colors.onSurface.copy(alpha = 0.5f), style = Stroke(2.dp.toPx())); return@drawBehind }
                if (spec.tileFill == TileFill.TRANSPARENT && wall != null) {
                    // Wallpaper visible only inside tiles, scrolling with parallax (gaps stay dark).
                    val scale = wall.full.width / (margin * 2 + spec.tileColumns * unit + (spec.tileColumns - 1) * gap)
                    val tx = ax.value; val ty = ay.value; val tw = aw.value; val th = ah.value
                    val imgY = (ty - scrollValue() * 0.7f).coerceIn(0f, (wall.full.height / scale - th).coerceAtLeast(0f))
                    drawImage(wall.full, srcOffset = IntOffset((tx * scale).roundToInt().coerceAtLeast(0), (imgY * scale).roundToInt()),
                        srcSize = IntSize((tw * scale).roundToInt().coerceAtLeast(1), (th * scale).roundToInt().coerceAtLeast(1)),
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
                    drawRect(Color.Black.copy(alpha = 0.12f))
                } else drawRect(fill)
            },
    ) {
        if (ghost) return@Box
        val content: @Composable (Boolean) -> Unit = { isBack ->
            when (tile.kind) {
                TileKind.APP -> if (app != null) AppTileContent(app, tile.size, badge, isBack) else Text("?", color = Color.White)
                TileKind.CLOCK -> ClockTileContent(tile.size, isBack)
                TileKind.CALENDAR -> CalendarTileContent(tile.size, isBack, ui.permissionsVersion)
                TileKind.PHOTOS -> PhotoTileContent(tile.size, visible, ui)
            }
        }
        if (!back) content(false) else Box(Modifier.fillMaxSize().graphicsLayer { rotationX = 180f }) { content(true) }
        if (selected && !dragged) {
            // edit affordances: unpin (top-right), resize (bottom-right), more (bottom-left)
            val theme = ui.themeId
            EditKnob("✕", Modifier.align(Alignment.TopEnd)) { LauncherApp.instance.store.update { c -> c.editLayout(theme) { it.unpinTile(tile.id) } }; ui.tileEdit = null }
            EditKnob(when (tile.size) { TileSize.SMALL -> "⤢"; TileSize.MEDIUM -> "⟷"; TileSize.WIDE -> "⤡" }, Modifier.align(Alignment.BottomEnd)) {
                LauncherApp.instance.store.update { c -> c.editLayout(theme) { it.resizeTile(tile.id) } }
            }
            if (app != null) EditKnob("⋯", Modifier.align(Alignment.BottomStart)) {
                ui.menu = buildAppMenu(ui, app, tile, spec, LauncherApp.instance.store.config.value, actions, ref.bounds() ?: Rect.Zero); ui.tileEdit = null
            }
        }
    }
}

@Composable
private fun EditKnob(symbol: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.padding(3.dp).size(26.dp).background(Color.White, CircleShape).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Text(symbol, color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AppTileContent(app: AppInfo, size: TileSize, badge: Int, back: Boolean) {
    val fonts = LocalFonts.current
    Box(Modifier.fillMaxSize()) {
        if (!back) {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val iconSize = if (size == TileSize.SMALL) maxHeight * 0.58f else maxHeight * 0.4f
                AppIcon(app, iconSize, shape = org.ivanivorontsov.launcher.core.IconShape.ORIGINAL, mono = true)
            }
            if (size != TileSize.SMALL) Text(app.label, color = Color.White, fontFamily = fonts.body, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 6.dp, end = 30.dp))
            if (badge > 0) Text(if (badge > 99) "99+" else badge.toString(), color = Color.White, fontFamily = fonts.body, fontSize = if (size == TileSize.SMALL) 11.sp else 15.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 5.dp))
        } else {
            Column(Modifier.fillMaxSize().padding(10.dp)) {
                Text(app.label, color = Color.White, fontFamily = fonts.heading, fontWeight = FontWeight.Light, fontSize = if (size == TileSize.WIDE) 26.sp else 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.weight(1f))
                Text(if (badge > 0) "новых: $badge" else "нажмите, чтобы открыть", color = Color.White.copy(alpha = 0.85f), fontFamily = fonts.body, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ClockTileContent(size: TileSize, back: Boolean) {
    val fonts = LocalFonts.current
    val t = nowMinute()
    val alarm = remember(t) { LauncherApp.instance.live.nextAlarm() }
    Column(Modifier.fillMaxSize().padding(10.dp)) {
        if (!back) {
            Text(timeText(t), color = Color.White, fontFamily = fonts.clock, fontWeight = FontWeight.Light, fontSize = if (size == TileSize.SMALL) 20.sp else 52.sp, lineHeight = 54.sp)
            Spacer(Modifier.weight(1f))
            if (size != TileSize.SMALL) Text(dateText(t).lowercase(), color = Color.White, fontFamily = fonts.body, fontSize = 13.sp, maxLines = 1)
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(dayNumber(t), color = Color.White, fontFamily = fonts.heading, fontWeight = FontWeight.Light, fontSize = if (size == TileSize.SMALL) 26.sp else 60.sp, lineHeight = 60.sp)
                if (size == TileSize.WIDE) Column(Modifier.padding(start = 12.dp, bottom = 8.dp)) {
                    Text(dayOfWeek(t), color = Color.White, fontFamily = fonts.body, fontSize = 18.sp)
                    Text(monthText(t).substringAfter(' '), color = Color.White.copy(alpha = 0.85f), fontFamily = fonts.body, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            if (size != TileSize.SMALL) Text(alarm?.let { "будильник $it" } ?: "будильников нет", color = Color.White.copy(alpha = 0.85f), fontFamily = fonts.body, fontSize = 12.sp)
        }
    }
}

@Composable
private fun CalendarTileContent(size: TileSize, back: Boolean, permVersion: Int) {
    val fonts = LocalFonts.current
    val live = LauncherApp.instance.live
    val t = nowMinute()
    val ev by produceState<org.ivanivorontsov.launcher.data.LiveSources.Event?>(null, t / (15 * 60_000L), permVersion) { value = live.nextEvent() }
    Column(Modifier.fillMaxSize().padding(10.dp)) {
        if (!back && size != TileSize.SMALL || size == TileSize.SMALL) {
            Text(dayNumber(t), color = Color.White, fontFamily = fonts.heading, fontWeight = FontWeight.Light, fontSize = if (size == TileSize.SMALL) 28.sp else 48.sp, lineHeight = 50.sp)
            if (size != TileSize.SMALL) Text(dayOfWeek(t), color = Color.White, fontFamily = fonts.body, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            if (size != TileSize.SMALL) Text(ev?.title ?: "календарь", color = Color.White, fontFamily = fonts.body, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            Text(ev?.title ?: if (live.hasCalendarAccess()) "событий нет" else "нажмите, чтобы показывать события", color = Color.White, fontFamily = fonts.body, fontSize = 16.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            ev?.let { Text(it.whenText, color = Color.White.copy(alpha = 0.85f), fontFamily = fonts.body, fontSize = 12.sp) }
        }
    }
}

/** Photos tile: user's own photos, slow Ken Burns pan/zoom, flips to the next photo. All on-device. */
@Composable
private fun PhotoTileContent(size: TileSize, visible: Boolean, ui: UiState) {
    val fonts = LocalFonts.current
    val anim = LocalAnimations.current
    val live = LauncherApp.instance.live
    val powerSave by live.powerSave.collectAsStateCompat()
    val access = remember(ui.permissionsVersion) { live.hasPhotoAccess() }
    val uris by produceState(emptyList<Uri>(), access, ui.permissionsVersion) { value = if (access) live.photos() else emptyList() }
    var idx by remember { mutableIntStateOf(0) }
    val img by produceState<ImageBitmap?>(null, uris, idx) { value = uris.takeIf { it.isNotEmpty() }?.let { live.thumbnail(it[idx % it.size]) } }
    val kb = remember { Animatable(0f) }
    val turn = remember { Animatable(0f) }
    val running = visible && ui.resumed && !powerSave && anim && uris.size > 0
    LaunchedEffect(running, uris) {
        if (!running) return@LaunchedEffect
        while (true) {
            kb.snapTo(0f)
            kb.animateTo(1f, tween(8500, easing = LinearEasing))
            turn.animateTo(1f, tween(330, easing = MotionSpecs.turnstileEase))
            idx = (idx + 1) % uris.size.coerceAtLeast(1)
            turn.snapTo(-1f)
            turn.animateTo(0f, tween(380, easing = MotionSpecs.turnstileEase))
        }
    }
    Box(Modifier.fillMaxSize()) {
        val i = img
        if (i != null) {
            val dir = if (idx % 2 == 0) 1f else -1f
            Image(i, null, Modifier.fillMaxSize().graphicsLayer {
                val k = kb.value
                val s = 1.06f + 0.14f * k
                scaleX = s; scaleY = s
                translationX = (k - 0.5f) * this.size.width * 0.06f * dir
                translationY = (0.5f - k) * this.size.height * 0.04f
                rotationX = turn.value * 90f
                cameraDistance = 14f * density
            }, contentScale = ContentScale.Crop)
        } else {
            Canvas(Modifier.size(46.dp).align(Alignment.Center)) {
                val w = this.size.width; val h = this.size.height
                val p = Path().apply { moveTo(0f, h); lineTo(w * 0.35f, h * 0.4f); lineTo(w * 0.6f, h * 0.75f); lineTo(w * 0.75f, h * 0.55f); lineTo(w, h); close() }
                drawPath(p, Color.White); drawCircle(Color.White, this.size.width * 0.1f, Offset(this.size.width * 0.75f, this.size.height * 0.2f))
            }
        }
        if (size != TileSize.SMALL) Text(if (access) "фото" else "фото — нажмите, чтобы разрешить", color = Color.White, fontFamily = fonts.body, fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 6.dp))
    }
}

@Suppress("unused") private fun pullGesture() = GestureAction.NOTIFICATIONS
