package com.zenfold.launcher.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
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

// With [phoneWallpaper], the phone's own wallpaper shows through the window (the Home
// theme sets windowShowWallpaper and a transparent background), and this only adds a
// light scrim so white labels stay readable on bright wallpapers. Android doesn't let
// apps read that wallpaper's pixels any more, so glass surfaces over it are tinted, not
// blurred. Otherwise it paints ZenFold's gradient: positions and radii mirror the HTML
// preview's CSS radial-gradients (e.g. "circle at 18% 10% ... transparent 55%").
// .haze() goes last so it captures the fully drawn wallpaper for the glass.
@Composable
fun Wallpaper(style: CustomStyle, hazeState: HazeState, phoneWallpaper: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.haze(hazeState)) {
        if (phoneWallpaper) {
            Canvas(Modifier.fillMaxSize()) { drawRect(Color.Black.copy(alpha = 0.12f)) }
            return@Box
        }
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

/** The phone's own wallpaper picker (on MIUI/HyperOS, the Themes app's wallpaper page). */
fun openWallpaperPicker(context: Context) {
    try {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Choose wallpaper"))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No wallpaper app found on this phone", Toast.LENGTH_SHORT).show()
    }
}
