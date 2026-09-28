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

/** One handle shared by themes. Up answers, down declines. */
@Composable
fun IncomingCallSwipe(onAnswer: () -> Unit, onDecline: () -> Unit) =
    IncomingSwipeControl(onAnswer, onDecline)

/** Swipe-down-only, used to end an already-connected call. */
@Composable
fun SwipeCallControl(icon: ImageVector, label: String, color: Color, upward: Boolean,
    ringing: Boolean = false, onCommit: () -> Unit) {
    val appearance = LocalAppearance.current
    val action by rememberUpdatedState(onCommit)
    val haptic = LocalHapticFeedback.current
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    var drag by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf(false) }
    var committed by remember { mutableStateOf(false) }
    val offset by animateFloatAsState(drag, if (dragging) snap() else spring(dampingRatio = .7f), label = "call-drag")
    fun commit() {
        if (!committed) { committed = true; if (appearance.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress); action() }
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
                            if (ready && !armed) if (appearance.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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

/** A single Flow handset: upward answers, downward declines, only after release. */
@Composable
fun IncomingSwipeControl(onAnswer: () -> Unit, onDecline: () -> Unit) {
    val answer by rememberUpdatedState(onAnswer)
    val decline by rememberUpdatedState(onDecline)
    val appearance = LocalAppearance.current
    val haptic = LocalHapticFeedback.current
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    var drag by remember { mutableFloatStateOf(0f) }
    var committed by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf(false) }
    val offset by animateFloatAsState(drag, spring(stiffness = 800f), label = "flow-swipe")
    fun commit(up: Boolean) {
        if (!committed) {
            committed = true
            if (appearance.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            if (up) answer() else decline()
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("↑  Swipe up to answer", color = Color.White)
        Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
            if (!committed) PulsingRing(CallGreen, Modifier.size(80.dp))
            Box(Modifier.offset { IntOffset(0, offset.roundToInt()) }.size(80.dp)
                .background(if (drag > 0) CallRed else CallGreen, CircleShape)
                .semantics {
                    contentDescription = "Incoming call control"
                    role = Role.Button
                    customActions = listOf(CustomAccessibilityAction("Answer") { commit(true); true },
                        CustomAccessibilityAction("Decline") { commit(false); true })
                }
                .pointerInput(threshold) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            drag = (drag + amount).coerceIn(-threshold * 1.5f, threshold * 1.5f)
                            val ready = kotlin.math.abs(drag) >= threshold
                            if (ready && !armed && appearance.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            armed = ready
                        },
                        onDragEnd = { if (armed) commit(drag < 0); drag = 0f; armed = false },
                        onDragCancel = { drag = 0f; armed = false })
                }, contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Call, null,
                    tint = Color.White, modifier = Modifier.size(34.dp))
            }
        }
        Text(if (armed) "Release to ${if (drag < 0) "answer" else "decline"}" else "↓  Swipe down to decline", color = Color.White)
    }
}
