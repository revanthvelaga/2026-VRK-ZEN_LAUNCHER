package com.zenfold.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.zenfold.launcher.AppEntry
import com.zenfold.launcher.notifications.ZenFoldNotificationListener
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenfold.launcher.home.GridSpec
import com.zenfold.launcher.icons.installedIconPacks
import com.zenfold.launcher.system.LockScreenService
import com.zenfold.launcher.style.AccentSwatches
import com.zenfold.launcher.style.CustomStyle
import com.zenfold.launcher.style.ClockDesign
import com.zenfold.launcher.style.WallpaperArt
import androidx.compose.runtime.saveable.rememberSaveable
import com.zenfold.launcher.style.FontScale
import com.zenfold.launcher.style.IconShapeKind
import com.zenfold.launcher.style.IconSize
import com.zenfold.launcher.style.IconStyle
import com.zenfold.launcher.style.StatusBarStyle
import com.zenfold.launcher.style.StylePresets
import com.zenfold.launcher.widgets.WidgetType
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.HazeState

@Composable
fun SettingsScreen(
    style: CustomStyle,
    apps: List<AppEntry>,
    onApplyStudioTheme: (CustomStyle, Boolean, Boolean) -> Unit,
    enabledWidgets: Set<WidgetType>,
    hiddenApps: List<AppEntry>,
    onUnhideApp: (String) -> Unit,
    gridSpec: GridSpec,
    onGridSpecChange: (GridSpec) -> Unit,
    iconPack: String?,
    onIconPackChange: (String?) -> Unit,
    phoneWallpaper: Boolean,
    onPhoneWallpaperChange: (Boolean) -> Unit,
    onApplyPreset: (CustomStyle) -> Unit,
    onChange: (CustomStyle) -> Unit,
    onWidgetToggle: (WidgetType, Boolean) -> Unit,
    onDone: () -> Unit
) {
    val hazeState = remember { HazeState() }
    var studioOpen by rememberSaveable { mutableStateOf(false) }
    if (studioOpen) {
        ThemeStudio(style, apps, onApplyStudioTheme, { studioOpen = false })
        return
    }

    Box(Modifier.fillMaxSize()) {
        // Settings is an ordinary window (no windowShowWallpaper), so it keeps the gradient.
        Wallpaper(style, hazeState, phoneWallpaper = false, modifier = Modifier.fillMaxSize())
        LazyColumn(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(top = 16.dp, start = 20.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Make it yours", fontSize = 22.sp, color = style.onBackground)
                    TextButton(onClick = onDone) { Text("Done") }
                }
            }

            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(style.surface).clickable { studioOpen = true }.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("DESIGN STUDIO", color = style.accent, fontSize = 12.sp)
                    Text("A home that feels like you.", color = style.onBackground, fontSize = 25.sp)
                    Text("Four complete looks. Wallpapers, icons and clocks, designed together.", color = style.onSurfaceVariant, fontSize = 14.sp)
                    Text("Explore themes →", color = style.accent, fontSize = 14.sp)
                }
            }
            item {
                SettingsSection(style, "Clock design") {
                    SegmentedRow(style, ClockDesign.entries.toList(), style.clockDesign, { it.label }) {
                        onChange(style.copy(clockDesign = it))
                    }
                }
            }
            item {
                SettingsSection(style, "Presets") {
                    StylePresets.presets.forEach { (launcherStyle, preset) ->
                        PresetRow(launcherStyle.displayName, launcherStyle.tagline, preset, style, hazeState, onApplyPreset)
                    }
                }
            }

            item {
                SettingsSection(style, "Accent color") {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AccentSwatches.all.forEach { color ->
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable { onChange(style.copy(accent = color)) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (color == style.accent) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = style.background)
                                }
                            }
                        }
                    }
                }
            }

            item {
                SettingsSection(style, "Icon shape") {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconShapeKind.entries.forEach { kind ->
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(kind.shape)
                                    .background(
                                        if (kind == style.iconShapeKind) style.accent else style.surface
                                    )
                                    .clickable { onChange(style.copy(iconShapeKind = kind)) }
                            )
                        }
                    }
                }
            }

            item {
                SettingsSection(style, "Icon style") {
                    SegmentedRow(style, IconStyle.entries.toList(), style.iconStyle, { it.label }) {
                        onChange(style.copy(iconStyle = it))
                    }
                }
            }

            item {
                SettingsSection(style, "Icon size") {
                    SegmentedRow(style, IconSize.entries.toList(), style.iconSize, { it.label }) {
                        onChange(style.copy(iconSize = it))
                    }
                }
            }

            item {
                SettingsSection(style, "Font size") {
                    SegmentedRow(style, FontScale.entries.toList(), style.fontScale, { it.label }) {
                        onChange(style.copy(fontScale = it))
                    }
                }
            }

            item {
                SettingsSection(style, "Home screen") {
                    ChoiceRow(style, "Home + app drawer", !style.showAppPages) {
                        onChange(style.copy(showAppPages = false))
                    }
                    ChoiceRow(style, "Home + app pages + drawer", style.showAppPages) {
                        onChange(style.copy(showAppPages = true))
                    }
                    Text("Switch layouts without losing your home icons or folders.", fontSize = 12.sp, color = style.onSurfaceVariant)
                    ToggleRow(style, "Search button above dock", style.showSearchPill) {
                        onChange(style.copy(showSearchPill = it))
                    }
                    ToggleRow(style, "Labels under icons", style.showHomeLabels) {
                        onChange(style.copy(showHomeLabels = it))
                    }
                }
            }

            item {
                SettingsSection(style, "App drawer & gestures") {
                    Text("Apps per row", fontSize = 13.sp, color = style.onBackground)
                    SegmentedRow(style, listOf(3, 4, 5), style.drawerColumns, { "$it" }) {
                        onChange(style.copy(drawerColumns = it))
                    }
                    ToggleRow(style, "Keyboard on swipe up", style.searchOnSwipe) {
                        onChange(style.copy(searchOnSwipe = it))
                    }
                    ToggleRow(style, "Swipe down to search", style.swipeDownSearch) {
                        onChange(style.copy(swipeDownSearch = it))
                    }
                    Text("When off, swiping down opens notifications.", fontSize = 12.sp, color = style.onSurfaceVariant)
                }
            }

            item {
                SettingsSection(style, "Status bar") {
                    SegmentedRow(
                        style,
                        StatusBarStyle.entries.toList(),
                        style.statusBarStyle,
                        { it.label }
                    ) { onChange(style.copy(statusBarStyle = it)) }
                }
            }

            item {
                SettingsSection(style, "Widgets") {
                    WidgetType.entries.forEach { widget ->
                        ToggleRow(style, widget.title, widget in enabledWidgets) { checked ->
                            onWidgetToggle(widget, checked)
                        }
                    }
                }
            }

            item {
                SettingsSection(style, "Home grid") {
                    Text("Columns", fontSize = 13.sp, color = style.onBackground)
                    SegmentedRow(style, listOf(4, 5), gridSpec.columns, { "$it" }) {
                        onGridSpecChange(gridSpec.copy(columns = it))
                    }
                    Text("Rows per page", fontSize = 13.sp, color = style.onBackground)
                    SegmentedRow(style, listOf(4, 5, 6), gridSpec.rows, { "$it" }) {
                        onGridSpecChange(gridSpec.copy(rows = it))
                    }
                    Text(
                        "Apps that no longer fit move to the next free spot — nothing is removed.",
                        fontSize = 12.sp,
                        color = style.onSurfaceVariant
                    )
                }
            }

            item {
                SettingsSection(style, "Wallpaper") {
                    SegmentedRow(style, WallpaperArt.entries.toList(), style.wallpaperArt, { it.label }) {
                        onChange(style.copy(wallpaperArt = it))
                        onPhoneWallpaperChange(false)
                    }
                    val context = LocalContext.current
                    ChoiceRow(style, "Phone wallpaper", selected = phoneWallpaper) { onPhoneWallpaperChange(true) }
                    ChoiceRow(style, "ZenFold artwork (this style's colors)", selected = !phoneWallpaper) { onPhoneWallpaperChange(false) }
                    TextButton(onClick = {
                        onPhoneWallpaperChange(true)
                        openWallpaperPicker(context)
                    }) { Text("Change wallpaper") }
                }
            }

            item {
                SettingsSection(style, "Icon pack") {
                    val context = LocalContext.current
                    val packs = remember { installedIconPacks(context) }
                    ChoiceRow(style, "App icons (default)", selected = iconPack == null) { onIconPackChange(null) }
                    packs.forEach { pack ->
                        ChoiceRow(style, pack.label, selected = iconPack == pack.packageName) { onIconPackChange(pack.packageName) }
                    }
                    Text(
                        if (packs.isEmpty()) {
                            "No icon packs installed. Get one from the Play Store — search “icon pack”."
                        } else {
                            "Pack icons replace crystal tiles for the apps the pack covers."
                        },
                        fontSize = 12.sp,
                        color = style.onSurfaceVariant
                    )
                }
            }

            item {
                SettingsSection(style, "Double-tap to lock") {
                    SystemAccessRow(
                        style,
                        onText = "On — double-tap an empty spot on Home to turn the screen off",
                        offText = "Off — needs ZenFold's accessibility switch",
                        isEnabled = { LockScreenService.isEnabled(it) },
                        openSystemSettings = { LockScreenService.openSettings(it) }
                    )
                }
            }

            item {
                SettingsSection(style, "Notification dots") {
                    SystemAccessRow(
                        style,
                        onText = "On — apps with unread notifications show a dot",
                        offText = "Off — needs notification access",
                        isEnabled = { ZenFoldNotificationListener.isEnabled(it) },
                        openSystemSettings = { ZenFoldNotificationListener.openSettings(it) }
                    )
                }
            }

            item {
                SettingsSection(style, "Hidden apps") {
                    if (hiddenApps.isEmpty()) {
                        Text(
                            "None. Long-press an app in the app drawer and choose Hide app.",
                            fontSize = 12.sp,
                            color = style.onSurfaceVariant
                        )
                    }
                    hiddenApps.forEach { app ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            AppIconImage(app, style, Modifier.size(36.dp).clip(style.iconShapeKind.shape))
                            Spacer(Modifier.width(12.dp))
                            Text(app.label, fontSize = 14.sp, color = style.onBackground, modifier = Modifier.weight(1f))
                            TextButton(onClick = { onUnhideApp(app.packageName) }) { Text("Unhide") }
                        }
                    }
                }
            }

        }
    }
}

