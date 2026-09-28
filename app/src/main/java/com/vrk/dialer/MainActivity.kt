package com.vrk.dialer

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val dialNumber = mutableStateOf("")

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    private val roleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { askPermissions() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dialNumber.value = numberFrom(intent)
        if (savedInstanceState == null) ensureDefaultDialer()
        setContent {
            DialerTheme {
                Surface(Modifier.fillMaxSize()) {
                    DialerApp(dialNumber.value, onSetDefault = ::ensureDefaultDialer)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        numberFrom(intent).takeIf { it.isNotEmpty() }?.let { dialNumber.value = it }
    }

    private fun numberFrom(i: Intent?) = i?.data?.schemeSpecificPart.orEmpty()

    /** Shows the system "Set as default Phone app?" prompt. */
    private fun ensureDefaultDialer() {
        val rm = getSystemService(RoleManager::class.java)
        if (rm.isRoleAvailable(RoleManager.ROLE_DIALER) && !rm.isRoleHeld(RoleManager.ROLE_DIALER)) {
            roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER))
        } else askPermissions()
    }

    private fun askPermissions() = permLauncher.launch(
        listOfNotNull(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.READ_PHONE_STATE,
            // Android 13+: without it there's no incoming-call heads-up (see CallService).
            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null
        ).toTypedArray()
    )
}

/** Where you are in the app; Back always returns towards Home. */
sealed interface Screen {
    data object Home : Screen
    data class Details(val number: String, val name: String?, val photo: String?) : Screen
    data object Settings : Screen
    data object Blocked : Screen
}

