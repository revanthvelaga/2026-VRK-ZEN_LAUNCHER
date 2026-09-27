package com.zenfold.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.zenfold.launcher.style.CustomStyle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

// Positions and radii mirror the HTML preview's CSS radial-gradients
// (e.g. "circle at 18% 10% ... transparent 55%") on a phone-shaped screen.
// .haze() goes last so it captures the fully drawn wallpaper for the glass.
@Composable
fun Wallpaper(style: CustomStyle, hazeState: HazeState, modifier: Modifier = Modifier) {
    Box(modifier.haze(hazeState)) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.linearGradient(
                    colors = listOf(style.background, style.surface),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )
            )
            glow(style.glowTopStart, Offset(size.width * 0.18f, size.height * 0.10f), size.height * 0.54f)
            glow(style.glowTopEnd, Offset(size.width * 0.90f, size.height * 0.20f), size.height * 0.45f)
            glow(style.glowBottomEnd, Offset(size.width * 0.72f, size.height * 0.94f), size.height * 0.50f)
        }
    }
}

private fun DrawScope.glow(color: Color, center: Offset, radius: Float) {
    if (color.alpha == 0f) return
    drawRect(Brush.radialGradient(listOf(color, Color.Transparent), center, radius))
}
