package com.vrk.dialer

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.os.Bundle
import android.provider.CallLog
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
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
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { DialerHome(dialNumber.value) }
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
        arrayOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_PHONE_STATE
        )
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialerHome(incoming: String) {
    val ctx = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var padOpen by rememberSaveable { mutableStateOf(true) }
    var pendingSimCall by remember { mutableStateOf<String?>(null) }
    var contacts by remember { mutableStateOf(emptyList<Contact>()) }
    var recents by remember { mutableStateOf(emptyList<Recent>()) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(incoming) { if (incoming.isNotEmpty()) { query = incoming; padOpen = true } }

    // Reload every time the screen comes back (after a call, after granting permissions)
    LifecycleResumeEffect(Unit) { reload++; onPauseOrDispose { } }
    LaunchedEffect(reload) {
        val (c, r) = withContext(Dispatchers.IO) { loadContacts(ctx) to loadRecents(ctx) }
        contacts = c; recents = r
    }

    fun dial(number: String) {
        // Dual SIM with "ask every time" -> show our own SIM picker
        if (simAccounts(ctx).size > 1 && defaultSim(ctx) == null) pendingSimCall = number
        else placeCall(ctx, number, null)
    }

    val contactMatches = remember(query, contacts) {
        if (query.isEmpty()) emptyList() else contacts.filter { t9Match(query, it.name, it.number) }.take(60)
    }
    val numberMatches = remember(query, recents) {
        if (query.isEmpty()) emptyList()
        else recents.filter { it.name == null && it.number.digits().contains(query) }
            .distinctBy { it.number.digits() }.take(10)
    }

    // MIUI behaviour: scrolling the list hides the keypad
    val listState = rememberLazyListState()
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && query.isEmpty()) padOpen = false
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        if (query.isEmpty()) {
            TabRow(selectedTabIndex = tab) {
                listOf("Recents", "Contacts").forEachIndexed { i, title ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
                }
            }
        }

        LazyColumn(Modifier.weight(1f), state = listState) {
            when {
                query.isNotEmpty() -> {
                    item {
                        ListItem(
                            headlineContent = { Text("Call $query", color = CallGreen) },
                            modifier = Modifier.clickable { dial(query) }
                        )
                    }
                    items(contactMatches) { c -> ContactRow(c) { dial(c.number) } }
                    items(numberMatches) { r -> RecentRow(r) { dial(r.number) } }
                }
                tab == 0 -> items(recents) { r -> RecentRow(r) { dial(r.number) } }
                else -> items(contacts) { c -> ContactRow(c) { dial(c.number) } }
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
            Dialpad(onKey = { query += it }, modifier = Modifier.padding(horizontal = 24.dp))
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
                    if (query.isNotEmpty()) dial(query) else recents.firstOrNull()?.let { query = it.number }
                }
                Spacer(Modifier.size(48.dp))
            }
        } else {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                RoundButton(Icons.Filled.Dialpad, null, CallGreen, Color.White) { padOpen = true }
            }
        }
    }

    pendingSimCall?.let { number ->
        val sims = remember(number) { simAccounts(ctx) }
        AlertDialog(
            onDismissRequest = { pendingSimCall = null },
            confirmButton = {},
            title = { Text("Call $number with") },
            text = {
                Column {
                    sims.forEach { h ->
                        TextButton(onClick = { pendingSimCall = null; placeCall(ctx, number, h) }) {
                            Text(simLabel(ctx, h), fontSize = 18.sp)
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun ContactRow(c: Contact, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(c.name) },
        supportingContent = { Text(c.number) },
        leadingContent = { Avatar(c.name) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
fun RecentRow(r: Recent, onClick: () -> Unit) {
    val missed = r.type == CallLog.Calls.MISSED_TYPE || r.type == CallLog.Calls.REJECTED_TYPE
    val kind = when (r.type) {
        CallLog.Calls.INCOMING_TYPE -> "Incoming"
        CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
        CallLog.Calls.MISSED_TYPE -> "Missed"
        CallLog.Calls.REJECTED_TYPE -> "Declined"
        CallLog.Calls.BLOCKED_TYPE -> "Blocked"
        else -> "Call"
    }
    val title = (r.name ?: r.number) + if (r.count > 1) " (${r.count})" else ""
    ListItem(
        headlineContent = { Text(title, color = if (missed) CallRed else Color.Unspecified) },
        supportingContent = { Text("$kind · ${DateUtils.getRelativeTimeSpanString(r.date)}") },
        leadingContent = { Avatar(r.name) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
