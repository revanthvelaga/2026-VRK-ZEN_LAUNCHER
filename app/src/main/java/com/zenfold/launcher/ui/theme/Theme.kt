package com.zenfold.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ZenFoldColorScheme = darkColorScheme(
    primary = ZenFoldMoss,
    secondary = ZenFoldClay,
    background = ZenFoldInk,
    surface = ZenFoldStone,
    onBackground = ZenFoldMist,
    onSurface = ZenFoldMist,
    onSurfaceVariant = ZenFoldMistDim
)

@Composable
fun ZenFoldTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ZenFoldColorScheme,
        content = content
    )
}
