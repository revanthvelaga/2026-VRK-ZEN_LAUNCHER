package com.zenfold.launcher.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
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

/**
 * Where the last-tapped icon sits on screen, so the app can open zooming out of it the way
 * MIUI and Pixel do. Set on tap, taken once by MainActivity when it starts the app.
 */
object LaunchBounds {
    private var last: android.graphics.Rect? = null

    fun set(bounds: android.graphics.Rect) {
        last = bounds
    }

    fun take(): android.graphics.Rect? = last.also { last = null }
}

@Composable
fun AppIcon(
    app: AppEntry,
    style: CustomStyle,
    showLabel: Boolean = true,
    badged: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // MIUI-style: no ripple, the icon itself sinks a little under your finger and springs back.
    val scale by animateFloatAsState(
        if (pressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "iconPress"
    )
    var bounds by remember { mutableStateOf(Rect.Zero) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            AppIconImage(
                app,
                style,
                Modifier
                    .size(style.iconSize.sizeDp.dp)
                    .onGloballyPositioned { bounds = it.boundsInWindow() }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(style.iconShapeKind.shape)
                    .combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        onLongClick = onLongClick,
                        onClick = {
                            LaunchBounds.set(bounds.toAndroidRect())
                            onClick()
                        }
                    )
            )
            if (badged) NotificationDot(style, Modifier.align(Alignment.TopEnd))
        }
        if (showLabel) IconLabel(app.label, style)
    }
}

/** Just the icon art (original or crystal), sized and clipped by the caller. */
@Composable
fun AppIconImage(app: AppEntry, style: CustomStyle, modifier: Modifier = Modifier) {
    val crystal = style.iconStyle == IconStyle.CRYSTAL
    val tonal = style.iconStyle == IconStyle.TONAL
    val glyph = remember(app.icon, tonal) { if (tonal) app.icon.monochromeLayer()?.toLayerBitmap(128)?.asImageBitmap() else null }
    val art = remember(app.packageName, app.icon, crystal) { iconArt(app, crystal) }
    if (tonal) {
        Box(modifier.background(style.accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            if (glyph != null) Image(glyph, app.label, Modifier.fillMaxSize(0.78f), colorFilter = ColorFilter.tint(style.onBackground))
            else {
                // Older apps keep their recognizable silhouette and shading, never a made-up glyph.
                val original = remember(app.icon) { app.icon.toUnmaskedBitmap(128).asImageBitmap() }
                Image(original, app.label, Modifier.fillMaxSize(0.74f),
                    colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }))
            }
        }
        return
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        when (art) {
            is IconArt.Original -> Image(bitmap = art.bitmap, contentDescription = app.label, modifier = Modifier.fillMaxSize())
            is IconArt.Crystal -> CrystalTile(art, app.label)
        }
    }
}

@Composable
internal fun IconLabel(text: String, style: CustomStyle) {
    Text(
        text = text,
        fontSize = (12 * style.fontScale.scale).sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = style.onBackground.copy(alpha = 0.88f)
    )
}

@Composable
internal fun NotificationDot(style: CustomStyle, modifier: Modifier = Modifier) {
    Box(
        modifier
            .offset(x = 3.dp, y = (-3).dp)
            .size(11.dp)
            .background(style.accent, CircleShape)
            .border(1.5.dp, style.background, CircleShape)
    )
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
