package org.ivanivorontsov.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ivanivorontsov.launcher.LauncherApp
import org.ivanivorontsov.launcher.core.Folder
import org.ivanivorontsov.launcher.core.IconShape
import org.ivanivorontsov.launcher.core.ScreenLayout

/** 2×2 preview of the first four apps, in the theme's icon shape. */
@Composable
fun FolderIcon(folder: Folder, size: Dp, modifier: Modifier = Modifier) {
    val spec = LocalSpec.current; val colors = LocalColors.current
    val byKey = LocalAppsByKey.current
    val shape = PathShape(if (spec.iconShape == IconShape.ORIGINAL) IconShape.SQUIRCLE else spec.iconShape)
    Box(modifier.size(size).clip(shape).background(if (colors.dark) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
        val mini = size * 0.34f
        Column(verticalArrangement = Arrangement.spacedBy(size * 0.06f)) {
            folder.apps.take(4).chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(size * 0.06f)) {
                    row.forEach { k -> byKey[k]?.let { AppIcon(it, mini) } ?: Spacer(Modifier.size(mini)) }
                    if (row.size == 1) Spacer(Modifier.size(mini))
                }
            }
        }
    }
}

/** Folder on the home screen or in the dock: tap opens, long press = menu, long press + move = drag. */
@Composable
fun FolderCell(folder: Folder, iconSize: Dp, showLabel: Boolean, source: DragSource, labelColor: Color = LocalColors.current.onWallpaper) {
    val spec = LocalSpec.current
    val ui = LocalUi.current
    val ref = remember { BoundsRef() }
    val iconRef = remember { BoundsRef() }
    val iconPx = with(LocalDensity.current) { iconSize.toPx() }
    RegisterIcon(ui, folder.id, iconRef)
    Column(
        Modifier.trackBounds(ref).launcherItem(
            ui, ref,
            onClick = { ui?.openFolder = folder.id },
            onLongPress = { ui?.let { it.menu = folderMenu(it, folder, ref.bounds() ?: Rect.Zero) } },
            dragItem = { local -> DragItem(folder.id, null, source, size = Size(iconPx, iconPx), grab = iconGrab(local, ref.bounds()?.width ?: iconPx, iconPx), startBounds = iconRef.bounds()) },
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.trackBounds(iconRef)) { FolderIcon(folder, iconSize) }
        if (showLabel) {
            Spacer(Modifier.height(5.dp))
            Text(spec.labelPrefix + spec.withCase(folder.name), style = labelStyle(labelColor), maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center, modifier = Modifier.width(iconSize + 22.dp))
        }
    }
}

fun folderMenu(ui: UiState, folder: Folder, anchor: Rect): MenuState {
    val store = LauncherApp.instance.store
    val theme = ui.themeId
    return MenuState(anchor, title = folder.name, entries = listOf(
        MenuEntry("Открыть") { ui.openFolder = folder.id },
        MenuEntry("Переименовать папку") { ui.renameFolder = folder.id },
        MenuEntry("Разобрать папку") { store.update { c -> c.editLayout(theme) { it.ungroup(folder.id) } } },
        MenuEntry("Удалить с экрана", danger = true) { store.update { c -> c.editLayout(theme) { it.remove(folder.id) } } },
    ))
}

/** Opened folder: dimmed backdrop, card with the apps; drag an app out to put it anywhere. */
@Composable
fun FolderOverlay(ui: UiState, layout: ScreenLayout) {
    val id = ui.openFolder ?: return
    val folder = layout.folders[id]
    if (folder == null) { LaunchedEffect(id) { ui.openFolder = null }; return }
    val spec = LocalSpec.current; val colors = LocalColors.current
    val byKey = LocalAppsByKey.current
    val appear = remember(id) { Animatable(0f) }
    LaunchedEffect(id) { appear.animateTo(1f, if (ui.animations) MotionSpecs.enter(spec.motion, true) else tween(0)) }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f * appear.value.coerceIn(0f, 1f)))
            .pointerInput(id) { detectTapGestures { ui.openFolder = null } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth(0.88f).heightIn(max = 520.dp)
                .graphicsLayer { val a = appear.value; alpha = a.coerceIn(0f, 1f); scaleX = 0.86f + 0.14f * a; scaleY = 0.86f + 0.14f * a }
                .clip(RoundedCornerShape(spec.cornerDp.coerceIn(8, 32).dp))
                .background(colors.surface.copy(alpha = 0.96f))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(16.dp),
        ) {
            Text(spec.withCase(folder.name), style = labelStyle(colors.onSurface, 20.sp).copy(shadow = null, fontFamily = LocalFonts.current.heading),
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clickable(remember { MutableInteractionSource() }, null) { ui.renameFolder = folder.id }.padding(bottom = 12.dp))
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val apps = folder.apps.mapNotNull { byKey[it] }
                apps.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        row.forEach { a -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { AppCell(a, (spec.iconSizeDp - 2).dp, true, labelColor = colors.onSurface, source = DragSource.FOLDER) } }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Text("Потяните иконку, чтобы вынести её из папки", style = labelStyle(colors.onSurface.copy(alpha = 0.55f), 12.sp).copy(shadow = null),
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
}

@Composable
fun FolderRenameDialog(ui: UiState, layout: ScreenLayout) {
    val id = ui.renameFolder ?: return
    val folder = layout.folders[id] ?: run { ui.renameFolder = null; return }
    var text by remember(id) { mutableStateOf(folder.name) }
    AlertDialog(
        onDismissRequest = { ui.renameFolder = null },
        title = { Text("Название папки") },
        text = { OutlinedTextField(text, { text = it }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = {
                val theme = ui.themeId
                LauncherApp.instance.store.update { c -> c.editLayout(theme) { it.renameFolder(id, text) } }
                ui.renameFolder = null
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = { ui.renameFolder = null }) { Text("Отмена") } },
    )
}
