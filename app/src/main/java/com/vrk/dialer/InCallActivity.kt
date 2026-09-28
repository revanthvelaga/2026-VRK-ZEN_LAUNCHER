@file:Suppress("DEPRECATION") // Call.getState() / CallAudioState: minSdk 29

package com.vrk.dialer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class InCallActivity : ComponentActivity() {

    private var proximity: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleAnswer(intent)
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            proximity = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "vrk:proximity")
        }
        setContent {
            DialerTheme(dark = true) {
                CompositionLocalProvider(LocalContentColor provides Color.White) {
                    InCallScreen(onDone = { finish() }, onProximity = ::setProximity)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAnswer(intent)
    }

    // "Answer" in the notification opens this screen with the extra (see CallNotifications).
    private fun handleAnswer(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_ANSWER, false) == true) CallManager.answer()
    }

    /** Screen off at your ear, so your cheek can't press buttons — not on speaker or Bluetooth. */
    private fun setProximity(on: Boolean) {
        val lock = proximity ?: return
        if (on && !lock.isHeld) lock.acquire(2 * 60 * 60 * 1000L)
        if (!on && lock.isHeld) lock.release()
    }

    override fun onDestroy() {
        setProximity(false)
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_ANSWER = "answer"

        fun intent(ctx: Context, answer: Boolean = false): Intent =
            Intent(ctx, InCallActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_ANSWER, answer)
    }
}

private val EAR_STATES = setOf(Call.STATE_ACTIVE, Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_HOLDING)

