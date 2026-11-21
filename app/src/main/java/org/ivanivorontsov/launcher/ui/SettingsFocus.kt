package org.ivanivorontsov.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import kotlinx.coroutines.delay
import org.ivanivorontsov.launcher.BuildConfig
import org.ivanivorontsov.launcher.core.SettingsAnchors
import org.ivanivorontsov.launcher.core.SettingsIndex

/**
 * Search-focus state for settings: expand the group first, scroll the LazyColumn item
 * into view, then pulse-highlight the exact row for [SettingsAnchors.HIGHLIGHT_MS].
 */
class SettingsFocusController {
    var targetId by mutableStateOf<String?>(null)
        private set
    var nonce by mutableIntStateOf(0)
        private set
    private val expanded: SnapshotStateList<String> = mutableStateListOf()

    fun isGroupExpanded(group: String): Boolean = group in expanded

    fun focus(entry: SettingsIndex.Entry) {
        val t = SettingsAnchors.resolve(entry.id, includePlayRestricted = !BuildConfig.PLAY)
            ?: SettingsAnchors.resolve(entry)
        expanded.clear()
        t?.group?.let { expanded.add(it) }
        targetId = entry.id
        nonce++
    }

    fun consume(id: String) {
        if (targetId == id) targetId = null
    }
}

val LocalSettingsFocus = staticCompositionLocalOf { SettingsFocusController() }

@Composable
fun rememberAnchoredListState(screen: String): LazyListState {
    val focus = LocalSettingsFocus.current
    val state = rememberLazyListState()
    val includeRestricted = !BuildConfig.PLAY
    LaunchedEffect(focus.nonce, focus.targetId, screen) {
        val id = focus.targetId ?: return@LaunchedEffect
        val target = SettingsAnchors.resolve(id, includeRestricted) ?: return@LaunchedEffect
        if (target.screen != screen) return@LaunchedEffect
        val order = SettingsAnchors.lazyOrder(includeRestricted)[screen] ?: return@LaunchedEffect
        val index = order.indexOf(target.lazyKey)
        if (index >= 0) state.animateScrollToItem(index)
    }
    return state
}

@Composable
fun SettingsAnchor(vararg ids: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val focus = LocalSettingsFocus.current
    val requester = remember { BringIntoViewRequester() }
    val highlight = remember { Animatable(0f) }
    val accent = MaterialTheme.colorScheme.primary
    val id = focus.targetId
    val mine = id != null && SettingsAnchors.covers(ids, id)
    LaunchedEffect(focus.nonce, id) {
        if (!mine) return@LaunchedEffect
        delay(40)
        requester.bringIntoView()
        highlight.snapTo(0f)
        highlight.animateTo(1f, tween(180))
        highlight.animateTo(0.4f, tween(280))
        highlight.animateTo(0.9f, tween(280))
        highlight.animateTo(0f, tween(760)) // 180+280+280+760 = 1500 ms
        if (id != null) focus.consume(id)
    }
    val a = highlight.value
    // Column, never Box: a search row is title + description + button. Box would
    // stack them at the same origin (the overlap on «Главный экран» at large font).
    Column(
        modifier
            .fillMaxWidth()
            .bringIntoViewRequester(requester)
            .background(accent.copy(alpha = a * 0.32f), RoundedCornerShape(16.dp)),
    ) { content() }
}
