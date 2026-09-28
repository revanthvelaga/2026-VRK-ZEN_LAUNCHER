package com.vrk.dialer

import android.content.Context
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.provider.Settings
import android.util.LruCache
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val CallGreen = Color(0xFF2DBE60)
val CallRed = Color(0xFFF2453D)

private val KEYS = listOf(
    "1" to " ", "2" to "ABC", "3" to "DEF",
    "4" to "GHI", "5" to "JKL", "6" to "MNO",
    "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
    "*" to " ", "0" to "+", "#" to " "
)

private val DTMF_TONES = mapOf(
    '0' to ToneGenerator.TONE_DTMF_0, '1' to ToneGenerator.TONE_DTMF_1, '2' to ToneGenerator.TONE_DTMF_2,
    '3' to ToneGenerator.TONE_DTMF_3, '4' to ToneGenerator.TONE_DTMF_4, '5' to ToneGenerator.TONE_DTMF_5,
    '6' to ToneGenerator.TONE_DTMF_6, '7' to ToneGenerator.TONE_DTMF_7, '8' to ToneGenerator.TONE_DTMF_8,
    '9' to ToneGenerator.TONE_DTMF_9, '*' to ToneGenerator.TONE_DTMF_S, '#' to ToneGenerator.TONE_DTMF_P
)

/**
 * The keypad. Key tones follow the phone's own "Dial pad tones" sound setting. Long-press:
 * 0 → "+", 1 → voicemail, 2–9 → speed dial (via [onLongDigit]); [tones] is off in-call,
 * where the network plays the tones instead.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Dialpad(
    onKey: (Char) -> Unit,
    modifier: Modifier = Modifier,
    tones: Boolean = true,
    onLongDigit: ((Char) -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val ctx = LocalContext.current
    val toneGen = remember { if (tones && dialTonesEnabled(ctx)) runCatching { ToneGenerator(AudioManager.STREAM_DTMF, 70) }.getOrNull() else null }
    DisposableEffect(toneGen) { onDispose { toneGen?.release() } }

    Column(modifier) {
        KEYS.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { (digit, letters) ->
                    val key = digit[0]
                    val longPress: (() -> Unit)? = when {
                        key == '0' -> ({ onKey('+') })
                        onLongDigit != null && key in '1'..'9' -> ({
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongDigit?.invoke(key)
                        })
                        else -> null
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .height(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .combinedClickable(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    DTMF_TONES[key]?.let { toneGen?.startTone(it, 120) }
                                    onKey(key)
                                },
                                onLongClick = longPress
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(digit, fontSize = 28.sp, fontWeight = FontWeight.Light)
                        Text(
                            if (key == '1') "voicemail" else letters,
                            fontSize = if (key == '1') 8.sp else 10.sp, letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun dialTonesEnabled(ctx: Context) =
    Settings.System.getInt(ctx.contentResolver, Settings.System.DTMF_TONE_WHEN_DIALING, 1) == 1

@Composable
fun RoundButton(
    icon: ImageVector,
    label: String?,
    bg: Color,
    fg: Color,
    size: Dp = 64.dp,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.35f
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(size).clip(CircleShape).background(bg.copy(alpha = bg.alpha * alpha))
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = fg.copy(alpha = alpha), modifier = Modifier.size(size * 0.42f))
        }
        if (label != null) {
            Spacer(Modifier.height(6.dp))
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
        }
    }
}

// Contact photos are small thumbnails, decoded once and kept for the session.
private val photoCache = LruCache<String, ImageBitmap>(200)

private fun loadPhoto(ctx: Context, uri: String): ImageBitmap? = photoCache.get(uri) ?: runCatching {
    ctx.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }
        ?.asImageBitmap()?.also { photoCache.put(uri, it) }
}.getOrNull()

/** The contact's photo when there is one, else a coloured initial (MIUI style). */
@Composable
fun Avatar(name: String?, photo: String? = null, size: Dp = 44.dp) {
    val ctx = LocalContext.current
    val image by produceState<ImageBitmap?>(photo?.let { photoCache.get(it) }, photo) {
        if (photo != null && value == null) value = withContext(Dispatchers.IO) { loadPhoto(ctx, photo) }
    }
    val letter = name?.firstOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: "#"
    val hue = ((name?.hashCode() ?: 0) and 0xFFFF) % 360
    Box(
        Modifier.size(size).clip(CircleShape).background(Color.hsl(hue.toFloat(), 0.45f, 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        val img = image
        if (img != null) {
            Image(img, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(letter, color = Color.White, fontWeight = FontWeight.Medium, fontSize = (size.value * 0.4f).sp)
        }
    }
}
