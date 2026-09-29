package com.vrk.dialer

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
private fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 16.dp)) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        Text(title, style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp))
}

@Composable
fun SettingsScreen(contacts: List<Contact>, onSetDefault: () -> Unit, onBlocked: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var isDefault by remember { mutableStateOf(isDefaultDialer(ctx)) }
    LifecycleResumeEffect(Unit) {
        isDefault = isDefaultDialer(ctx)
        onPauseOrDispose { }
    }
    var replies by remember { mutableStateOf(quickReplies(ctx)) }
    var editReply by remember { mutableStateOf<Int?>(null) }
    var speedDials by remember { mutableStateOf((2..9).associateWith { speedDial(ctx, it) }) }
    var pickSpeedDial by remember { mutableStateOf<Int?>(null) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        ScreenHeader("Settings", onBack)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item {
                ListItem(
                    headlineContent = { Text("Default Phone app") },
                    supportingContent = {
                        Text(if (isDefault) "VRK Dialer handles your calls" else "Not set — incoming calls, blocking and history editing need it")
                    },
                    trailingContent = { if (!isDefault) Button(onClick = onSetDefault) { Text("Set") } }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("SIM & call settings") },
                    supportingContent = { Text("Default SIM, call forwarding, call waiting, caller ID") },
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { openCallSettings(ctx) }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Blocked numbers") },
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onBlocked)
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Call notifications") },
                    supportingContent = { Text("Heads-up for incoming calls") },
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable {
                        startSafely(ctx, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                    }
                )
            }

            item { SectionTitle("Quick responses (decline with message)") }
            items(replies.size) { index ->
                ListItem(
                    headlineContent = { Text(replies[index]) },
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { editReply = index }
                )
            }
            item {
                TextButton(onClick = {
                    setQuickReplies(ctx, DEFAULT_QUICK_REPLIES)
                    replies = DEFAULT_QUICK_REPLIES
                }, modifier = Modifier.padding(start = 8.dp)) { Text("Reset to defaults") }
            }

            item { SectionTitle("Speed dial (long-press a key on the keypad)") }
            item {
                ListItem(headlineContent = { Text("1") }, supportingContent = { Text("Voicemail") })
            }
            items((2..9).toList()) { digit ->
                val entry = speedDials[digit]
                ListItem(
                    headlineContent = { Text("$digit") },
                    supportingContent = { Text(entry?.let { "${it.name} · ${it.number}" } ?: "Not set — tap to choose a contact") },
                    trailingContent = {
                        if (entry != null) IconButton(onClick = {
                            setSpeedDial(ctx, digit, null)
                            speedDials = speedDials + (digit to null)
                        }) { Icon(Icons.Filled.Close, "Clear") }
                    },
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { pickSpeedDial = digit }
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    editReply?.let { index ->
        var text by remember(index) { mutableStateOf(replies[index]) }
        AlertDialog(
            onDismissRequest = { editReply = null },
            title = { Text("Quick response") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = {
                    replies = replies.toMutableList().also { it[index] = text.trim() }
                    setQuickReplies(ctx, replies)
                    editReply = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editReply = null }) { Text("Cancel") } }
        )
    }
    pickSpeedDial?.let { digit ->
        ContactPickerDialog(
            title = "Speed dial $digit",
            contacts = contacts,
            onDismiss = { pickSpeedDial = null },
            onPick = { c ->
                val entry = SpeedDial(c.name, c.number)
                setSpeedDial(ctx, digit, entry)
                speedDials = speedDials + (digit to entry)
                pickSpeedDial = null
            }
        )
    }
}

/** The phone's own blocked list, shared with the system: blocked callers never ring. */
@Composable
fun BlockedScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var numbers by remember { mutableStateOf(emptyList<String>()) }
    var allowed by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }
    var newNumber by remember { mutableStateOf("") }
    LaunchedEffect(reload) {
        val (can, list) = withContext(Dispatchers.IO) { canBlock(ctx) to loadBlocked(ctx) }
        allowed = can
        numbers = list
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        ScreenHeader("Blocked numbers", onBack)
        if (!allowed) {
            Text(
                "Set VRK Dialer as the default Phone app to see and edit blocked numbers.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp)
            )
            return@Column
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newNumber, onValueChange = { newNumber = it }, singleLine = true,
                placeholder = { Text("Add a number") }, modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(enabled = newNumber.isNotBlank(), onClick = {
                if (blockNumber(ctx, newNumber.trim())) {
                    newNumber = ""
                    reload++
                } else toast(ctx, "Couldn't block that number")
            }) { Text("Block") }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (numbers.isEmpty()) item { EmptyHint("No blocked numbers") }
            items(numbers) { number ->
                ListItem(
                    headlineContent = { Text(number) },
                    trailingContent = {
                        TextButton(onClick = { unblockNumber(ctx, number); reload++ }) { Text("Unblock") }
                    }
                )
            }
        }
    }
}
