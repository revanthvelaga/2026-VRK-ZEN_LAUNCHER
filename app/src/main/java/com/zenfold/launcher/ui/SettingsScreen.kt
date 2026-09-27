package com.zenfold.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenfold.launcher.style.AccentSwatches
import com.zenfold.launcher.style.CustomStyle
import com.zenfold.launcher.style.FontScale
import com.zenfold.launcher.style.IconShapeKind
import com.zenfold.launcher.style.IconSize
import com.zenfold.launcher.style.StatusBarStyle
import com.zenfold.launcher.style.StylePresets
import com.zenfold.launcher.widgets.WidgetType
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.HazeState

@Composable
fun SettingsScreen(
    style: CustomStyle,
    enabledWidgets: Set<WidgetType>,
    onApplyPreset: (CustomStyle) -> Unit,
    onChange: (CustomStyle) -> Unit,
    onWidgetToggle: (WidgetType, Boolean) -> Unit,
    onDone: () -> Unit
) {
    val hazeState = remember { HazeState() }

    Box(Modifier.fillMaxSize()) {
        Wallpaper(style, hazeState, Modifier.fillMaxSize())
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
                    Text("Launcher style", fontSize = 22.sp, color = style.onBackground)
                    TextButton(onClick = onDone) { Text("Done") }
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
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    ToggleRow(style, "Labels under icons", style.showHomeLabels) {
                        onChange(style.copy(showHomeLabels = it))
                    }
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
        }
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
    val selected = preset == current
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        Text(label, fontSize = 14.sp, color = style.onBackground)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
