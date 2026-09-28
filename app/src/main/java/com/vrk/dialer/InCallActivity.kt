package com.vrk.dialer

import android.os.Bundle
import android.os.PowerManager
import android.telecom.Call
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        // Screen turns off when the phone is at your ear, so your cheek can't press buttons
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            proximity = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "vrk:proximity")
                .apply { acquire(2 * 60 * 60 * 1000L) }
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CompositionLocalProvider(LocalContentColor provides Color.White) {
                    InCallScreen(onDone = { finish() })
                }
            }
        }
    }

    override fun onDestroy() {
        proximity?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}

@Composable
fun InCallScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val call by CallManager.call.collectAsState()
    val state by CallManager.state.collectAsState()

    var number by remember { mutableStateOf("") }
    var name by remember { mutableStateOf<String?>(null) }
    var muted by remember { mutableStateOf(false) }
    var speaker by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf(false) }
    var showPad by remember { mutableStateOf(false) }
    var seconds by remember { mutableLongStateOf(0L) }

    LaunchedEffect(call) {
        val c = call
        if (c == null) { delay(800); onDone(); return@LaunchedEffect }
        number = c.details.handle?.schemeSpecificPart.orEmpty()
    }
    LaunchedEffect(number) { name = withContext(Dispatchers.IO) { lookupName(ctx, number) } }
    LaunchedEffect(state, call) {
        val c = call ?: return@LaunchedEffect
        while (state == Call.STATE_ACTIVE) {
            seconds = (System.currentTimeMillis() - c.details.connectTimeMillis) / 1000
            delay(1000)
        }
    }

    val status = when (state) {
        Call.STATE_RINGING -> "Incoming call"
        Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_NEW -> "Calling…"
        Call.STATE_ACTIVE -> "%02d:%02d".format(seconds / 60, seconds % 60)
        Call.STATE_HOLDING -> "On hold"
        Call.STATE_SELECT_PHONE_ACCOUNT -> "Choose SIM"
        else -> "Call ended"
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1B2A3A), Color(0xFF0B0F14))))
            .safeDrawingPadding()
    ) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(64.dp))
            Avatar(name)
            Spacer(Modifier.height(16.dp))
            Text(
                name ?: number.ifEmpty { "Unknown" },
                fontSize = 32.sp, textAlign = TextAlign.Center, maxLines = 2
            )
            if (name != null) Text(number, fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f))
            Spacer(Modifier.height(8.dp))
            Text(status, fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f))
            Spacer(Modifier.weight(1f))

            when (state) {
                Call.STATE_RINGING -> Row(
                    Modifier.fillMaxWidth().padding(bottom = 48.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    RoundButton(Icons.Filled.CallEnd, "Decline", CallRed, Color.White, 72.dp) { CallManager.hangUp() }
                    RoundButton(Icons.Filled.Call, "Answer", CallGreen, Color.White, 72.dp) { CallManager.answer() }
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
                    RoundButton(Icons.Filled.CallEnd, null, CallRed, Color.White, 72.dp) { CallManager.hangUp() }
                }

                else -> {
                    if (showPad) {
                        Dialpad(onKey = { CallManager.dtmf(it) })
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Toggle(if (muted) Icons.Filled.MicOff else Icons.Filled.Mic, "Mute", muted) {
                                muted = !muted; CallManager.setMute(muted)
                            }
                            Toggle(Icons.Filled.Dialpad, "Keypad", false) { showPad = true }
                            Toggle(Icons.AutoMirrored.Filled.VolumeUp, "Speaker", speaker) {
                                speaker = !speaker; CallManager.setSpeaker(speaker)
                            }
                            Toggle(Icons.Filled.Pause, "Hold", held) {
                                held = !held; CallManager.setHold(held)
                            }
                        }
                    }
                    Spacer(Modifier.height(40.dp))
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 32.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showPad) TextButton(onClick = { showPad = false }) { Text("Hide", color = Color.White) }
                        else Spacer(Modifier.width(64.dp))
                        RoundButton(Icons.Filled.CallEnd, null, CallRed, Color.White, 72.dp) { CallManager.hangUp() }
                        Spacer(Modifier.width(64.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Toggle(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) =
    RoundButton(
        icon, label,
        bg = if (on) Color.White else Color.White.copy(alpha = 0.12f),
        fg = if (on) Color.Black else Color.White,
        onClick = onClick
    )
