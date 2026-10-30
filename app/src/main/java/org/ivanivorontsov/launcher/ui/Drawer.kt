package org.ivanivorontsov.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.ivanivorontsov.launcher.core.AlphabetIndex
import org.ivanivorontsov.launcher.core.AppCategory
import org.ivanivorontsov.launcher.core.DrawerStyle
import org.ivanivorontsov.launcher.core.HomeLayout
import org.ivanivorontsov.launcher.core.LauncherConfig
import org.ivanivorontsov.launcher.core.SearchEngine
import org.ivanivorontsov.launcher.data.AppInfo
import org.ivanivorontsov.launcher.data.Device

/** Full-screen app drawer that follows the finger. Off-screen when closed, so it never steals touches. */
@Composable
fun DrawerLayer(ui: UiState, apps: List<AppInfo>, config: LauncherConfig, wall: WallImages?) {
    val spec = LocalSpec.current; val colors = LocalColors.current
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val drawer = ui.drawer
    LaunchedEffect(Unit) {
        snapshotFlow { drawer.progress >= 0.999f }.distinctUntilChanged().collect { open ->
            if (open && config.autoKeyboard) try { focus.requestFocus(); keyboard?.show() } catch (_: Exception) {}
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { drawer.progress <= 0.001f }.distinctUntilChanged().collect { closed ->
            if (closed) { query = ""; focusManager.clearFocus(); keyboard?.hide() }
        }
    }
    val nested = remember(drawer) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (drawer.progress < 1f && drawer.progress > 0f && source == NestedScrollSource.UserInput) {
                    drawer.dragBy(-available.y / 1800f); return available
                }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0 && source == NestedScrollSource.UserInput) { drawer.dragBy(-available.y / 1800f); return available }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (drawer.progress < 1f) { drawer.settle(-available.y / 1800f); return available }
                return Velocity.Zero
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().graphicsLayer {
        val p = drawer.progress
        translationY = (1f - p) * size.height
        alpha = (p * 1.6f).coerceAtMost(1f)
    }) {
        // Background: frosted copy of the theme wallpaper (no per-frame blur) or a translucent surface.
        if (spec.blur && wall != null && !wall.tall) {
            Image(wall.frosted, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(colors.surface.copy(alpha = (spec.surfaceAlpha * 0.75f).coerceAtMost(0.85f))))
        } else Box(Modifier.fillMaxSize().background(colors.surface.copy(alpha = spec.surfaceAlpha)))

        Column(Modifier.fillMaxSize().statusBarsPadding().nestedScroll(nested)) {
            val results = remember(query, apps, config.usage) {
                if (query.isBlank()) apps else SearchEngine.search(apps, query, { it.search }, { config.usage[it.key] ?: 0 })
            }
            SearchField(query, { query = it }, focus,
                placeholder = if (spec.drawer == DrawerStyle.LIBRARY) "Библиотека приложений" else "Поиск приложений",
                onGo = { results.firstOrNull()?.let { ui.actions?.launch(it, null) } ?: Device.webSearch(ui.actionsContext(), query) })
            val bottomPad = WindowInsets.imeAnimationTarget.asPaddingValues().calculateBottomPadding().coerceAtLeast(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
            if (query.isNotBlank()) SearchResults(results, query, bottomPad)
            else when (spec.drawer) {
                DrawerStyle.GRID -> GridDrawer(apps, bottomPad)
                DrawerStyle.LIST -> ListDrawer(apps, bottomPad, textOnly = spec.layout == HomeLayout.MINIMAL)
                DrawerStyle.LIBRARY -> LibraryDrawer(apps, config, bottomPad)
                DrawerStyle.TILES_LIST -> TilesListBody(apps, bottomPad)
            }
        }
    }
}

