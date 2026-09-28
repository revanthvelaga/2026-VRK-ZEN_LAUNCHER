package com.vrk.dialer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One number: call, message, copy, save/open contact, block, and its full call history. */
@Composable
fun DetailsScreen(
    number: String,
    name: String?,
    photo: String?,
    history: List<Recent>,
    sims: Map<String, String>,
    onDial: (String) -> Unit,
    onBack: () -> Unit,
    onChanged: () -> Unit
) {
    val ctx = LocalContext.current
    var blocked by remember { mutableStateOf(false) }
    var contactUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(number) {
        val (b, uri) = withContext(Dispatchers.IO) { isBlocked(ctx, number) to contactUriFor(ctx, number) }
        blocked = b
        contactUri = uri
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Contact details", style = MaterialTheme.typography.titleMedium)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(MaterialTheme.colorScheme.surface).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(name, photo, size = 104.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(name ?: number, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    if (name != null) Text(number, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (blocked) Text("Blocked", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        RoundButton(Icons.Filled.Call, "Call", CallGreen, Color.White, 56.dp) { onDial(number) }
                        RoundButton(Icons.Filled.Sms, "Message", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, 56.dp) {
                            sendMessage(ctx, number)
                        }
                        RoundButton(Icons.Filled.ContentCopy, "Copy", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, 56.dp) {
                            ctx.getSystemService(ClipboardManager::class.java)
                                .setPrimaryClip(ClipData.newPlainText("Phone number", number))
                            toast(ctx, "Number copied")
                        }
                        val uri = contactUri
                        if (uri != null) {
                            RoundButton(Icons.Filled.Person, "Contact", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, 56.dp) {
                                startSafely(ctx, Intent(Intent.ACTION_VIEW, uri))
                            }
                        } else {
                            RoundButton(Icons.Filled.PersonAdd, "Save", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, 56.dp) {
                                addToContacts(ctx, number)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
            item {
                ListItem(
                    headlineContent = { Text(if (blocked) "Unblock number" else "Block number", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Filled.Block, null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable {
                        val ok = if (blocked) unblockNumber(ctx, number) else blockNumber(ctx, number)
                        if (ok) {
                            blocked = !blocked
                            toast(ctx, if (blocked) "Calls from $number will be blocked" else "$number unblocked")
                        } else toast(ctx, "Set VRK Dialer as the default Phone app to block numbers")
                    }
                )
            }
            if (history.isNotEmpty()) {
                item {
                    ListItem(
                        headlineContent = { Text("Delete call history") },
                        leadingContent = { Icon(Icons.Filled.Delete, null) },
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { confirmDelete = true }
                    )
                }
                item {
                    Text(
                        "Call history", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp)
                    )
                }
            }
            item {
                Text("${history.size} calls · ${formatDuration(history.sumOf { it.duration })} total",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            }
            items(history.sortedByDescending { it.date }) { call ->
                val missed = isMissed(call.type)
                ListItem(
                    headlineContent = {
                        Text(callTypeLabel(call.type), color = if (missed) CallRed else Color.Unspecified)
                    },
                    supportingContent = {
                        Text(
                            listOfNotNull(
                                DateUtils.formatDateTime(
                                    ctx, call.date,
                                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH
                                ),
                                call.duration.takeIf { it > 0 }?.let(::formatDuration),
                                call.accountId?.let(sims::get)
                            ).joinToString(" · ")
                        )
                    },
                    leadingContent = {
                        Icon(callTypeIcon(call.type), null, tint = if (missed) CallRed else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete call history?") },
            text = { Text("All ${history.size} calls with ${name ?: number} will be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    if (deleteCalls(ctx, history.flatMap { it.ids })) onChanged()
                    else toast(ctx, "Set VRK Dialer as the default Phone app to delete calls")
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

/** "1 min 05 sec", "42 sec" — the way MIUI's call history shows it. */
fun formatDuration(seconds: Long): String = when {
    seconds >= 3600 -> "%d hr %d min".format(seconds / 3600, seconds / 60 % 60)
    seconds >= 60 -> "%d min %02d sec".format(seconds / 60, seconds % 60)
    else -> "$seconds sec"
}
