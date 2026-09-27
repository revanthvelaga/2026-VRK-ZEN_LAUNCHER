package com.zenfold.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.luminance
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
    val resolved = if (style.background.luminance() > 0.5f) lightColorScheme(
        primary = style.accent, onPrimary = style.background,
        secondary = style.secondary, background = style.background, surface = style.surface,
        onBackground = style.onBackground, onSurface = style.onBackground,
        onSurfaceVariant = style.onSurfaceVariant
    ) else colorScheme.copy(onPrimary = style.background)
    MaterialTheme(colorScheme = resolved, content = content)
}
