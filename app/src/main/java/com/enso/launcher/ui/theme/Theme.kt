package com.enso.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val EnsoColorScheme = darkColorScheme(
    primary = EnsoMoss,
    secondary = EnsoClay,
    background = EnsoInk,
    surface = EnsoStone,
    onBackground = EnsoMist,
    onSurface = EnsoMist,
    onSurfaceVariant = EnsoMistDim
)

@Composable
fun EnsoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EnsoColorScheme,
        content = content
    )
}
