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
import androidx.compose.foundation.border
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
    data object Appearance : Screen
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
        screen = if (screen == Screen.Blocked || screen == Screen.Appearance) Screen.Settings else Screen.Home
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
            onAppearance = { screen = Screen.Appearance },
            onBack = { screen = Screen.Home }
        )
        Screen.Appearance -> AppearanceScreen(onBack = { screen = Screen.Settings })
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

fun recentDateLabel(timestamp: Long): String {
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
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick)
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
        trailingContent = { IconButton(onClick = onClick) { Icon(Icons.Filled.Call, "Call ${r.name ?: r.number}", tint = CallGreen, modifier = Modifier.size(20.dp)) } },
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).combinedClickable(onClick = onDetails, onLongClick = onDetails)
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
