package com.zenfold.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zenfold.launcher.style.CustomStyle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

// The blur behind glass surfaces needs something with real detail to blur —
// a flat color blurs into itself. These two soft, off-screen-anchored glows
// (in the active style's own accent/secondary) stand in for a real wallpaper.
// .haze() is applied last so it captures the fully composited background,
// matching how Haze's own examples chain it after content-drawing modifiers.
@Composable
fun Wallpaper(style: CustomStyle, hazeState: HazeState, modifier: Modifier = Modifier) {
    Box(modifier.background(style.background).haze(hazeState)) {
        GlowBlob(
            color = style.accent,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset((-60).dp, (-60).dp)
                .size(260.dp)
        )
        GlowBlob(
            color = style.secondary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(60.dp, 80.dp)
                .size(280.dp)
        )
    }
}

@Composable
private fun GlowBlob(color: Color, modifier: Modifier) {
    Box(
        modifier.background(
            Brush.radialGradient(listOf(color.copy(alpha = 0.45f), Color.Transparent))
        )
    )
}