/** 1.4.6: «Поиск» from the home long-press menu: all apps, also when every app lives on the home pages. */
@Composable
fun AppSearchOverlay(ui: UiState, apps: List<AppInfo>, config: LauncherConfig) {
    val colors = LocalColors.current
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { try { focus.requestFocus(); keyboard?.show() } catch (_: Exception) {} }
    val results = remember(query, apps, config.usage) {
        if (query.isBlank()) apps.sortedBy { it.label.lowercase() } else SearchEngine.search(apps, query, { it.search }, { config.usage[it.key] ?: 0 })
    }
    Box(Modifier.fillMaxSize().background(colors.surface.copy(alpha = 0.97f)).pointerInput(Unit) { detectTapGestures { } }) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            SearchField(query, { query = it }, focus, placeholder = "Поиск приложений",
                onGo = { results.firstOrNull()?.let { ui.appSearch = false; ui.actions?.launch(it, null) } ?: Device.webSearch(ui.actionsContext(), query) })
            val bottomPad = WindowInsets.imeAnimationTarget.asPaddingValues().calculateBottomPadding().coerceAtLeast(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
            SearchResults(results, query.ifBlank { "" }, bottomPad)
        }
    }
}

private fun UiState.actionsContext() = org.ivanivorontsov.launcher.LauncherApp.instance

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, focus: FocusRequester, placeholder: String, onGo: () -> Unit) {
    val spec = LocalSpec.current; val colors = LocalColors.current; val fonts = LocalFonts.current
    val shape = if (spec.drawer == DrawerStyle.TILES_LIST) RoundedCornerShape(0.dp) else RoundedCornerShape(spec.cornerDp.coerceAtMost(22).dp)
    Box(
        Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth().height(46.dp).clip(shape)
            .background(colors.onSurface.copy(alpha = if (spec.drawer == DrawerStyle.TILES_LIST) 0.9f else 0.1f)).padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val textColor = if (spec.drawer == DrawerStyle.TILES_LIST) colors.background else colors.onSurface
        if (value.isEmpty()) Text((if (spec.labelPrefix.isNotEmpty()) "$ grep " else "⌕  ") + spec.withCase(placeholder), color = textColor.copy(alpha = 0.55f), fontFamily = fonts.body, fontSize = 16.sp)
        BasicTextField(
            value, onChange, singleLine = true, modifier = Modifier.fillMaxWidth().focusRequester(focus),
            textStyle = androidx.compose.ui.text.TextStyle(color = textColor, fontFamily = fonts.body, fontSize = 17.sp),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onGo = { onGo() }),
        )
    }
}

@Composable
private fun SearchResults(results: List<AppInfo>, query: String, bottom: Dp) {
    val spec = LocalSpec.current; val colors = LocalColors.current
    val ctx = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + 12.dp)) {
        items(results, key = { it.key }) { a -> AppRow(a, highlight = a === results.firstOrNull()) }
        item {
            Text("Найти в интернете: «$query»", style = labelStyle(colors.accent, 16.sp).copy(shadow = null),
                modifier = Modifier.fillMaxWidth().clickable { Device.webSearch(ctx, query) }.padding(horizontal = 24.dp, vertical = 14.dp))
            Text("Найти в магазине приложений", style = labelStyle(colors.onSurface.copy(alpha = 0.7f), 15.sp).copy(shadow = null),
                modifier = Modifier.fillMaxWidth().clickable { Device.marketSearch(ctx, query) }.padding(horizontal = 24.dp, vertical = 12.dp))
        }
    }
    if (results.isEmpty()) Spacer(Modifier.height(1.dp))
    @Suppress("UNUSED_EXPRESSION") spec
}