@Composable
fun DialerApp(incoming: String, onSetDefault: () -> Unit) {
    val ctx = LocalContext.current
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var contacts by remember { mutableStateOf(emptyList<Contact>()) }
    var callLog by remember { mutableStateOf(emptyList<Recent>()) }
    var sims by remember { mutableStateOf(emptyMap<String, String>()) }
    var reload by remember { mutableIntStateOf(0) }

    // Reload every time the screen comes back (after a call, after granting permissions)
    LifecycleResumeEffect(Unit) {
        reload++
        onPauseOrDispose { }
    }
    // ...and the moment the call log or contacts actually change, even while this screen
    // stays in the foreground behind the in-call UI (a call ending writes a new row here).
    // Debounced: a sync can fire dozens of contact updates in a burst.
    DisposableEffect(Unit) {
        val handler = Handler(Looper.getMainLooper())
        var pending: Runnable? = null
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                pending?.let(handler::removeCallbacks)
                pending = Runnable { reload++ }.also { handler.postDelayed(it, 400) }
            }
        }
        ctx.contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer)
        ctx.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer)
        onDispose {
            pending?.let(handler::removeCallbacks)
            ctx.contentResolver.unregisterContentObserver(observer)
        }
    }
    LaunchedEffect(reload) {
        val loaded = withContext(Dispatchers.IO) { Triple(loadContacts(ctx), loadCallLog(ctx), simLabels(ctx)) }
        contacts = loaded.first
        callLog = loaded.second
        sims = loaded.third
    }

    var pendingSimCall by remember { mutableStateOf<String?>(null) }
    fun dial(number: String) {
        // Dual SIM with "ask every time" -> show our own SIM picker
        if (simAccounts(ctx).size > 1 && defaultSim(ctx) == null) pendingSimCall = number
        else placeCall(ctx, number, null)
    }

    BackHandler(enabled = screen != Screen.Home) {
        screen = if (screen == Screen.Blocked) Screen.Settings else Screen.Home
    }

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            val forward = targetState !is Screen.Home && initialState is Screen.Home
            val backward = targetState is Screen.Home && initialState !is Screen.Home
            when {
                forward -> slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(220)) togetherWith
                    fadeOut(tween(160))
                backward -> fadeIn(tween(200)) togetherWith
                    slideOutHorizontally(tween(260)) { it / 3 } + fadeOut(tween(180))
                else -> fadeIn(tween(200)) togetherWith fadeOut(tween(150))
            }
        },
        label = "screen"
    ) { s ->
    when (s) {
        Screen.Home -> DialerHome(
            incoming = incoming,
            contacts = contacts,
            callLog = callLog,
            sims = sims,
            onDial = ::dial,
            onOpen = { screen = it },
            onChanged = { reload++ }
        )
        is Screen.Details -> DetailsScreen(
            number = s.number, name = s.name, photo = s.photo,
            history = callLog.filter { it.number.numberKey() == s.number.numberKey() },
            sims = sims,
            onDial = ::dial,
            onBack = { screen = Screen.Home },
            onChanged = { reload++ }
        )
        Screen.Settings -> SettingsScreen(
            contacts = contacts,
            onSetDefault = onSetDefault,
            onBlocked = { screen = Screen.Blocked },
            onBack = { screen = Screen.Home }
        )
        Screen.Blocked -> BlockedScreen(onBack = { screen = Screen.Settings })
    }
    }

    pendingSimCall?.let { number ->
        val simHandles = remember(number) { simAccounts(ctx) }
        AlertDialog(
            onDismissRequest = { pendingSimCall = null },
            confirmButton = {},
            title = { Text("Call $number with") },
            text = {
                Column {
                    simHandles.forEach { h ->
                        TextButton(onClick = { pendingSimCall = null; placeCall(ctx, number, h) }) {
                            Text(simLabel(ctx, h), fontSize = 18.sp)
                        }
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialerHome(
    incoming: String,
    contacts: List<Contact>,
    callLog: List<Recent>,
    sims: Map<String, String>,
    onDial: (String) -> Unit,
    onOpen: (Screen) -> Unit,
    onChanged: () -> Unit
) {
    val ctx = LocalContext.current
    val focusManager = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var padOpen by rememberSaveable { mutableStateOf(false) }
    var missedOnly by rememberSaveable { mutableStateOf(false) }
    var contactSearch by rememberSaveable { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var assignSpeedDial by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(incoming) { if (incoming.isNotEmpty()) { query = incoming; padOpen = true } }
    // Looking at Recents clears the "missed call" notification, like the stock app.
    LaunchedEffect(tab) { if (tab == 0) markMissedCallsRead(ctx) }

    val recents = remember(callLog, missedOnly, contactSearch) {
        val search = contactSearch.trim()
        callLog.filter { (!missedOnly || isMissed(it.type)) &&
            (search.isEmpty() || it.name?.contains(search, ignoreCase = true) == true ||
                it.number.digits().contains(search.digits().ifEmpty { "\u0000" })) }
            .groupBy { recentDateLabel(it.date) }.values.flatMap { it.grouped() }
    }
    val favorites = remember(contacts) { contacts.filter { it.starred }.distinctBy { it.name } }
    // Built once per contact list change, not once per keystroke: T9 needs every contact's
    // name normalized and split into words, which is wasted work to redo on every digit.
    val t9Index = remember(contacts) { buildT9Index(contacts) }
    // Explicit type on both the emptyList() branch and the val: three chained generic calls
    // (filter/map/take) alongside a bare emptyList() branch is more than this Kotlin
    // compiler version's local type inference resolves on its own.
    val contactMatches: List<Contact> = remember(query, t9Index) {
        if (query.isEmpty()) emptyList<Contact>() else t9Index.filter { it.matches(query) }.map { it.contact }.take(60)
    }
    val numberMatches = remember(query, callLog) {
        if (query.isEmpty()) emptyList()
        else callLog.filter { it.name == null && it.number.digits().contains(query) }
            .distinctBy { it.number.digits() }.take(10)
    }
    val filteredContacts = remember(contactSearch, contacts) {
        val q = contactSearch.trim()
        if (q.isEmpty()) contacts
        else contacts.filter { it.name.contains(q, ignoreCase = true) || it.number.digits().contains(q.digits().ifEmpty { "\u0000" }) }
    }
    // A–Z sections, '#' (digits, symbols, other scripts) last — grouped so each header
    // appears exactly once (list keys must be unique).
    val contactSections = remember(filteredContacts) {
        filteredContacts.groupBy { c -> c.name.firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' } ?: '#' }
            .toSortedMap(compareBy<Char> { if (it == '#') Char.MAX_VALUE else it })
    }

    // MIUI behaviour: scrolling the list hides the keypad
    val listState = rememberLazyListState()
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && query.isEmpty()) padOpen = false
    }

    fun details(number: String, name: String?, photo: String?) = onOpen(Screen.Details(number, name, photo))

    val palette = MaterialTheme.colorScheme
    BackHandler(enabled = padOpen || contactSearch.isNotEmpty() || tab != 0) {
        when {
            padOpen -> { padOpen = false; query = "" }
            contactSearch.isNotEmpty() -> contactSearch = ""
            else -> tab = 0
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(palette.background).safeDrawingPadding().imePadding()) {
        val keypadMaxHeight = maxHeight * 0.76f
        val compact = maxHeight < 500.dp
        Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (!compact) Text("VRK PHONE", style = MaterialTheme.typography.labelMedium, color = palette.primary, letterSpacing = 2.sp)
                Text(if (padOpen) "Keypad" else listOf("Calls", "Contacts", "Favourites")[tab],
                    style = if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge)
            }
            if (compact && !padOpen) IconButton(onClick = { focusManager.clearFocus(); contactSearch = ""; padOpen = true }) {
                Icon(Icons.Filled.Dialpad, "Open keypad", tint = palette.primary)
            }
            if (tab == 1 && !padOpen) IconButton(onClick = { addToContacts(ctx, "") }) {
                Icon(Icons.Filled.PersonAdd, "Create contact", tint = palette.primary)
            }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "More options") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Settings") }, onClick = { menuOpen = false; onOpen(Screen.Settings) })
                    DropdownMenuItem(text = { Text("Blocked numbers") }, onClick = { menuOpen = false; onOpen(Screen.Blocked) })
                    DropdownMenuItem(text = { Text("Clear call history") }, onClick = { menuOpen = false; confirmClear = true })
                }
            }
        }
        if (!padOpen) {
            OutlinedTextField(
                value = contactSearch, onValueChange = { contactSearch = it },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (contactSearch.isNotEmpty()) IconButton(onClick = { contactSearch = "" }) {
                    Icon(Icons.Filled.Close, "Clear search")
                } },
                placeholder = { Text(if (tab == 0) "Search calls or a number" else "Search your contacts") },
                singleLine = true, shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.Transparent, focusedBorderColor = palette.primary,
                    unfocusedContainerColor = palette.surfaceVariant, focusedContainerColor = palette.surface),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            )
            if (tab == 0) Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !missedOnly, onClick = { missedOnly = false }, label = { Text("All calls") }, shape = RoundedCornerShape(16.dp))
                FilterChip(selected = missedOnly, onClick = { missedOnly = true }, label = { Text("Missed") }, shape = RoundedCornerShape(16.dp))
            } else Spacer(Modifier.height(16.dp))
        }
        Box(Modifier.weight(1f)) {
            if (query.isEmpty() && tab == 2 && !padOpen) {
                val shownFavorites = remember(favorites, contactSearch) {
                    val search = contactSearch.trim()
                    favorites.filter { search.isEmpty() || it.name.contains(search, ignoreCase = true) ||
                        it.number.digits().contains(search.digits().ifEmpty { "\u0000" }) }
                }
                if (shownFavorites.isEmpty()) EmptyHint(if (contactSearch.isNotEmpty()) "No matching favourites" else "Your people, one tap away\nStar a contact to add them here.")
                LazyVerticalGrid(columns = GridCells.Adaptive(148.dp), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 88.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(shownFavorites, key = { it.name + it.number }) { c ->
                        Column(Modifier.clip(RoundedCornerShape(28.dp)).background(palette.surface)
                            .combinedClickable(onClick = { onDial(c.number) }, onLongClick = { details(c.number, c.name, c.photo) })
                            .padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Avatar(c.name, c.photo, size = 64.dp)
                            Spacer(Modifier.height(12.dp))
                            Text(c.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(c.number, style = MaterialTheme.typography.bodyMedium, color = palette.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = { details(c.number, c.name, c.photo) }) { Text("View contact") }
                        }
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = if (padOpen) 8.dp else 96.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    when {
                        query.isNotEmpty() -> {
                            item { SectionCaption("MATCHING CONTACTS") }
                            items(contactMatches) { c -> ContactRow(c, onCall = { onDial(c.number) }) { details(c.number, c.name, c.photo) } }
                            items(numberMatches) { r -> RecentRow(r, sims, onDetails = { details(r.number, r.name, r.photo) }) { onDial(r.number) } }
                            if (contactMatches.isEmpty()) {
                                item { ListItem(headlineContent = { Text("Create new contact") }, leadingContent = { Icon(Icons.Filled.PersonAdd, null) },
                                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { addToContacts(ctx, query) }) }
                                item { ListItem(headlineContent = { Text("Send message") }, leadingContent = { Icon(Icons.Filled.Sms, null) },
                                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { sendMessage(ctx, query) }) }
                            }
                        }
                        tab == 0 || padOpen -> {
                            if (recents.isEmpty()) item { EmptyHint(if (contactSearch.isNotEmpty()) "No matching calls" else if (missedOnly) "All caught up\nNo missed calls." else "Your conversations start here\nTap Keypad to make your first call.") }
                            recents.groupBy { recentDateLabel(it.date) }.forEach { (day, calls) ->
                                item { SectionCaption(day) }
                                items(calls) { r -> RecentRow(r, sims, onDetails = { details(r.number, r.name, r.photo) }) { onDial(r.number) } }
                            }
                        }
                        else -> {
                            contactSections.forEach { (letter, section) ->
                                item(key = "h$letter") { SectionCaption(letter.toString()) }
                                items(section) { c -> ContactRow(c, onCall = { onDial(c.number) }) { details(c.number, c.name, c.photo) } }
                            }
                            if (filteredContacts.isEmpty()) item { EmptyHint(if (contactSearch.isNotEmpty()) "No matching contacts" else "Your contacts will appear here\nAllow Contacts access in app settings.") }
                        }
                    }
                }
            }
            if (!padOpen && !compact) ExtendedFloatingActionButton(
                onClick = { focusManager.clearFocus(); contactSearch = ""; padOpen = true }, icon = { Icon(Icons.Filled.Dialpad, "Open keypad") }, text = { Text("Keypad") },
                containerColor = palette.primaryContainer, contentColor = palette.onPrimaryContainer,
                shape = RoundedCornerShape(24.dp), modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp))
        }
        if (padOpen) {
            Surface(modifier = Modifier.heightIn(max = keypadMaxHeight), shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), color = palette.surface, tonalElevation = 2.dp) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { padOpen = false; query = "" }) { Icon(Icons.Filled.KeyboardArrowDown, "Close keypad") }
                        Text(query.ifEmpty { "Enter a number" }, fontSize = if (query.isEmpty()) 20.sp else 30.sp,
                            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (query.isEmpty()) palette.onSurfaceVariant else palette.onSurface,
                            textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).combinedClickable(
                            onClick = {
                                if (query.isNotEmpty()) query = query.dropLast(1)
                                else {
                                    val clipboard = ctx.getSystemService(android.content.ClipboardManager::class.java)
                                    val pasted = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
                                    query = pasted.filter { it.isDigit() || it in "+*#,;" }.take(100)
                                    if (query.isEmpty()) toast(ctx, "No phone number to paste")
                                }
                            }, onLongClick = { query = "" }), contentAlignment = Alignment.Center) {
                            Icon(if (query.isEmpty()) Icons.Filled.ContentPaste else Icons.AutoMirrored.Filled.Backspace,
                                if (query.isEmpty()) "Paste number" else "Delete digit; hold to clear", tint = palette.onSurfaceVariant)
                        }
                    }
                    Dialpad(onKey = { query += it }, onLongDigit = { key ->
                        if (query.isEmpty()) {
                            if (key == '1') callVoicemail(ctx)
                            else {
                                val digit = key.digitToInt()
                                val entry = speedDial(ctx, digit)
                                if (entry != null) onDial(entry.number) else assignSpeedDial = digit
                            }
                        } else query += key
                    })
                    Button(onClick = { if (query.isNotEmpty()) onDial(query) else callLog.firstOrNull()?.let { query = it.number } },
                        enabled = query.isNotEmpty() || callLog.isNotEmpty(), shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 10.dp).height(56.dp)) {
                        Icon(Icons.Filled.Call, null)
                        Spacer(Modifier.width(12.dp))
                        Text(if (query.isEmpty()) "Last number" else "Call", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        } else {
            NavigationBar(containerColor = palette.background, tonalElevation = 0.dp, windowInsets = WindowInsets(0, 0, 0, 0)) {
                val icons = listOf(Icons.Filled.History, Icons.Filled.People, Icons.Filled.Star)
                listOf("Calls", "Contacts", "Favourites").forEachIndexed { index, label ->
                    NavigationBarItem(selected = tab == index, onClick = { focusManager.clearFocus(); tab = index; contactSearch = "" },
                        icon = { Icon(icons[index], null) }, label = { Text(label) })
                }
            }
        }
    }

    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear call history?") },
            text = { Text("All recent calls will be deleted from this phone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    if (!clearCallLog(ctx)) toast(ctx, "Set VRK Dialer as the default Phone app to delete calls")
                    onChanged()
                }) { Text("Clear", color = CallRed) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
    assignSpeedDial?.let { digit ->
        ContactPickerDialog(
            title = "Speed dial $digit",
            contacts = contacts,
            onDismiss = { assignSpeedDial = null },
            onPick = { c ->
                setSpeedDial(ctx, digit, SpeedDial(c.name, c.number))
                assignSpeedDial = null
                toast(ctx, "Long-press $digit to call ${c.name}")
            }
        )
    }
}

