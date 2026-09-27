package com.zenfold.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild

private val glassWash = Brush.verticalGradient(
    listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.04f))
)
private val glassEdge = Color.White.copy(alpha = 0.22f)

/** Frosted glass: real blur of the wallpaper behind, plus the preview's white wash and hairline edge. */
fun Modifier.glass(hazeState: HazeState, shape: Shape): Modifier = this
    .clip(shape)
    .hazeChild(state = hazeState, shape = shape)
    .glassTint(shape)

/** The wash and edge alone, for surfaces that already sit on a blurred layer (e.g. inside the drawer). */
fun Modifier.glassTint(shape: Shape): Modifier = this
    .clip(shape)
    .background(glassWash, shape)
    .border(1.dp, glassEdge, shape)