@Composable
fun AppRow(a: AppInfo, highlight: Boolean = false, textOnly: Boolean = false, iconSize: Dp = 40.dp, source: DragSource = DragSource.DRAWER) {
    val spec = LocalSpec.current; val colors = LocalColors.current; val fonts = LocalFonts.current
    val actions = LocalActions.current
    val ui = LocalUi.current
    val ref = remember(a.key) { BoundsRef() }
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { (if (textOnly) 48.dp else iconSize).toPx() }
    Row(
        Modifier.fillMaxWidth().trackBounds(ref)
            .background(if (highlight) colors.accent.copy(alpha = 0.14f) else Color.Transparent)
            .launcherItem(ui, ref, onClick = { actions.launch(a, ref.bounds()) }, onLongPress = { actions.showMenu(a, null, ref.bounds() ?: Rect.Zero) },
                dragItem = { local -> rowDragItem(a, source, ref, px, textOnly, local) })
            .padding(horizontal = 22.dp, vertical = if (textOnly) 10.dp else 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!textOnly) { AppIcon(a, iconSize); Spacer(Modifier.width(16.dp)) }
        Text(spec.labelPrefix + spec.withCase(a.label), style = labelStyle(colors.onSurface, if (textOnly) (spec.labelSizeSp * 0.85f).sp else 17.sp).copy(fontFamily = if (textOnly) fonts.heading else fonts.body, shadow = if (spec.glow) labelStyle(colors.onSurface).shadow else null),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (a.isWork) Text("  ·  работа", style = labelStyle(colors.onSurface.copy(alpha = 0.5f), 12.sp).copy(shadow = null))
    }
}

@Composable
private fun GridDrawer(apps: List<AppInfo>, bottom: Dp) {
    val spec = LocalSpec.current
    val state = rememberLazyGridState()
    val letters = remember(apps) { firstIndexByLetter(apps) }
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(GridCells.Fixed(spec.columns), state = state, modifier = Modifier.fillMaxSize().padding(end = 18.dp),
            contentPadding = PaddingValues(start = 8.dp, top = 4.dp, bottom = bottom + 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            items(apps, key = { it.key }, contentType = { 0 }) { a ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { AppCell(a, (spec.iconSizeDp - 4).dp, true, labelColor = LocalColors.current.onSurface) }
            }
        }
        FastScroller(letters, Modifier.align(Alignment.CenterEnd)) { idx -> state.scrollToItem(idx) }
    }
}

private fun firstIndexByLetter(apps: List<AppInfo>): List<Pair<String, Int>> {
    val out = ArrayList<Pair<String, Int>>()
    apps.forEachIndexed { i, a -> if (out.isEmpty() || out.last().first != a.letter) out += a.letter to i }
    return out
}

/** Rows list with letter headers + alphabet wave scroller (one-hand friendly). */
@Composable
private fun ListDrawer(apps: List<AppInfo>, bottom: Dp, textOnly: Boolean) {
    val spec = LocalSpec.current; val colors = LocalColors.current; val fonts = LocalFonts.current
    val state = rememberLazyListState()
    val groups = remember(apps) { apps.groupBy { it.letter }.toList() }
    val headerIndex = remember(groups) {
        var i = 0; groups.map { (l, items) -> (l to i).also { i += items.size + 1 } }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = state, modifier = Modifier.fillMaxSize().padding(end = 22.dp), contentPadding = PaddingValues(bottom = bottom + 16.dp)) {
            groups.forEach { (letter, items) ->
                item(key = "h_$letter", contentType = 1) {
                    Text(if (spec.labelPrefix.isNotEmpty()) "# ${letter.lowercase()}" else letter, style = labelStyle(colors.accent, 14.sp).copy(fontFamily = fonts.heading, fontWeight = FontWeight.Bold, shadow = null),
                        modifier = Modifier.padding(start = 24.dp, top = 14.dp, bottom = 2.dp))
                }
                items(items, key = { it.key }, contentType = { 2 }) { a -> AppRow(a, textOnly = textOnly) }
            }
        }
        FastScroller(headerIndex, Modifier.align(Alignment.CenterEnd)) { idx -> state.scrollToItem(idx) }
    }
}

/**
 * Alphabet scroller: touch or slide; letters near the finger swell and lean out (a “wave”),
 * a bubble shows the current letter, haptic tick on change. Reads touch state only in draw/layer
 * lambdas, so sliding never recomposes the list.
 */
