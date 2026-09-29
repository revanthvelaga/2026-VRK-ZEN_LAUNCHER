package com.vrk.dialer

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.* // Role/onClick/contentDescription (SwipeCallControl) + CustomAccessibilityAction/customActions/semantics (IncomingCallSwipe)
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Samsung One UI style incoming-call control: one handle, both directions — drag it up to
 * answer, down to decline. Cancelling (releasing before the threshold) never calls either.
 */
@Composable
fun IncomingCallSwipe(onAnswer: () -> Unit, onDecline: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    var drag by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var armed by remember { mutableIntStateOf(0) } // -1 decline, 0 neither, 1 answer
    var committed by remember { mutableStateOf(false) }
    val offset by animateFloatAsState(drag, if (dragging) snap() else spring(dampingRatio = .6f), label = "incoming-drag")

    // The handle breathes gently while it waits, and settles the moment you touch it.
    val ringing = rememberInfiniteTransition(label = "incoming-pulse")
    val pulse by ringing.animateFloat(1f, 1.1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val wobble by ringing.animateFloat(-6f, 6f, infiniteRepeatable(tween(200), RepeatMode.Reverse), label = "wobble")

    fun commit(answer: Boolean) {
        if (committed) return
        committed = true
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (answer) onAnswer() else onDecline()
    }

    val progress = (offset.coerceIn(-threshold * 1.4f, threshold * 1.4f) / threshold).coerceIn(-1f, 1f)
    val handleColor = when {
        progress > 0f -> lerp(Color.White, CallGreen, progress)
        progress < 0f -> lerp(Color.White, CallRed, -progress)
        else -> Color.White
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 32.dp)) {
        Text(
            when (armed) {
                1 -> "Release to answer"
                -1 -> "Release to decline"
                else -> "Swipe up to answer · down to decline"
            },
            color = Color.White.copy(alpha = .85f), fontSize = 13.sp
        )
        Spacer(Modifier.height(8.dp))
        Box(Modifier.width(120.dp).height(288.dp), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Filled.KeyboardArrowUp, "Answer", tint = CallGreen.copy(alpha = if (armed >= 0) 1f else .3f),
                modifier = Modifier.align(Alignment.TopCenter).size(32.dp)
            )
            Icon(
                Icons.Filled.KeyboardArrowDown, "Decline", tint = CallRed.copy(alpha = if (armed <= 0) 1f else .3f),
                modifier = Modifier.align(Alignment.BottomCenter).size(32.dp)
            )
            Box(
                Modifier
                    .offset { IntOffset(0, offset.roundToInt()) }
                    .scale(if (dragging) 1f else pulse)
                    .size(80.dp)
                    .background(handleColor, CircleShape)
                    .semantics {
                        customActions = listOf(
                            CustomAccessibilityAction("Answer") { commit(true); true },
                            CustomAccessibilityAction("Decline") { commit(false); true }
                        )
                    }
                    .pointerInput(threshold) {
                        detectVerticalDragGestures(
                            onDragStart = { dragging = true },
                            onVerticalDrag = { change, amount ->
                                change.consume()
                                drag = (drag + amount).coerceIn(-threshold * 1.4f, threshold * 1.4f)
                                val next = when {
                                    drag <= -threshold -> -1
                                    drag >= threshold -> 1
                                    else -> 0
                                }
                                if (next != 0 && next != armed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                armed = next
                            },
                            onDragEnd = {
                                if (armed != 0) commit(armed == 1)
                                dragging = false; drag = 0f; armed = 0
                            },
                            onDragCancel = { dragging = false; drag = 0f; armed = 0 }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                val icon: ImageVector = if (progress < 0f) Icons.Filled.CallEnd else Icons.Filled.Call
                val tint = if (progress == 0f) Color(0xFF2436A0) else Color.White
                Icon(icon, null, tint = tint, modifier = Modifier.size(30.dp).let { m ->
                    if (!dragging) m.rotate(wobble) else m
                })
            }
        }
    }
}

/** Swipe-down-only, used to end an already-connected call. */
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
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
        Text(if (armed) "Release to ${label.lowercase()}" else label, color = Color.White, fontSize = 14.sp)
    }
}