private fun recentDateLabel(timestamp: Long): String {
    val day = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = java.util.Calendar.getInstance()
    fun sameDay() = day.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) &&
        day.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR)
    if (sameDay()) return "Today"
    today.add(java.util.Calendar.DAY_OF_YEAR, -1)
    if (sameDay()) return "Yesterday"
    return java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(timestamp))
}

@Composable
fun SectionCaption(title: String) {
    Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 8.dp))
}

@Composable
fun EmptyHint(text: String) {
    Text(
        text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(48.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactRow(c: Contact, onCall: () -> Unit, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(c.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(c.number) },
        leadingContent = { Avatar(c.name, c.photo) },
        trailingContent = {
            IconButton(onClick = onCall) { Icon(Icons.Filled.Call, "Call ${c.name}", tint = MaterialTheme.colorScheme.primary) }
        },
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick)
    )
}

/** Tap calls back (MIUI/OxygenOS), ⓘ or long-press opens the number's details. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentRow(r: Recent, sims: Map<String, String>, onDetails: () -> Unit, onClick: () -> Unit) {
    val missed = isMissed(r.type)
    val title = (r.name ?: r.number) + if (r.count > 1) " (${r.count})" else ""
    val sim = r.accountId?.let(sims::get)
    ListItem(
        headlineContent = {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (missed) MaterialTheme.colorScheme.error else Color.Unspecified, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(callTypeIcon(r.type), null, Modifier.size(14.dp), tint = if (missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text(listOfNotNull(callTypeLabel(r.type), sim, DateUtils.formatDateTime(LocalContext.current, r.date, DateUtils.FORMAT_SHOW_TIME)).joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        leadingContent = { Avatar(r.name, r.photo) },
        trailingContent = { IconButton(onClick = onDetails) { Icon(Icons.Filled.Info, "Details") } },
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).combinedClickable(onClick = onClick, onLongClick = onDetails)
    )
}

fun callTypeIcon(type: Int): ImageVector = when (type) {
    CallLog.Calls.OUTGOING_TYPE -> Icons.AutoMirrored.Filled.CallMade
    CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> Icons.AutoMirrored.Filled.CallMissed
    CallLog.Calls.BLOCKED_TYPE -> Icons.Filled.Block
    else -> Icons.AutoMirrored.Filled.CallReceived
}

fun callTypeLabel(type: Int): String = when (type) {
    CallLog.Calls.INCOMING_TYPE -> "Incoming"
    CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
    CallLog.Calls.MISSED_TYPE -> "Missed"
    CallLog.Calls.REJECTED_TYPE -> "Declined"
    CallLog.Calls.BLOCKED_TYPE -> "Blocked"
    CallLog.Calls.VOICEMAIL_TYPE -> "Voicemail"
    else -> "Call"
}

@Composable
fun ContactPickerDialog(title: String, contacts: List<Contact>, onDismiss: () -> Unit, onPick: (Contact) -> Unit) {
    var search by remember { mutableStateOf("") }
    val shown = remember(search, contacts) {
        contacts.filter { search.isBlank() || it.name.contains(search.trim(), ignoreCase = true) }.take(100)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = search, onValueChange = { search = it }, singleLine = true,
                    placeholder = { Text("Search contacts") }, modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(shown) { c ->
                        ListItem(
                            headlineContent = { Text(c.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(c.number) },
                            leadingContent = { Avatar(c.name, c.photo, size = 36.dp) },
                            modifier = Modifier.clickable { onPick(c) }
                        )
                    }
                }
            }
        }
    )
}

// ---------- Hand-offs to other apps ----------

fun sendMessage(ctx: android.content.Context, number: String) = startSafely(ctx, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}")))

fun addToContacts(ctx: android.content.Context, number: String) = startSafely(
    ctx,
    Intent(Intent.ACTION_INSERT_OR_EDIT).setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE)
        .putExtra(ContactsContract.Intents.Insert.PHONE, number)
)

fun openCallSettings(ctx: android.content.Context) = startSafely(ctx, Intent(TelecomManager.ACTION_SHOW_CALL_SETTINGS))

fun startSafely(ctx: android.content.Context, intent: Intent) {
    runCatching { ctx.startActivity(intent) }.onFailure { toast(ctx, "No app found for that") }
}

fun toast(ctx: android.content.Context, text: String) =
    android.widget.Toast.makeText(ctx, text, android.widget.Toast.LENGTH_SHORT).show()