@Composable
fun FastScroller(letters: List<Pair<String, Int>>, modifier: Modifier = Modifier, scrollTo: suspend (Int) -> Unit) {
    if (letters.size < 2) return
    val colors = LocalColors.current; val fonts = LocalFonts.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var touchY by remember { mutableFloatStateOf(-1f) }
    var current by remember { mutableStateOf<String?>(null) }
    var heightPx by remember { mutableFloatStateOf(1f) }
    fun pick(y: Float) {
        val i = ((y / heightPx) * letters.size).toInt().coerceIn(0, letters.size - 1)
        val l = letters[i]
        if (current != l.first) { current = l.first; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); scope.launch { scrollTo(l.second) } }
    }
    Box(modifier.fillMaxHeight(0.82f).width(44.dp)) {
        Column(
            Modifier.fillMaxHeight().width(28.dp).align(Alignment.CenterEnd)
                .androidxOnSize { heightPx = it }
                .pointerInput(letters) {
                    detectTapGestures(onPress = { o -> touchY = o.y; pick(o.y); tryAwaitRelease(); touchY = -1f; current = null })
                }
                .pointerInput(letters) {
                    detectVerticalDragGestures(onDragStart = { o -> touchY = o.y; pick(o.y) }, onDragEnd = { touchY = -1f; current = null }, onDragCancel = { touchY = -1f; current = null }) { ch, _ ->
                        touchY = ch.position.y.coerceIn(0f, heightPx); pick(touchY)
                    }
                },
            verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            letters.forEachIndexed { i, (l, _) ->
                Text(l, fontSize = 10.sp, fontFamily = fonts.body, color = colors.onSurface.copy(alpha = 0.7f), modifier = Modifier.graphicsLayer {
                    val ty = touchY
                    if (ty >= 0f) {
                        val center = (i + 0.5f) / letters.size * heightPx
                        val d = ((center - ty) / (heightPx * 0.12f))
                        val k = kotlin.math.exp(-(d * d)).toFloat()
                        translationX = -k * 26.dp.toPx(); scaleX = 1f + k * 0.8f; scaleY = 1f + k * 0.8f
                    }
                })
            }
        }
        current?.let {
            Box(Modifier.align(Alignment.CenterStart).offset(x = (-70).dp).size(64.dp).clip(RoundedCornerShape(50)).background(colors.accent), contentAlignment = Alignment.Center) {
                Text(it, fontSize = 30.sp, color = Color.White, fontFamily = fonts.heading)
            }
        }
    }
}

private fun Modifier.androidxOnSize(f: (Float) -> Unit) = this.onSizeChanged { f(it.height.toFloat()) }

