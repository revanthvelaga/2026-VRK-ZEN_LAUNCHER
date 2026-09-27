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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.zenfold.launcher.style.WallpaperArt
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
            drawWallpaperArt(style)
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

/** Resolution-independent artwork drawn only when Compose invalidates the canvas; no animation loop. */
internal fun DrawScope.drawWallpaperArt(style: CustomStyle) {
    val w = size.width
    val h = size.height
    when (style.wallpaperArt) {
        WallpaperArt.GLOW -> Unit
        WallpaperArt.DUNE -> repeat(4) { layer ->
            val crest = h * (0.44f + layer * 0.13f)
            val path = Path().apply {
                moveTo(-w * 0.1f, h)
                lineTo(-w * 0.1f, crest + h * 0.15f)
                cubicTo(w * 0.25f, crest - h * 0.2f, w * 0.65f, crest + h * 0.24f, w * 1.1f, crest)
                lineTo(w * 1.1f, h)
                close()
            }
            drawPath(path, Brush.verticalGradient(listOf(style.secondary.copy(alpha = 0.13f + layer * 0.035f), style.background), crest, h))
        }
        WallpaperArt.ORBIT -> {
            val center = Offset(w * 0.9f, h * 0.59f)
            glow(style.secondary.copy(alpha = 0.2f), center, w * 0.8f)
            repeat(5) { index ->
                drawCircle(style.accent.copy(alpha = 0.13f - index * 0.018f), w * (0.46f + index * 0.13f), center,
                    style = Stroke(width = (w * 0.006f).coerceAtLeast(1f)))
            }
        }
        WallpaperArt.MIST -> repeat(3) { layer ->
            val path = Path().apply {
                moveTo(0f, h * (0.6f + layer * 0.1f))
                cubicTo(w * 0.24f, h * (0.36f + layer * 0.1f), w * 0.7f, h * (0.78f + layer * 0.04f), w, h * (0.52f + layer * 0.13f))
                lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(path, style.secondary.copy(alpha = 0.08f + layer * 0.045f))
        }
    }
}
