package com.vrk.dialer

import android.content.Context
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.util.LruCache
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val CallGreen = Color(0xFF009F70)
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
    val appearance = LocalAppearance.current
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val ctx = LocalContext.current
    // Always on, matching what "enable dial pad touch sound" actually means to a user —
    // not gated behind the phone's own Settings > Sounds > "Dial pad tones" toggle, which
    // plenty of phones ship off by default.
    val toneGen = remember(tones, appearance.tones) { if (tones && appearance.tones) runCatching { ToneGenerator(AudioManager.STREAM_DTMF, 70) }.getOrNull() else null }
    DisposableEffect(toneGen) { onDispose { toneGen?.release() } }

    Column(modifier.widthIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        KEYS.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                row.forEach { (digit, letters) ->
                    val key = digit[0]
                    val longPress: (() -> Unit)? = when {
                        key == '0' -> ({ onKey('+') })
                        onLongDigit != null && key in '1'..'9' -> ({
                            if (appearance.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongDigit?.invoke(key)
                        })
                        else -> null
                    }
                    val interaction = remember { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    val scale by animateFloatAsState(if (pressed && appearance.motion) 0.96f else 1f, spring(dampingRatio = 0.72f, stiffness = 650f), label = "key")
                    val keyColor by animateColorAsState(
                        if (pressed) MaterialTheme.colorScheme.primaryContainer else if (appearance.theme == PhoneTheme.FLOW) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant,
                        tween(110), label = "key-color"
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .wrapContentWidth(Alignment.CenterHorizontally)
                            .widthIn(max = 104.dp).fillMaxWidth().height(68.dp)
                            .scale(scale)
                            .clip(if (appearance.theme == PhoneTheme.LUMINOUS) RoundedCornerShape(24.dp) else CircleShape)
                            .background(keyColor)
                            .semantics { contentDescription = when (key) {
                                '0' -> "0, hold for plus"
                                '1' -> if (onLongDigit != null) "1, hold for voicemail" else "1"
                                else -> "$digit $letters"
                            } }
                            .combinedClickable(
                                role = Role.Button,
                                interactionSource = interaction,
                                indication = null,
                                onClick = {
                                    // KEYBOARD_TAP is the real "typing on a keypad" click Android
                                    // uses for its own dial pad — Compose's HapticFeedbackType only
                                    // exposes LongPress/TextHandleMove, neither of which is that.
                                    if (appearance.haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    DTMF_TONES[key]?.let { toneGen?.startTone(it, 120) }
                                    onKey(key)
                                },
                                onLongClick = longPress
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(digit, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            if (key == '1' && onLongDigit != null) "voicemail" else letters,
                            fontSize = if (key == '1') 9.sp else 10.sp, letterSpacing = 1.6.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RoundButton(
    icon: ImageVector,
    label: String?,
    bg: Color,
    fg: Color,
    size: Dp = 64.dp,
    enabled: Boolean = true,
    elevated: Boolean = false,
    accessibilityLabel: String? = label,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.35f
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val appearance = LocalAppearance.current
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(if (pressed && appearance.motion) 0.9f else 1f, spring(dampingRatio = 0.5f), label = "round-button")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(size)
                .scale(scale)
                .then(if (elevated) Modifier.shadow(10.dp, CircleShape, spotColor = bg, ambientColor = bg) else Modifier)
                .clip(CircleShape)
                .background(bg.copy(alpha = bg.alpha * alpha))
                .clickable(role = Role.Button, enabled = enabled, interactionSource = interaction, indication = null, onClick = { if (appearance.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = accessibilityLabel, tint = fg.copy(alpha = alpha), modifier = Modifier.size(size * 0.42f))
        }
        if (label != null) {
            Spacer(Modifier.height(6.dp))
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
        }
    }
}

/**
 * A soft expanding ring behind the incoming-call avatar (Google's own "someone is calling"
 * motion). Pure animation, no allocation per frame; stops entirely once composed away.
 */
@Composable
fun PulsingRing(color: Color, modifier: Modifier = Modifier) {
    if (!LocalAppearance.current.motion) return
    val transition = rememberInfiniteTransition(label = "ring")
    val scale by transition.animateFloat(
        initialValue = 1f, targetValue = 1.55f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing)),
        label = "ring-scale"
    )
    val alpha by transition.animateFloat(
        initialValue = 0.5f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing)),
        label = "ring-alpha"
    )
    Box(
        modifier
            .scale(scale)
            .border(2.dp, color.copy(alpha = alpha), CircleShape)
    )
}

// Contact photos are small thumbnails, decoded once and kept for the session.
private val photoCache = object : LruCache<String, ImageBitmap>(16 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
}

internal fun loadPhoto(ctx: Context, uri: String): ImageBitmap? = photoCache.get(uri) ?: runCatching {
    val source = Uri.parse(uri)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    ctx.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?.asImageBitmap()?.also { photoCache.put(uri, it) }
}.getOrNull()

/** The contact's photo when there is one, else a coloured initial (MIUI style). */
@Composable
fun Avatar(name: String?, photo: String? = null, size: Dp = 44.dp) {
    val ctx = LocalContext.current
    var image by remember(photo) { mutableStateOf(photo?.let { photoCache.get(it) }) }
    LaunchedEffect(photo) {
        if (photo != null && image == null) image = withContext(Dispatchers.IO) { loadPhoto(ctx, photo) }
    }
    val letter = name?.firstOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: "#"
    val avatarColors = listOf(Color(0xFF386B5C), Color(0xFF586E96), Color(0xFF946754), Color(0xFF7C638D), Color(0xFF657345))
    val avatarColor = avatarColors[((name?.hashCode() ?: 0) and 0x7FFFFFFF) % avatarColors.size]
    Box(
        Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(avatarColor, avatarColor.copy(red = avatarColor.red * 0.75f, green = avatarColor.green * 0.75f, blue = avatarColor.blue * 0.75f)))),
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