/** “Library”: apps grouped into category folders (3 big icons + a 2×2 cluster), suggestions first. */
@Composable
private fun LibraryDrawer(apps: List<AppInfo>, config: LauncherConfig, bottom: Dp) {
    val spec = LocalSpec.current; val colors = LocalColors.current
    var open by remember { mutableStateOf<AppCategory?>(null) }
    val groups = remember(apps, config.usage) {
        val suggested = apps.filter { (config.usage[it.key] ?: 0) > 0 }.sortedByDescending { config.usage[it.key] ?: 0 }.take(8)
        val m = LinkedHashMap<AppCategory, List<AppInfo>>()
        if (suggested.size >= 4) m[AppCategory.SUGGESTED] = suggested
        AppCategory.entries.filter { it != AppCategory.SUGGESTED }.forEach { c ->
            val l = apps.filter { it.category == c }.sortedByDescending { config.usage[it.key] ?: 0 }
            if (l.isNotEmpty()) m[c] = l
        }
        m.toList()
    }
    val oc = open
    if (oc != null) {
        val list = groups.firstOrNull { it.first == oc }?.second.orEmpty()
        Column(Modifier.fillMaxSize()) {
            Text("‹  " + oc.title, style = labelStyle(colors.onSurface, 20.sp).copy(fontFamily = LocalFonts.current.heading, shadow = null),
                modifier = Modifier.fillMaxWidth().clickable { open = null }.padding(horizontal = 22.dp, vertical = 12.dp))
            LazyVerticalGrid(GridCells.Fixed(spec.columns), Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp, 4.dp, 8.dp, bottom + 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                items(list, key = { it.key }) { a -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { AppCell(a, spec.iconSizeDp.dp, true, labelColor = colors.onSurface) } }
            }
        }
        androidx.activity.compose.BackHandler { open = null }
        return
    }
    LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 6.dp, 18.dp, bottom + 18.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items(groups, key = { it.first.name }) { (cat, list) ->
            Column {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(spec.cornerDp.coerceAtMost(28).dp))
                    .background(if (colors.dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f)).padding(12.dp)) {
                    Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
                        for (r in 0..1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            for (c in 0..1) {
                                val i = r * 2 + c
                                Box(Modifier.weight(1f).aspectRatio(1f).padding(5.dp), contentAlignment = Alignment.Center) {
                                    if (i < 3 || list.size == 4) list.getOrNull(i)?.let { AppCellIconOnly(it) }
                                    else if (list.size > 4) MiniCluster(list.drop(3).take(4)) { open = cat }
                                }
                            }
                        }
                    }
                }
                Text(cat.title, style = labelStyle(colors.onSurface.copy(alpha = 0.85f), 12.sp).copy(shadow = null), maxLines = 1,
                    modifier = Modifier.padding(top = 6.dp).align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
private fun AppCellIconOnly(a: AppInfo) {
    val actions = LocalActions.current
    val ref = remember(a.key) { BoundsRef() }
    val ui = LocalUi.current
    BoxWithConstraints(Modifier.fillMaxSize().trackBounds(ref).launcherItem(ui, ref, { actions.launch(a, ref.bounds()) }, { actions.showMenu(a, null, ref.bounds() ?: Rect.Zero) },
        dragItem = { local -> val w = ref.bounds()?.width ?: 1f; DragItem(a.key, a, DragSource.DRAWER, size = androidx.compose.ui.geometry.Size(w, w), grab = local, startBounds = ref.bounds()) }), contentAlignment = Alignment.Center) {
        AppIcon(a, maxWidth)
    }
}

@Composable
private fun MiniCluster(apps: List<AppInfo>, onClick: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().pressFeedback(onClick, null)) {
        val s = maxWidth / 2 - 3.dp
        Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
            for (r in 0..1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (c in 0..1) apps.getOrNull(r * 2 + c)?.let { AppIcon(it, s) } ?: Spacer(Modifier.size(s))
            }
        }
    }
}

// ------------------------------------------------- tile-style list with letter squares and jump grid

@Composable
fun TilesListBody(apps: List<AppInfo>, bottom: Dp) {
    val spec = LocalSpec.current; val colors = LocalColors.current; val fonts = LocalFonts.current
    val anim = LocalAnimations.current
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var jump by remember { mutableStateOf(false) }
    val groups = remember(apps) { AlphabetIndex.sortAndGroup(apps) { it.label } }
    val headerIndex = remember(groups) { var i = 0; groups.associate { (l, items) -> (l to i).also { i += items.size + 1 } } }
    val enter = remember { Animatable(1f) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 12.dp, top = 6.dp, bottom = bottom + 24.dp)) {
            groups.forEach { (letter, items) ->
                item(key = "h_$letter", contentType = 1) {
                    Box(Modifier.padding(vertical = 6.dp).size(52.dp).background(colors.accent).clickable { jump = true; scope.launch { enter.snapTo(0f); enter.animateTo(1f, MotionSpecs.enter(spec.motion, anim)) } }.padding(5.dp), contentAlignment = Alignment.BottomStart) {
                        Text(letter.lowercase(), color = Color.White, fontFamily = fonts.heading, fontWeight = FontWeight.Light, fontSize = 30.sp, lineHeight = 30.sp)
                    }
                }
                items(items, key = { it.key }, contentType = { 2 }) { a -> TileListRow(a) }
            }
        }
        if (jump) JumpGrid(groups.map { it.first }.toSet(), enter.value, onPick = { l ->
            jump = false; headerIndex[l]?.let { scope.launch { state.scrollToItem(it) } }
        }, onDismiss = { jump = false })
    }
}

