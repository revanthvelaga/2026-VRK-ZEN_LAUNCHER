package com.zenfold.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenfold.launcher.AppEntry
import com.zenfold.launcher.dominantHue
import com.zenfold.launcher.monochromeLayer
import com.zenfold.launcher.style.CustomStyle
import com.zenfold.launcher.style.IconStyle
import com.zenfold.launcher.toLayerBitmap
import com.zenfold.launcher.toUnmaskedBitmap
import kotlin.math.abs
import kotlin.math.min

@Composable
fun AppIcon(app: AppEntry, style: CustomStyle, showLabel: Boolean = true, onClick: () -> Unit) {
    val crystal = style.iconStyle == IconStyle.CRYSTAL
    val art = remember(app.packageName, crystal) { iconArt(app, crystal) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(style.iconSize.sizeDp.dp)
                .clip(style.iconShapeKind.shape)
        ) {
            when (art) {
                is IconArt.Original -> Image(bitmap = art.bitmap, contentDescription = app.label)
                is IconArt.Crystal -> CrystalTile(art, app.label)
            }
        }
        if (showLabel) {
            Text(
                text = app.label,
                fontSize = (12 * style.fontScale.scale).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = style.onBackground.copy(alpha = 0.88f)
            )
        }
    }
}

@Composable
private fun CrystalTile(art: IconArt.Crystal, label: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(art.light, art.dark), start = Offset.Zero, end = Offset.Infinite)),
        contentAlignment = Alignment.Center
    ) {
        // Glossy top highlight, like the preview's inset white edge.
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.28f), 0.45f to Color.Transparent))
        )
        Image(
            bitmap = art.glyph,
            contentDescription = label,
            colorFilter = ColorFilter.tint(Color.White),
            modifier = Modifier.fillMaxSize(0.8f)
        )
    }
}

private sealed interface IconArt {
    data class Original(val bitmap: ImageBitmap) : IconArt
    data class Crystal(val glyph: ImageBitmap, val light: Color, val dark: Color) : IconArt
}

// Crystal = the app's own Android 13+ themed-icon glyph on a gradient tile. Apps that
// don't ship a themed glyph (and phones below Android 13) keep their original icon.
private fun iconArt(app: AppEntry, crystal: Boolean): IconArt {
    val full = app.icon.toUnmaskedBitmap(128)
    val glyph = if (crystal) app.icon.monochromeLayer() else null
    if (glyph == null) return IconArt.Original(full.asImageBitmap())
    val (light, dark) = crystalGradient(full.dominantHue())
    return IconArt.Crystal(glyph.toLayerBitmap(128).asImageBitmap(), light, dark)
}

// The preview's jewel-tone gradients, keyed by hue.
private val crystalGradients = listOf(
    38f to (Color(0xFFFFC369) to Color(0xFFD97706)),   // amber
    155f to (Color(0xFF22E0A0) to Color(0xFF0D7A54)),  // emerald
    172f to (Color(0xFF5EEAD4) to Color(0xFF0F766E)),  // teal
    190f to (Color(0xFF45E0F0) to Color(0xFF0E7490)),  // cyan
    220f to (Color(0xFF4D8BFF) to Color(0xFF1743B0)),  // sapphire
    262f to (Color(0xFFA78BFA) to Color(0xFF5B21B6)),  // violet
    325f to (Color(0xFFFF85C0) to Color(0xFFBE185D)),  // rose
    352f to (Color(0xFFFF7B8F) to Color(0xFFB5233B))   // ruby
)
private val slateGradient = Color(0xFFA9B6C9) to Color(0xFF475569)

private fun crystalGradient(hue: Float?): Pair<Color, Color> {
    hue ?: return slateGradient
    return crystalGradients.minBy { (anchor, _) ->
        val d = abs(anchor - hue)
        min(d, 360f - d)
    }.second
}
