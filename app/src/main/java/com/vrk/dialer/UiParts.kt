package com.vrk.dialer

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val CallGreen = Color(0xFF2DBE60)
val CallRed = Color(0xFFF2453D)

private val KEYS = listOf(
    "1" to " ", "2" to "ABC", "3" to "DEF",
    "4" to "GHI", "5" to "JKL", "6" to "MNO",
    "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
    "*" to " ", "0" to "+", "#" to " "
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Dialpad(onKey: (Char) -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    Column(modifier) {
        KEYS.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { (digit, letters) ->
                    Column(
                        Modifier
                            .weight(1f)
                            .height(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .combinedClickable(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onKey(digit[0])
                                },
                                // long-press 0 for "+", like MIUI
                                onLongClick = if (digit == "0") ({ onKey('+') }) else null
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(digit, fontSize = 28.sp, fontWeight = FontWeight.Light)
                        Text(
                            letters, fontSize = 10.sp, letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RoundButton(
    icon: ImageVector,
    label: String?,
    bg: Color,
    fg: Color,
    size: Dp = 64.dp,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(size).clip(CircleShape).background(bg).clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(size * 0.42f))
        }
        if (label != null) {
            Spacer(Modifier.height(6.dp))
            Text(label, fontSize = 12.sp)
        }
    }
}

@Composable
fun Avatar(name: String?) {
    val letter = name?.firstOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: "#"
    val hue = ((name?.hashCode() ?: 0) and 0xFFFF) % 360
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(Color.hsl(hue.toFloat(), 0.45f, 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        Text(letter, color = Color.White, fontWeight = FontWeight.Medium)
    }
}