@Composable
private fun TileListRow(a: AppInfo) {
    val spec = LocalSpec.current; val colors = LocalColors.current; val fonts = LocalFonts.current
    val actions = LocalActions.current
    val ref = remember(a.key) { BoundsRef() }
    val ui = LocalUi.current
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { 52.dp.toPx() }
    Row(Modifier.fillMaxWidth().trackBounds(ref).launcherItem(ui, ref, { actions.launch(a, ref.bounds()) }, { actions.showMenu(a, null, ref.bounds() ?: Rect.Zero) },
        dragItem = { local -> rowDragItem(a, DragSource.LIST, ref, px, false, local) }, tiltOverride = true)
        .padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).background(if (spec.tileFill == org.ivanivorontsov.launcher.core.TileFill.ICON_COLOR) (org.ivanivorontsov.launcher.LauncherApp.instance.icons.color(a.key)?.let { Color(it) } ?: colors.accent) else colors.accent), contentAlignment = Alignment.Center) {
            AppIcon(a, 34.dp, shape = org.ivanivorontsov.launcher.core.IconShape.ORIGINAL, mono = true)
        }
        Spacer(Modifier.width(14.dp))
        Text(a.label, color = colors.onSurface, fontFamily = fonts.body, fontSize = 20.sp, fontWeight = FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Letter jump grid: every letter as a square, letters without apps dimmed; squares swing in like a turnstile. */
@Composable
private fun JumpGrid(present: Set<String>, appear: Float, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalColors.current; val fonts = LocalFonts.current
    val cells = remember(present) { AlphabetIndex.jumpGrid(present) }
    androidx.activity.compose.BackHandler { onDismiss() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f * appear.coerceIn(0f, 1f))).pointerInput(Unit) { detectTapGestures { onDismiss() } }) {
        LazyVerticalGrid(GridCells.Fixed(4), Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 40.dp, 20.dp, 40.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(cells.size) { i ->
                val (l, ok) = cells[i]
                val row = i / 4
                Box(Modifier.aspectRatio(1f).graphicsLayer {
                    val local = ((appear * 1.6f) - row * 0.04f).coerceIn(0f, 1f)
                    rotationY = (1f - local) * -75f; transformOrigin = TransformOrigin(0f, 0.5f); alpha = local; cameraDistance = 10f * density
                }.background(if (ok) colors.accent else Color(0xFF2B2B2B)).clickable(enabled = ok) { onPick(l) }.padding(6.dp), contentAlignment = Alignment.BottomStart) {
                    Text(l.lowercase(), color = if (ok) Color.White else Color(0xFF5E5E5E), fontFamily = fonts.heading, fontWeight = FontWeight.Light, fontSize = 30.sp)
                }
            }
        }
    }
}


/** Drag item for a list row: the floating copy is the app icon (or its label in text-only themes) under the finger. */
fun rowDragItem(a: AppInfo, source: DragSource, ref: BoundsRef, px: Float, asLabel: Boolean, local: androidx.compose.ui.geometry.Offset): DragItem =
    if (asLabel) DragItem(a.key, a, source, size = androidx.compose.ui.geometry.Size(px * 3, px), grab = androidx.compose.ui.geometry.Offset(px * 0.6f, px * 0.4f), asLabel = true, startBounds = ref.bounds())
    else DragItem(a.key, a, source, size = androidx.compose.ui.geometry.Size(px, px), grab = androidx.compose.ui.geometry.Offset(px / 2, px / 2), startBounds = ref.bounds())
