package com.vrk.dialer

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Drag then release beyond the threshold; cancellation never invokes a telecom action. */
@Composable
fun SwipeCallControl(icon: ImageVector, label: String, color: Color, upward: Boolean,
    ringing: Boolean = false, onCommit: () -> Unit) {
    val action by rememberUpdatedState(onCommit)
    val haptic = LocalHapticFeedback.current
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    var drag by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf(false) }
    var committed by remember { mutableStateOf(false) }
    val offset by animateFloatAsState(drag, if (dragging) snap() else spring(dampingRatio = .7f), label = "call-drag")
    val wobble = if (ringing) {
        val transition = rememberInfiniteTransition(label = "incoming-call")
        val angle by transition.animateFloat(-7f, 7f, infiniteRepeatable(tween(180), RepeatMode.Reverse), label = "handset-wobble")
        angle
    } else 0f
    fun commit() {
        if (!committed) { committed = true; haptic.performHapticFeedback(HapticFeedbackType.LongPress); action() }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(140.dp)) {
        Text(if (upward) "Swipe up" else "Swipe down", color = Color.White.copy(alpha = .75f), fontSize = 13.sp)
        Box(Modifier.height(176.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.offset { IntOffset(0, offset.roundToInt()) }.size(76.dp)
                .background(color, CircleShape)
                .semantics {
                    contentDescription = label
                    role = Role.Button
                    // TalkBack users can activate the same action without performing a drag.
                    onClick(label = label) { commit(); true }
                }
                .pointerInput(threshold, upward) {
                    detectVerticalDragGestures(
                        onDragStart = { dragging = true },
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            drag = if (upward) (drag + amount).coerceIn(-threshold * 1.4f, 0f) else (drag + amount).coerceIn(0f, threshold * 1.4f)
                            val ready = kotlin.math.abs(drag) >= threshold
                            if (ready && !armed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            armed = ready
                        },
                        onDragEnd = { if (armed) commit(); dragging = false; drag = 0f; armed = false },
                        onDragCancel = { dragging = false; drag = 0f; armed = false }
                    )
                }, contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(32.dp).rotate(if (ringing && !dragging) wobble else 0f))
            }
        }
        Text(if (armed) "Release to ${label.lowercase()}" else label, color = Color.White, fontSize = 14.sp)
    }
}
