package com.vrk.dialer

import android.content.ClipboardManager
import android.telecom.PhoneAccountHandle
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun BrandMark() {
    Box(Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(Brush.linearGradient(
        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.ic_phone_foreground), null, tint = Color.White, modifier = Modifier.size(28.dp))
    }
}

@Composable fun ContactPortrait(name: String?, photo: String?, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var image by remember(photo) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(photo) { image = photo?.let { withContext(Dispatchers.IO) { loadPhoto(ctx, it) } } }
    Box(modifier.background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer))), contentAlignment = Alignment.Center) {
        val bitmap = image
        if (bitmap != null) Image(bitmap, name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Avatar(name, size = 76.dp)
    }
}

@Composable fun PremiumKeypad(number: String, setNumber: (String) -> Unit, matches: List<Contact>,
    calls: List<Recent>, sims: Map<String, String>, onDial: (String) -> Unit,
    onRecent: (Recent) -> Unit, onContact: (Contact) -> Unit, onSpeed: (Int) -> Unit) {
    val ctx = LocalContext.current
    val theme = LocalAppearance.current.theme
    val colors = MaterialTheme.colorScheme
    var expanded by rememberSaveable { mutableStateOf(true) }
    var simMenu by remember { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    // Read again on composition: the default/available SIM can change after returning from settings.
    val accounts = simAccounts(ctx)
    val selected = accounts.firstOrNull { it.id == selectedId }
    val default = defaultSim(ctx)
    val simText = (selected ?: default)?.let { simLabel(ctx, it) }
        ?: if (accounts.size > 1) "Ask each time" else accounts.firstOrNull()?.let { simLabel(ctx, it) } ?: "Default SIM"
    fun call(handle: PhoneAccountHandle? = selected) {
        if (number.isEmpty()) { setNumber(calls.firstOrNull()?.number.orEmpty()); return }
        if (handle != null) placeCall(ctx, number, handle) else onDial(number)
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val minHeight = maxHeight
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = minHeight),
            verticalArrangement = Arrangement.Bottom) {
            if (theme != PhoneTheme.SAPPHIRE && number.isEmpty()) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SectionCaption("Recent calls")
                    if (calls.isEmpty()) Text("Your recent calls will appear here", color = colors.onSurfaceVariant, modifier = Modifier.padding(12.dp))
                    calls.take(if (expanded) 2 else 8).forEach { r -> RecentRow(r, sims, { onRecent(r) }, { onDial(r.number) }) }
                }
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(if (theme == PhoneTheme.SAPPHIRE) Color.Transparent else colors.surface)
                .animateContentSize().padding(horizontal = 24.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (theme != PhoneTheme.SAPPHIRE) TextButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.Dialpad, null)
                    Spacer(Modifier.width(8.dp)); Text(if (expanded) "Hide keypad" else "Show keypad")
                }
                if (expanded || theme == PhoneTheme.SAPPHIRE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(number.ifEmpty { "Enter a number" }, fontSize = if (number.length > 14) 24.sp else 30.sp,
                            textAlign = TextAlign.Center, modifier = Modifier.weight(1f), maxLines = 3)
                        IconButton(onClick = { setNumber(number.dropLast(1)) }, enabled = number.isNotEmpty()) {
                            Icon(Icons.AutoMirrored.Filled.Backspace, "Delete digit")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.Center) {
                        if (number.isNotEmpty()) {
                            TextButton(onClick = { addToContacts(ctx, number) }) { Text("Add contact") }
                            TextButton(onClick = { setNumber("") }) { Text("Clear number") }
                        } else TextButton(onClick = { callVoicemail(ctx) }) { Text("Voicemail") }
                        TextButton(onClick = {
                            val clip = ctx.getSystemService(ClipboardManager::class.java).primaryClip
                            val value = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
                            setNumber(value.filter { it.isDigit() || it in "+*#,;" }.take(100))
                        }) { Text("Paste") }
                    }
                    matches.take(1).forEach { c ->
                        ListItem(headlineContent = { Text(c.name) }, supportingContent = { Text(c.number) },
                            leadingContent = { Avatar(c.name, c.photo, 36.dp) },
                            trailingContent = { IconButton(onClick = { onDial(c.number) }) { Icon(Icons.Default.Call, "Call ${c.name}", tint = CallGreen) } },
                            modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { onContact(c) })
                    }
                    Dialpad(onKey = { if (number.length < 100) setNumber(number + it) }, onLongDigit = { digit ->
                        if (number.isEmpty()) {
                            if (digit == '1') callVoicemail(ctx) else {
                                val entry = speedDial(ctx, digit.digitToInt())
                                if (entry != null) onDial(entry.number) else onSpeed(digit.digitToInt())
                            }
                        } else if (number.length < 100) setNumber(number + digit)
                    })
                    Box {
                        TextButton(onClick = { simMenu = true }) {
                            Icon(Icons.Default.SimCard, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                            Text(simText); Icon(Icons.Default.KeyboardArrowDown, null)
                        }
                        DropdownMenu(simMenu, { simMenu = false }) {
                            DropdownMenuItem(text = { Text("Use system preference") }, onClick = { selectedId = null; simMenu = false })
                            accounts.forEach { account -> DropdownMenuItem(text = { Text(simLabel(ctx, account)) },
                                onClick = { selectedId = account.id; simMenu = false }) }
                            DropdownMenuItem(text = { Text("SIM settings") }, onClick = { simMenu = false; openCallSettings(ctx) })
                        }
                    }
                    if (theme == PhoneTheme.FLOW && accounts.size > 1) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            accounts.take(2).forEach { account -> Button(onClick = { call(account) },
                                enabled = number.isNotBlank() || calls.isNotEmpty(), colors = ButtonDefaults.buttonColors(containerColor = CallGreen),
                                modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                                Icon(Icons.Default.Call, null); Spacer(Modifier.width(6.dp)); Text(simLabel(ctx, account))
                            } }
                        }
                    } else Button(onClick = { call() }, enabled = number.isNotBlank() || calls.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = CallGreen, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { contentDescription = "Call" }, shape = CircleShape) {
                        Icon(Icons.Default.Call, null); Spacer(Modifier.width(10.dp)); Text("Call", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