// For features that need a permission Android only lets the user grant by hand in system
// Settings (notification access, accessibility): shows whether it's on, re-checked every
// time the user comes back here, with a button to the right system screen.
@Composable
private fun SystemAccessRow(
    style: CustomStyle,
    onText: String,
    offText: String,
    isEnabled: (Context) -> Boolean,
    openSystemSettings: (Context) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var enabled by remember { mutableStateOf(isEnabled(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) enabled = isEnabled(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (enabled) onText else offText,
            fontSize = 14.sp,
            color = style.onBackground,
            modifier = Modifier.weight(1f)
        )
        if (!enabled) {
            TextButton(onClick = { openSystemSettings(context) }) { Text("Turn on") }
        }
    }
}

@Composable
private fun ChoiceRow(style: CustomStyle, label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = style.onBackground, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = style.accent)
    }
}

@Composable
private fun SettingsSection(style: CustomStyle, title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, fontSize = 13.sp, color = style.onSurfaceVariant)
        content()
    }
}

@Composable
private fun PresetRow(
    name: String,
    tagline: String,
    preset: CustomStyle,
    current: CustomStyle,
    hazeState: HazeState,
    onApply: (CustomStyle) -> Unit
) {
    val selected = preset.copy(
        showAppPages = current.showAppPages,
        drawerColumns = current.drawerColumns,
        searchOnSwipe = current.searchOnSwipe,
        swipeDownSearch = current.swipeDownSearch,
        showSearchPill = current.showSearchPill
    ) == current
    val rowShape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .glass(hazeState, rowShape)
            .background(if (selected) Color.White.copy(alpha = 0.10f) else Color.Transparent)
            .clickable { onApply(preset) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(36.dp).clip(preset.iconShapeKind.shape).background(preset.accent))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 15.sp, color = preset.onBackground)
            Text(tagline, fontSize = 12.sp, color = preset.onSurfaceVariant)
        }
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = preset.accent)
        }
    }
}

@Composable
private fun <T> SegmentedRow(
    style: CustomStyle,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isSelected) style.accent else style.surface.copy(alpha = 0.5f))
                    .clickable { onSelect(option) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    label(option),
                    fontSize = 13.sp,
                    color = if (isSelected) style.background else style.onBackground
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(style: CustomStyle, label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = style.onBackground, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
