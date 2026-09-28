package com.vrk.dialer

import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** One pager owns both tab selection and gestures, so a partial swipe never loses state. */
@Composable
fun DialerHome(incoming: String, contacts: List<Contact>, callLog: List<Recent>, sims: Map<String, String>,
    onDial: (String) -> Unit, onOpen: (Screen) -> Unit, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val pager = rememberPagerState(initialPage = 1, pageCount = { 4 })
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var number by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var missed by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var clear by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf<Int?>(null) }
    val titles = listOf("Phone", "Calls", "Contacts", "Favourites")
    val labels = listOf("Keypad", "Calls", "Contacts", "Favourites")
    val icons = listOf(Icons.Default.Dialpad, Icons.Default.Call, Icons.Default.Person, Icons.Default.Star)
    val index = remember(contacts) { buildT9Index(contacts) }
    val matches = remember(number, index) { if (number.isBlank()) emptyList() else index.filter { it.matches(number) }.map { it.contact }.take(3) }
    fun contactDetails(c: Contact) = onOpen(Screen.Details(c.number, c.name, c.photo))
    fun recentDetails(r: Recent) = onOpen(Screen.Details(r.number, r.name, r.photo))
    LaunchedEffect(incoming) { if (incoming.isNotBlank()) { number = incoming; pager.scrollToPage(0) } }
    LaunchedEffect(pager.settledPage) { if (pager.settledPage == 1) markMissedCallsRead(ctx) }
    BackHandler(pager.currentPage != 1 || search.isNotBlank()) {
        if (search.isNotBlank()) search = "" else scope.launch { pager.animateScrollToPage(1) }
    }
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(colors.primaryContainer.copy(alpha = .4f), colors.background, colors.background))).safeDrawingPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("P H O N E", color = colors.primary, style = MaterialTheme.typography.labelSmall)
                Text(titles[pager.currentPage], style = MaterialTheme.typography.headlineLarge)
            }
            if (pager.currentPage == 2) IconButton(onClick = { addToContacts(ctx, "") }) { Icon(Icons.Default.PersonAdd, "Create contact") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More options") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Settings") }, onClick = { menu = false; onOpen(Screen.Settings) })
                    DropdownMenuItem(text = { Text("Blocked numbers") }, onClick = { menu = false; onOpen(Screen.Blocked) })
                    DropdownMenuItem(text = { Text("Clear call history") }, onClick = { menu = false; clear = true })
                }
            }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.Top) { page ->
            when (page) {
                0 -> Box(Modifier.fillMaxSize()) { Column(
                    Modifier.align(Alignment.Center).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(number.ifEmpty { "Enter a number" }, fontSize = if (number.length > 14) 24.sp else 32.sp,
                        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().animateContentSize())
                    Row(horizontalArrangement = Arrangement.Center) {
                        if (number.isNotEmpty()) TextButton(onClick = { addToContacts(ctx, number) }) { Text("Add to contacts") }
                        TextButton(onClick = {
                            val clip = ctx.getSystemService(ClipboardManager::class.java).primaryClip
                            val value = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
                            number = value.filter { it.isDigit() || it in "+*#,;" }.take(100)
                        }) { Text("Paste") }
                    }
                    if (matches.isNotEmpty()) {
                        val c = matches.first()
                        ListItem(headlineContent = { Text(c.name, maxLines = 1) }, supportingContent = { Text(c.number) },
                            leadingContent = { Avatar(c.name, c.photo, 36.dp) },
                            trailingContent = { IconButton(onClick = { onDial(c.number) }) { Icon(Icons.Default.Call, "Call ${c.name}", tint = CallGreen) } },
                            modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { contactDetails(c) })
                    }
                    Spacer(Modifier.height(20.dp))
                    Dialpad(onKey = { if (number.length < 100) number += it }, onLongDigit = { key ->
                        if (number.isEmpty()) {
                            if (key == '1') callVoicemail(ctx) else {
                                val entry = speedDial(ctx, key.digitToInt())
                                if (entry != null) onDial(entry.number) else speed = key.digitToInt()
                            }
                        } else if (number.length < 100) number += key
                    })
                    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { callVoicemail(ctx) }) { Icon(Icons.Default.Voicemail, "Voicemail") }
                        RoundButton(Icons.Default.Call, null, CallGreen, Color.White, 72.dp, enabled = number.isNotEmpty() || callLog.isNotEmpty(), elevated = true, accessibilityLabel = "Call") {
                            if (number.isNotEmpty()) onDial(number) else number = callLog.firstOrNull()?.number.orEmpty()
                        }
                        IconButton(onClick = { number = number.dropLast(1) }, enabled = number.isNotEmpty()) { Icon(Icons.AutoMirrored.Filled.Backspace, "Delete digit") }
                    }
                    if (number.isNotEmpty()) TextButton(onClick = { number = "" }) { Text("Clear number") }
                } }
                else -> Column(Modifier.fillMaxSize()) {
                    if (page != 3) OutlinedTextField(value = search, onValueChange = { search = it }, singleLine = true,
                        placeholder = { Text(if (page == 1) "Search calls" else "Search contacts") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Default.Close, "Clear search") } },
                        shape = CircleShape, colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Transparent, unfocusedContainerColor = colors.primaryContainer.copy(alpha = .45f)),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
                    when (page) {
                        1 -> {
                            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(!missed, { missed = false }, label = { Text("All calls") }, shape = CircleShape)
                                FilterChip(missed, { missed = true }, label = { Text("Missed") }, shape = CircleShape)
                            }
                            val rows = callLog.filter { (!missed || isMissed(it.type)) && (search.isBlank() || it.name?.contains(search, true) == true || it.number.contains(search)) }
                            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                                if (rows.isEmpty()) item { EmptyHint("No calls to show") }
                                rows.groupBy { recentDateLabel(it.date) }.forEach { (day, entries) ->
                                    item { SectionCaption(day) }
                                    items(entries.grouped()) { r -> RecentRow(r, sims, { recentDetails(r) }, { onDial(r.number) }) }
                                }
                            }
                        }
                        2 -> {
                            val rows = contacts.filter { search.isBlank() || it.name.contains(search, true) || it.number.contains(search) }
                            LazyColumn(contentPadding = PaddingValues(16.dp)) {
                                if (rows.isEmpty()) item { EmptyHint("No matching contacts") }
                                rows.groupBy { it.name.firstOrNull()?.uppercaseChar() ?: '#' }.toSortedMap().forEach { (letter, entries) ->
                                    item { SectionCaption(letter.toString()) }
                                    items(entries) { c -> ContactRow(c, { onDial(c.number) }, { contactDetails(c) }) }
                                }
                            }
                        }
                        3 -> {
                            Text("Your people, a tap away", color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                            val favorites = contacts.filter { it.starred }.distinctBy { it.number }
                            if (favorites.isEmpty()) EmptyHint("Star a contact in your Contacts app to add them here.")
                            LazyVerticalGrid(GridCells.Adaptive(150.dp), contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(favorites) { c ->
                                    Column(Modifier.clip(RoundedCornerShape(28.dp)).background(if (c.name.hashCode() % 2 == 0) colors.primaryContainer.copy(alpha = .6f) else colors.secondaryContainer.copy(alpha = .7f)).clickable { contactDetails(c) }.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Avatar(c.name, c.photo, 72.dp)
                                        Spacer(Modifier.height(12.dp))
                                        Text(c.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
                                            RoundButton(Icons.Default.Call, null, CallGreen, Color.White, 48.dp, accessibilityLabel = "Call ${c.name}") { onDial(c.number) }
                                            RoundButton(Icons.Default.Sms, null, colors.surface, colors.primary, 48.dp, accessibilityLabel = "Message ${c.name}") { sendMessage(ctx, c.number) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        NavigationBar(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).clip(RoundedCornerShape(30.dp)), containerColor = colors.surface, windowInsets = WindowInsets(0, 0, 0, 0)) {
            labels.forEachIndexed { i, label ->
                NavigationBarItem(selected = pager.currentPage == i, onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    scope.launch { pager.animateScrollToPage(i) }
                }, icon = { Icon(icons[i], if (i == 0) "Open keypad" else null) }, label = { Text(label, fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = colors.primary, selectedIconColor = colors.onPrimary, selectedTextColor = colors.primary))
            }
        }
    }
    if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text("Clear call history?") }, text = { Text("All recent calls will be deleted from this phone.") },
        confirmButton = { TextButton(onClick = { clear = false; if (!clearCallLog(ctx)) toast(ctx, "Set Phone as your default phone app"); onChanged() }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { clear = false }) { Text("Cancel") } })
    speed?.let { digit -> ContactPickerDialog("Speed dial $digit", contacts, { speed = null }) { c -> setSpeedDial(ctx, digit, SpeedDial(c.name, c.number)); speed = null } }
}
