package com.zenfold.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import com.zenfold.launcher.style.CustomStyle
import com.zenfold.launcher.style.StylePresets

@Composable
fun ZenFoldTheme(style: CustomStyle = StylePresets.default, content: @Composable () -> Unit) {
    val colorScheme = darkColorScheme(
        primary = style.accent,
        secondary = style.secondary,
        background = style.background,
        surface = style.surface,
        onBackground = style.onBackground,
        onSurface = style.onBackground,
        onSurfaceVariant = style.onSurfaceVariant
    )
    MaterialTheme(colorScheme = colorScheme, content = content)
}
