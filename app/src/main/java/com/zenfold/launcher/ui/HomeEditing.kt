package com.zenfold.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenfold.launcher.AppEntry
import com.zenfold.launcher.GridPos
import com.zenfold.launcher.style.CustomStyle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild

/** An icon or folder being dragged on Home, with the finger's position in root coordinates. */
internal data class HomeDrag(val from: GridPos, val isFolder: Boolean, val pointer: androidx.compose.ui.geometry.Offset)

internal enum class DropZone { REMOVE, UNINSTALL, PREVIOUS_PAGE, NEXT_PAGE }

/** Long-press on empty Home space: the way into editing it, like every other launcher. */
@Composable
internal fun HomeMenuSheet(
    style: CustomStyle,
    hazeState: HazeState,
    onAddApps: () -> Unit,
    onWidgets: () -> Unit,
    onWallpapers: () -> Unit,
    onSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val appear = rememberAppearAnimation()
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = appear.value.coerceIn(0f, 1f) }
            .background(Color.Black.copy(alpha = 0.25f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            Modifier
                .navigationBarsPadding()
                .padding(20.dp)
                .fillMaxWidth()
                .graphicsLayer { translationY = (1f - appear.value) * 240f }
                .glass(hazeState, RoundedCornerShape(28.dp))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MenuTile(Icons.Filled.Apps, "Add apps", style, onAddApps)
            MenuTile(Icons.Filled.Widgets, "Widgets", style, onWidgets)
            MenuTile(Icons.Filled.Wallpaper, "Wallpapers", style, onWallpapers)
            MenuTile(Icons.Filled.Settings, "Settings", style, onSettings)
        }
    }
}

@Composable
private fun MenuTile(icon: ImageVector, label: String, style: CustomStyle, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(52.dp)
                .background(style.accent.copy(alpha = 0.22f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = style.onBackground, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = style.onBackground)
    }
}

/** Every app with a checkbox: ticked = on Home. The one obvious place to add or remove apps. */
@Composable
internal fun AppPickerOverlay(
    apps: List<AppEntry>,
    onHome: Set<String>,
    style: CustomStyle,
    hazeState: HazeState,
    onToggle: (AppEntry, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(apps, query) {
        val q = query.trim()
        if (q.isEmpty()) apps else apps.filter { it.label.contains(q, ignoreCase = true) }
    }
    val appear = rememberAppearAnimation()

    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = (1f - appear.value) * 320f
                alpha = appear.value.coerceIn(0f, 1f)
            }
            .hazeChild(state = hazeState, shape = RectangleShape)
            .background(style.background.copy(alpha = 0.6f))
            .pointerInput(Unit) { detectTapGestures { } }
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Apps on Home",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = style.onBackground,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) { Text("Done", color = style.accent, fontWeight = FontWeight.SemiBold) }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .glassTint(RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = style.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("Search apps", fontSize = 14.sp, color = style.onSurfaceVariant)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, color = style.onBackground),
                    cursorBrush = SolidColor(style.accent),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(shown, key = { it.packageName }) { app ->
                val checked = app.packageName in onHome
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onToggle(app, !checked) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIconImage(app, style, Modifier.size(40.dp).clip(style.iconShapeKind.shape))
                    Spacer(Modifier.width(14.dp))
                    Text(app.label, fontSize = 15.sp, color = style.onBackground, modifier = Modifier.weight(1f))
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { onToggle(app, it) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = style.accent,
                            uncheckedColor = style.onSurfaceVariant,
                            checkmarkColor = style.background
                        )
                    )
                }
            }
        }
    }
}

/** Pixel/One UI-style targets that appear while dragging: remove, uninstall, or another page. */
@Composable
internal fun DropTargets(drag: HomeDrag?, zone: DropZone?, style: CustomStyle) {
    drag ?: return
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 12.dp, start = 20.dp, end = 20.dp)
                .height(56.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DropPill("Remove", Icons.Filled.Close, active = zone == DropZone.REMOVE, style, Modifier.weight(1f))
            if (!drag.isFolder) {
                DropPill("Uninstall", Icons.Filled.Delete, active = zone == DropZone.UNINSTALL, style, Modifier.weight(1f))
            }
        }
        if (drag.from.page > 0) {
            EdgeHint(Icons.AutoMirrored.Filled.KeyboardArrowLeft, zone == DropZone.PREVIOUS_PAGE, style, Modifier.align(Alignment.CenterStart))
        }
        EdgeHint(Icons.AutoMirrored.Filled.KeyboardArrowRight, zone == DropZone.NEXT_PAGE, style, Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun DropPill(label: String, icon: ImageVector, active: Boolean, style: CustomStyle, modifier: Modifier) {
    Row(
        modifier
            .fillMaxHeight()
            .background(
                if (active) Color(0xFFE5484D).copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.35f),
                RoundedCornerShape(20.dp)
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = style.onBackground, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = style.onBackground)
    }
}

@Composable
private fun EdgeHint(icon: ImageVector, active: Boolean, style: CustomStyle, modifier: Modifier) {
    Box(
        modifier
            .width(28.dp)
            .height(160.dp)
            .background(style.accent.copy(alpha = if (active) 0.6f else 0.18f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = style.onBackground)
    }
}

/** 0 → 1 with a soft spring the first time a sheet or overlay is shown. */
@Composable
internal fun rememberAppearAnimation(): Animatable<Float, AnimationVector1D> {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
    }
    return appear
}

@Composable
internal fun PageIndicator(count: Int, current: Int, style: CustomStyle, modifier: Modifier = Modifier) {
    if (count < 2) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { index ->
            Box(
                Modifier
                    .size(if (index == current) 8.dp else 6.dp)
                    .background(
                        style.onBackground.copy(alpha = if (index == current) 0.9f else 0.35f),
                        CircleShape
                    )
            )
        }
    }
}