@Composable
fun InCallScreen(onDone: () -> Unit, onProximity: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val version by CallManager.version.collectAsState()
    val audio by CallManager.audio.collectAsState()
    val calls = remember(version) { CallManager.calls.value }
    val primary = remember(version) { CallManager.primary() }
    val ringing = remember(version) { CallManager.ringing() }
    val active = remember(version) { CallManager.active() }
    val held = remember(version) { CallManager.held() }
    val canMerge = remember(version) { CallManager.canMerge() }
    val state = primary?.state ?: Call.STATE_DISCONNECTED
    // The other call while two are up (one on hold) — shown as a strip you can swap to.
    val other = remember(version) { calls.firstOrNull { it != primary && it.state != Call.STATE_RINGING } }

    var showPad by remember { mutableStateOf(false) }
    var padDigits by remember { mutableStateOf("") }
    var showReplies by remember { mutableStateOf(false) }
    var showRoutes by remember { mutableStateOf(false) }
    var showConference by remember { mutableStateOf(false) }

    LaunchedEffect(calls.isEmpty()) {
        if (calls.isEmpty()) {
            delay(800)
            onDone()
        }
    }

    val route = audio?.route ?: CallAudioState.ROUTE_EARPIECE
    val routes = audio?.supportedRouteMask ?: (CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER)
    val muted = audio?.isMuted == true
    val hasBluetooth = routes and CallAudioState.ROUTE_BLUETOOTH != 0
    LaunchedEffect(state, route) { onProximity(route == CallAudioState.ROUTE_EARPIECE && state in EAR_STATES) }

    val number = primary?.number().orEmpty()
    val conference = primary?.isConference == true
    val name = rememberContactName(number)
    val otherNumber = other?.number().orEmpty()
    val otherName = rememberContactName(otherNumber)
    val sim = remember(primary, version) {
        primary?.details?.accountHandle?.let { h -> if (simAccounts(ctx).size > 1) simLabel(ctx, h) else null }
    }

    val status = when (state) {
        Call.STATE_RINGING -> if (active != null || held != null) "Call waiting" else "Incoming call"
        Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_NEW -> "Calling…"
        Call.STATE_ACTIVE -> null // ticking text, rendered by DurationText below on its own clock
        Call.STATE_HOLDING -> "On hold"
        Call.STATE_SELECT_PHONE_ACCOUNT -> "Choose SIM"
        Call.STATE_DISCONNECTING -> "Ending…"
        else -> "Call ended"
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF303B81), Color(0xFF0B102B))))
            .safeDrawingPadding()
    ) {
        val availableHeight = maxHeight
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = availableHeight).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (other != null && state != Call.STATE_RINGING) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.10f))
                        .clickable { CallManager.swap() }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(otherName ?: otherNumber.ifEmpty { "Other call" }, fontSize = 15.sp, maxLines = 1)
                        Text(if (other.state == Call.STATE_HOLDING) "On hold" else "Active", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                    }
                    if (other.state == Call.STATE_HOLDING) Text("Swap", color = CallGreen, fontSize = 15.sp)
                }
            }
            Spacer(Modifier.height(if (other != null) 24.dp else 32.dp))
            Box(contentAlignment = Alignment.Center) {
                if (state == Call.STATE_RINGING) {
                    PulsingRing(CallGreen, Modifier.size(104.dp))
                }
                Avatar(if (conference) "Conference" else name ?: number, size = 104.dp)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                when {
                    conference -> "Conference call"
                    else -> name ?: number.ifEmpty { "Unknown number" }
                },
                style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center, maxLines = 2
            )
            if (name != null && !conference) Text(number, fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f))
            Spacer(Modifier.height(8.dp))
            if (status != null) {
                Text(listOfNotNull(status, sim).joinToString(" · "), fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f))
            } else if (primary != null) {
                DurationText(primary, sim)
            }
            if (conference && primary != null) {
                TextButton(onClick = { showConference = true }) {
                    Text("Manage conference (${primary.children.size})", color = CallGreen)
                }
            }
            Spacer(Modifier.height(32.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when (state) {
                Call.STATE_RINGING -> if (active != null || held != null) {
                    // Call waiting, as on MIUI/OxygenOS: three ways to take the new call.
                    Row(Modifier.fillMaxWidth().padding(bottom = 40.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        RoundButton(Icons.Filled.CallEnd, "Decline", CallRed, Color.White, 68.dp, elevated = true) { CallManager.decline() }
                        RoundButton(Icons.Filled.Call, "Hold & answer", CallGreen, Color.White, 68.dp, elevated = true) { CallManager.answer() }
                        RoundButton(Icons.Filled.PhoneInTalk, "End & answer", CallGreen, Color.White, 68.dp, elevated = true) { CallManager.endActiveAndAnswer() }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 8.dp)) {
                        RoundButton(Icons.Filled.Sms, "Message", Color.White.copy(alpha = 0.12f), Color.White, 52.dp) { showReplies = true }
                        Spacer(Modifier.height(20.dp))
                        // One handle, either direction — the Samsung One UI pattern, not two
                        // separate swipe zones side by side.
                        IncomingCallSwipe(onAnswer = { CallManager.answer() }, onDecline = { CallManager.decline() })
                    }
                }

                Call.STATE_SELECT_PHONE_ACCOUNT -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 32.dp)
                ) {
                    simAccounts(ctx).forEach { h ->
                        Button(onClick = { CallManager.selectSim(h) }, modifier = Modifier.fillMaxWidth()) {
                            Text(simLabel(ctx, h))
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Spacer(Modifier.height(24.dp))
                    RoundButton(Icons.Filled.CallEnd, null, CallRed, Color.White, 72.dp, accessibilityLabel = "End call") { CallManager.hangUp() }
                }

                else -> {
                    if (showPad) {
                        Text(padDigits, fontSize = 28.sp, maxLines = 1)
                        Dialpad(onKey = { padDigits += it; CallManager.dtmf(it) }, tones = false)
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Toggle(if (muted) Icons.Filled.MicOff else Icons.Filled.Mic, "Mute", muted) {
                                CallManager.setMute(!muted)
                            }
                            Toggle(Icons.Filled.Dialpad, "Keypad", false) { showPad = true }
                            if (hasBluetooth) {
                                Toggle(routeIcon(route), routeLabel(route), route != CallAudioState.ROUTE_EARPIECE) { showRoutes = true }
                            } else {
                                val speaker = route == CallAudioState.ROUTE_SPEAKER
                                Toggle(Icons.AutoMirrored.Filled.VolumeUp, "Speaker", speaker) {
                                    CallManager.setRoute(if (speaker) CallAudioState.ROUTE_WIRED_OR_EARPIECE else CallAudioState.ROUTE_SPEAKER)
                                }
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            val onHold = primary?.state == Call.STATE_HOLDING
                            Toggle(if (onHold) Icons.Filled.PlayArrow else Icons.Filled.Pause, if (onHold) "Resume" else "Hold", onHold,
                                enabled = active != null || held != null) { CallManager.toggleHold(primary) }
                            Toggle(Icons.Filled.Add, "Add call", false) {
                                ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                            if (!canMerge && active != null && held != null) {
                                Toggle(Icons.Filled.SwapVert, "Swap", false) { CallManager.swap() }
                            } else {
                                Toggle(Icons.Filled.Group, "Merge", false, enabled = canMerge) { CallManager.merge() }
                            }
                        }
                    }
                    Spacer(Modifier.height(36.dp))
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 32.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showPad) TextButton(onClick = { showPad = false }) { Text("Hide", color = Color.White) }
                        else Spacer(Modifier.width(64.dp))
                        SwipeCallControl(Icons.Filled.CallEnd, "End call", CallRed, upward = false) { CallManager.hangUp() }
                        Spacer(Modifier.width(64.dp))
                    }
                }
            }
        }
    }

    }

    if (showReplies) {
        QuickReplyDialog(
            onDismiss = { showReplies = false },
            onSend = { text ->
                showReplies = false
                CallManager.decline(message = text)
            }
        )
    }
    if (showRoutes) {
        AlertDialog(
            onDismissRequest = { showRoutes = false },
            confirmButton = {},
            title = { Text("Audio") },
            text = {
                Column {
                    listOf(
                        CallAudioState.ROUTE_BLUETOOTH, CallAudioState.ROUTE_SPEAKER,
                        CallAudioState.ROUTE_WIRED_HEADSET, CallAudioState.ROUTE_EARPIECE
                    ).filter { routes and it != 0 }.forEach { r ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .clickable { CallManager.setRoute(r); showRoutes = false }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(routeIcon(r), contentDescription = null)
                            Spacer(Modifier.width(16.dp))
                            Text(routeLabel(r), fontSize = 17.sp, modifier = Modifier.weight(1f))
                            if (r == route) Text("✓", color = CallGreen, fontSize = 18.sp)
                        }
                    }
                }
            }
        )
    }
    if (showConference && primary != null) {
        ConferenceDialog(primary, onDismiss = { showConference = false })
    }
}

