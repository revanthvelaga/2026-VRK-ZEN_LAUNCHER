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
    // Built once per contact list change, not once per keystroke: T9 needs every contact's
    // name normalized and split into words, which is wasted work to redo on every digit.
    val t9Index = remember(contacts) { buildT9Index(contacts) }

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
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var padOpen by rememberSaveable { mutableStateOf(true) }
    var missedOnly by rememberSaveable { mutableStateOf(false) }
    var contactSearch by rememberSaveable { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var assignSpeedDial by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(incoming) { if (incoming.isNotEmpty()) { query = incoming; padOpen = true } }
    // Looking at Recents clears the "missed call" notification, like the stock app.
    LaunchedEffect(tab) { if (tab == 0) markMissedCallsRead(ctx) }

    val recents = remember(callLog, missedOnly) {
        (if (missedOnly) callLog.filter { isMissed(it.type) } else callLog).grouped()
    }
    val favorites = remember(contacts) { contacts.filter { it.starred }.distinctBy { it.name } }
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

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        if (query.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TabRow(selectedTabIndex = tab, modifier = Modifier.weight(1f)) {
                    listOf("Recents", "Contacts", "Favourites").forEachIndexed { i, title ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
                    }
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Settings") }, onClick = { menuOpen = false; onOpen(Screen.Settings) })
                        DropdownMenuItem(text = { Text("Blocked numbers") }, onClick = { menuOpen = false; onOpen(Screen.Blocked) })
                        DropdownMenuItem(text = { Text("Clear call history") }, onClick = { menuOpen = false; confirmClear = true })
                    }
                }
            }
            if (tab == 0) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !missedOnly, onClick = { missedOnly = false }, label = { Text("All") })
                    FilterChip(selected = missedOnly, onClick = { missedOnly = true }, label = { Text("Missed") })
                }
            }
            if (tab == 1) {
                OutlinedTextField(
                    value = contactSearch, onValueChange = { contactSearch = it },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    placeholder = { Text("Search ${contacts.size} contacts") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        Box(Modifier.weight(1f)) {
            if (query.isEmpty() && tab == 2) {
                if (favorites.isEmpty()) {
                    Text(
                        "No favourites yet.\nStar a contact in your Contacts app to pin it here.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center).padding(32.dp)
                    )
                }
                LazyVerticalGrid(columns = GridCells.Fixed(3), contentPadding = PaddingValues(16.dp)) {
                    items(favorites, key = { it.name + it.number }) { c ->
                        Column(
                            Modifier.padding(8.dp).combinedClickable(
                                onClick = { onDial(c.number) },
                                onLongClick = { details(c.number, c.name, c.photo) }
                            ),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Avatar(c.name, c.photo, size = 72.dp)
                            Spacer(Modifier.height(6.dp))
                            Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    when {
                        query.isNotEmpty() -> {
                            item {
                                ListItem(
                                    headlineContent = { Text("Call $query", color = CallGreen) },
                                    leadingContent = { Icon(Icons.Filled.Call, null, tint = CallGreen) },
                                    modifier = Modifier.clickable { onDial(query) }
                                )
                            }
                            items(contactMatches) { c -> ContactRow(c, onCall = { onDial(c.number) }) { details(c.number, c.name, c.photo) } }
                            items(numberMatches) { r -> RecentRow(r, sims, onDetails = { details(r.number, r.name, r.photo) }) { onDial(r.number) } }
                            if (contactMatches.isEmpty()) {
                                item {
                                    ListItem(
                                        headlineContent = { Text("Create new contact") },
                                        leadingContent = { Icon(Icons.Filled.PersonAdd, null) },
                                        modifier = Modifier.clickable { addToContacts(ctx, query) }
                                    )
                                }
                                item {
                                    ListItem(
                                        headlineContent = { Text("Send message") },
                                        leadingContent = { Icon(Icons.Filled.Sms, null) },
                                        modifier = Modifier.clickable { sendMessage(ctx, query) }
                                    )
                                }
                            }
                        }
                        tab == 0 -> {
                            if (recents.isEmpty()) item { EmptyHint(if (missedOnly) "No missed calls" else "No calls yet") }
                            items(recents) { r -> RecentRow(r, sims, onDetails = { details(r.number, r.name, r.photo) }) { onDial(r.number) } }
                        }
                        else -> {
                            contactSections.forEach { (letter, section) ->
                                item(key = "h$letter") {
                                    Text(
                                        letter.toString(), color = CallGreen, fontSize = 13.sp,
                                        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp)
                                    )
                                }
                                items(section) { c ->
                                    ContactRow(c, onCall = { onDial(c.number) }) { details(c.number, c.name, c.photo) }
                                }
                            }
                            if (filteredContacts.isEmpty()) item { EmptyHint("No contacts") }
                        }
                    }
                }
            }
        }

        if (padOpen) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    query, fontSize = 30.sp, maxLines = 1, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                if (query.isNotEmpty()) {
                    Icon(
                        Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete",
                        modifier = Modifier.padding(8.dp).combinedClickable(
                            onClick = { query = query.dropLast(1) },
                            onLongClick = { query = "" }
                        )
                    )
                }
            }
            Dialpad(
                onKey = { query += it },
                modifier = Modifier.padding(horizontal = 24.dp),
                onLongDigit = { key ->
                    // Only on an empty number, so a long press mid-number doesn't dial someone.
                    if (query.isEmpty()) {
                        if (key == '1') callVoicemail(ctx)
                        else {
                            val digit = key.digitToInt()
                            val entry = speedDial(ctx, digit)
                            if (entry != null) onDial(entry.number) else assignSpeedDial = digit
                        }
                    } else query += key
                }
            )
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { padOpen = false }) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Hide keypad")
                }
                RoundButton(Icons.Filled.Call, null, CallGreen, Color.White) {
                    // Empty + call button = bring back last number (MIUI habit)
                    if (query.isNotEmpty()) onDial(query) else callLog.firstOrNull()?.let { query = it.number }
                }
                Spacer(Modifier.size(48.dp))
            }
        } else {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                RoundButton(Icons.Filled.Dialpad, null, CallGreen, Color.White) { padOpen = true }
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
        headlineContent = { Text(c.name) },
        supportingContent = { Text(c.number) },
        leadingContent = { Avatar(c.name, c.photo) },
        trailingContent = {
            IconButton(onClick = onCall) { Icon(Icons.Filled.Call, "Call", tint = CallGreen) }
        },
        modifier = Modifier.clickable(onClick = onClick)
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
            Text(title, color = if (missed) CallRed else Color.Unspecified, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(callTypeIcon(r.type), null, Modifier.size(14.dp), tint = if (missed) CallRed else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text(listOfNotNull(sim, DateUtils.getRelativeTimeSpanString(r.date).toString()).joinToString(" · "), maxLines = 1)
            }
        },
        leadingContent = { Avatar(r.name, r.photo) },
        trailingContent = { IconButton(onClick = onDetails) { Icon(Icons.Filled.Info, "Details") } },
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onDetails)
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
                            headlineContent = { Text(c.name) },
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