/** Its own tick loop, so only this line recomposes every second — not the whole screen. */
@Composable
private fun DurationText(call: Call, sim: String?) {
    var text by remember(call) { mutableStateOf("00:00") }
    LaunchedEffect(call) {
        while (true) {
            val s = ((System.currentTimeMillis() - call.details.connectTimeMillis) / 1000).coerceAtLeast(0)
            text = if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
            delay(1000)
        }
    }
    Text(listOfNotNull(text, sim).joinToString(" · "), fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f))
}

@Composable
private fun rememberContactName(number: String): String? {
    val ctx = LocalContext.current
    var name by remember(number) { mutableStateOf<String?>(null) }
    LaunchedEffect(number) {
        name = if (number.isBlank()) null else withContext(Dispatchers.IO) { lookupName(ctx, number) }
    }
    return name
}

private fun routeIcon(route: Int): ImageVector = when (route) {
    CallAudioState.ROUTE_BLUETOOTH -> Icons.Filled.Bluetooth
    CallAudioState.ROUTE_SPEAKER -> Icons.AutoMirrored.Filled.VolumeUp
    CallAudioState.ROUTE_WIRED_HEADSET -> Icons.Filled.Headset
    else -> Icons.Filled.PhoneInTalk
}

private fun routeLabel(route: Int): String = when (route) {
    CallAudioState.ROUTE_BLUETOOTH -> "Bluetooth"
    CallAudioState.ROUTE_SPEAKER -> "Speaker"
    CallAudioState.ROUTE_WIRED_HEADSET -> "Headset"
    else -> "Phone"
}

@Composable
fun QuickReplyDialog(onDismiss: () -> Unit, onSend: (String) -> Unit) {
    val ctx = LocalContext.current
    val replies = remember { quickReplies(ctx) }
    var custom by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Decline with message") },
        text = {
            Column {
                replies.forEach { reply ->
                    Text(
                        reply, fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable { onSend(reply) }.padding(vertical = 12.dp, horizontal = 8.dp)
                    )
                }
                OutlinedTextField(
                    value = custom, onValueChange = { custom = it },
                    placeholder = { Text("Write your own…") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSend(custom.trim()) }, enabled = custom.isNotBlank()) { Text("Send") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ConferenceDialog(conference: Call, onDismiss: () -> Unit) {
    val version by CallManager.version.collectAsState()
    val people = remember(version) { conference.children.toList() }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Conference") },
        text = {
            Column {
                if (people.isEmpty()) Text("Nobody left in the conference")
                people.forEach { person ->
                    val number = person.number()
                    val name = rememberContactName(number)
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(name ?: number, modifier = Modifier.weight(1f), maxLines = 1)
                        // Private = split them off into a one-to-one call.
                        if (person.details.can(Call.Details.CAPABILITY_SEPARATE_FROM_CONFERENCE)) {
                            TextButton(onClick = { person.splitFromConference() }) { Text("Private") }
                        }
                        if (person.details.can(Call.Details.CAPABILITY_DISCONNECT_FROM_CONFERENCE)) {
                            TextButton(onClick = { person.disconnect() }) { Text("End", color = CallRed) }
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun Toggle(icon: ImageVector, label: String, on: Boolean, enabled: Boolean = true, onClick: () -> Unit) =
    RoundButton(
        icon, label,
        bg = if (on) Color.White else Color.White.copy(alpha = 0.12f),
        fg = if (on) Color.Black else Color.White,
        enabled = enabled,
        onClick = onClick
    )
