package com.zenfold.launcher.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Environment
import android.os.StatFs
import android.provider.CalendarContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.zenfold.launcher.SettingsActivity
import com.zenfold.launcher.feeds.CricketMatch
import com.zenfold.launcher.feeds.FeedApiKeys
import com.zenfold.launcher.feeds.GoldPrice
import com.zenfold.launcher.feeds.MarketIndex
import com.zenfold.launcher.feeds.fetchCricketMatches
import com.zenfold.launcher.feeds.fetchGoldPrice
import com.zenfold.launcher.feeds.fetchSensex
import com.zenfold.launcher.style.CustomStyle
import com.zenfold.launcher.tasks.TaskItem
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

private val Positive = Color(0xFF34C759)
private val Negative = Color(0xFFFF453A)
private val LiveRed = Color(0xFFE5484D)

// The "-1" page to the left of Home: a greeting, quick stats, the cricket scoreboard, a real
// month calendar, tasks, and sample tweets. Market and cricket data only appear once the user
// adds their own API keys (Settings → Feeds); until then each spot shows a tidy "Set up"
// prompt rather than fake numbers. No "Mail" section: a real inbox needs Gmail/OAuth
// integration, a separate project from anything a launcher can do on its own.
@Composable
fun TodayPanel(
    style: CustomStyle,
    hazeState: HazeState,
    tasks: List<TaskItem>,
    feedKeys: FeedApiKeys,
    onAddTask: (String) -> Unit,
    onToggleTask: (String, Boolean) -> Unit,
    onRemoveTask: (String) -> Unit
) {
    val feeds = rememberFeeds(feedKeys)

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        Greeting(style)
        StatTiles(style, hazeState, feedKeys, feeds)
        ScoreboardCard(style, hazeState, feedKeys, feeds)
        CalendarCard(style, hazeState)
        TasksCard(style, hazeState, tasks, onAddTask, onToggleTask, onRemoveTask)
        TweetsCard(style, hazeState)
        Spacer(Modifier.height(24.dp))
    }
}

private val cardShape = RoundedCornerShape(24.dp)

private fun Modifier.card(hazeState: HazeState) = this
    .fillMaxWidth()
    .glass(hazeState, cardShape)
    .padding(18.dp)

private fun openFeedSettings(context: Context) {
    context.startActivity(Intent(context, SettingsActivity::class.java))
}

@Composable
private fun CardTitle(text: String, style: CustomStyle, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = style.onSurfaceVariant,
        modifier = modifier
    )
}

@Composable
private fun Greeting(style: CustomStyle) {
    val now = rememberCurrentTimeMillis()
    val hour = remember(now) { LocalTime.now().hour }
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    val date = remember(now) { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(now)) }
    Column(Modifier.padding(horizontal = 4.dp)) {
        Text(greeting, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = style.onBackground)
        Text(date, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = style.onSurfaceVariant)
    }
}

private class FeedState(
    val gold: GoldPrice?,
    val sensex: MarketIndex?,
    val cricket: List<CricketMatch>?,
    val loaded: Boolean
)

@Composable
private fun rememberFeeds(keys: FeedApiKeys): FeedState {
    var state by remember { mutableStateOf(FeedState(null, null, null, loaded = false)) }
    LaunchedEffect(keys) {
        state = FeedState(null, null, null, loaded = false)
        state = withContext(Dispatchers.IO) {
            FeedState(
                gold = keys.goldApiKey.takeIf { it.isNotBlank() }?.let { fetchGoldPrice(it) },
                sensex = keys.marketApiKey.takeIf { it.isNotBlank() }?.let { fetchSensex(it) },
                cricket = keys.cricketApiKey.takeIf { it.isNotBlank() }?.let { fetchCricketMatches(it) },
                loaded = true
            )
        }
    }
    return state
}

// ---------------------------------------------------------------- Stat tiles

@Composable
private fun StatTiles(style: CustomStyle, hazeState: HazeState, keys: FeedApiKeys, feeds: FeedState) {
    val context = LocalContext.current
    val storage = remember {
        val stat = StatFs(Environment.getDataDirectory().path)
        stat.totalBytes to (stat.totalBytes - stat.availableBytes)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile(
            label = "Gold 24k",
            value = feeds.gold?.let { "₹%,.0f".format(it.pricePerGram) },
            detail = if (feeds.gold != null) "per gram" else null,
            detailColor = style.onSurfaceVariant,
            needsSetup = keys.goldApiKey.isBlank(),
            loading = !feeds.loaded,
            style = style,
            hazeState = hazeState,
            onSetup = { openFeedSettings(context) }
        )
        StatTile(
            label = "Sensex",
            value = feeds.sensex?.let { "%,.0f".format(it.value) },
            detail = feeds.sensex?.let { "%+.2f%%".format(it.changePercent) },
            detailColor = if ((feeds.sensex?.changePercent ?: 0.0) >= 0) Positive else Negative,
            needsSetup = keys.marketApiKey.isBlank(),
            loading = !feeds.loaded,
            style = style,
            hazeState = hazeState,
            onSetup = { openFeedSettings(context) }
        )
        val (total, used) = storage
        StatTile(
            label = "Storage",
            value = if (total > 0) "${used * 100 / total}%" else null,
            detail = "%.0f of %.0f GB".format(used / 1e9, total / 1e9),
            detailColor = style.onSurfaceVariant,
            needsSetup = false,
            loading = false,
            style = style,
            hazeState = hazeState,
            onClick = { context.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) }
        )
    }
}

@Composable
private fun RowScope.StatTile(
    label: String,
    value: String?,
    detail: String?,
    detailColor: Color,
    needsSetup: Boolean,
    loading: Boolean,
    style: CustomStyle,
    hazeState: HazeState,
    onSetup: () -> Unit = {},
    onClick: (() -> Unit)? = null
) {
    Column(
        Modifier
            .weight(1f)
            .glass(hazeState, RoundedCornerShape(20.dp))
            .clickable(enabled = needsSetup || onClick != null) { if (needsSetup) onSetup() else onClick?.invoke() }
            .padding(horizontal = 12.dp, vertical = 14.dp)
    ) {
        CardTitle(label, style)
        Spacer(Modifier.height(8.dp))
        when {
            needsSetup -> {
                Text("—", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = style.onSurfaceVariant)
                Text("Set up", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = style.accent)
            }
            value != null -> {
                Text(value, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = style.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                detail?.let { Text(it, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = detailColor, maxLines = 1) }
            }
            else -> {
                Text("…", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = style.onSurfaceVariant)
                Text(if (loading) "Loading" else "Unavailable", fontSize = 12.sp, color = style.onSurfaceVariant)
            }
        }
    }
}

// ---------------------------------------------------------------- Cricket scoreboard

@Composable
private fun ScoreboardCard(style: CustomStyle, hazeState: HazeState, keys: FeedApiKeys, feeds: FeedState) {
    val context = LocalContext.current
    Column(Modifier.card(hazeState)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.SportsCricket, contentDescription = null, tint = style.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            CardTitle("Cricket", style)
        }
        Spacer(Modifier.height(12.dp))
        val matches = feeds.cricket
        when {
            keys.cricketApiKey.isBlank() -> SetupPrompt(
                "Live scores appear here once you add a free CricAPI key.",
                style
            ) { openFeedSettings(context) }
            !feeds.loaded -> Text("Loading scores…", fontSize = 13.sp, color = style.onSurfaceVariant)
            matches == null -> Text("Couldn't reach CricAPI — check your key in Settings.", fontSize = 13.sp, color = style.onSurfaceVariant)
            matches.isEmpty() -> Text("No matches on right now", fontSize = 13.sp, color = style.onSurfaceVariant)
            else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                matches.forEach { match -> MatchScore(match, style) }
            }
        }
    }
}

@Composable
private fun MatchScore(match: CricketMatch, style: CustomStyle) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (match.live) {
                Text(
                    "LIVE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .background(LiveRed, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                listOf(match.matchType, match.name).filter { it.isNotBlank() }.joinToString(" · "),
                fontSize = 11.sp,
                color = style.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(8.dp))
        match.teams.forEach { team ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    team.team,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = style.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    team.score ?: "Yet to bat",
                    fontSize = if (team.score != null) 15.sp else 12.sp,
                    fontWeight = if (team.score != null) FontWeight.Bold else FontWeight.Normal,
                    color = if (team.score != null) style.onBackground else style.onSurfaceVariant
                )
            }
        }
        if (match.status.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(match.status, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = style.accent, maxLines = 2)
        }
    }
}

@Composable
private fun SetupPrompt(text: String, style: CustomStyle, onSetup: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 13.sp, color = style.onSurfaceVariant, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(
            "Set up",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = style.background,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(style.accent)
                .clickable(onClick = onSetup)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

// ---------------------------------------------------------------- Tasks

@Composable
private fun TasksCard(
    style: CustomStyle,
    hazeState: HazeState,
    tasks: List<TaskItem>,
    onAdd: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onRemove: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }

    Column(Modifier.card(hazeState)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle("Tasks", style, Modifier.weight(1f))
            val open = tasks.count { !it.done }
            if (tasks.isNotEmpty()) Text("$open left", fontSize = 12.sp, color = style.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f)) {
                if (input.isEmpty()) Text("Add a task…", fontSize = 14.sp, color = style.onSurfaceVariant)
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, color = style.onBackground),
                    cursorBrush = SolidColor(style.accent),
                    keyboardActions = KeyboardActions(onDone = {
                        onAdd(input)
                        input = ""
                    }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Icon(
                Icons.Filled.Add,
                contentDescription = "Add task",
                tint = style.accent,
                modifier = Modifier
                    .size(22.dp)
                    .clickable {
                        onAdd(input)
                        input = ""
                    }
            )
        }

        if (tasks.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                tasks.forEach { task ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(if (task.done) style.accent else style.onSurfaceVariant.copy(alpha = 0.2f))
                                .clickable { onToggle(task.id, !task.done) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (task.done) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = style.background, modifier = Modifier.size(14.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            task.text,
                            fontSize = 14.sp,
                            color = if (task.done) style.onSurfaceVariant else style.onBackground,
                            textDecoration = if (task.done) TextDecoration.LineThrough else null,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Remove",
                            tint = style.onSurfaceVariant,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onRemove(task.id) }
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Calendar

private data class CalendarEvent(val title: String, val startMillis: Long, val allDay: Boolean)

private fun hasCalendarPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

// A Sunday-first 6-week grid always covers any month, including the lead-in/lead-out
// days from the adjacent months that a real calendar app shows to fill the grid.
private fun monthGridDates(month: YearMonth): List<LocalDate> {
    val first = month.atDay(1)
    val leadingDays = first.dayOfWeek.value % 7 // DayOfWeek.value: Mon=1 … Sun=7, so Sun%7=0
    val gridStart = first.minusDays(leadingDays.toLong())
    return (0 until 42).map { gridStart.plusDays(it.toLong()) }
}

// CalendarContract.Instances expands recurring events into their actual occurrences —
// querying Events directly would miss "every Monday" style meetings. Queried once per
// visible month over the whole 6-week grid so day cells can show a dot for real events.
private fun queryMonthEvents(context: Context, month: YearMonth): Map<LocalDate, List<CalendarEvent>> {
    val dates = monthGridDates(month)
    val zone = ZoneId.systemDefault()
    val startMillis = dates.first().atStartOfDay(zone).toInstant().toEpochMilli()
    val endMillis = dates.last().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
        .appendPath(startMillis.toString())
        .appendPath(endMillis.toString())
        .build()
    val projection = arrayOf(
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.ALL_DAY
    )
    val byDay = mutableMapOf<LocalDate, MutableList<CalendarEvent>>()
    context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { cursor ->
        while (cursor.moveToNext()) {
            val begin = cursor.getLong(1)
            val date = Instant.ofEpochMilli(begin).atZone(zone).toLocalDate()
            byDay.getOrPut(date) { mutableListOf() } += CalendarEvent(
                title = cursor.getString(0) ?: "Untitled event",
                startMillis = begin,
                allDay = cursor.getInt(2) != 0
            )
        }
    }
    return byDay
}

private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
private val weekdayLabels = listOf("S", "M", "T", "W", "T", "F", "S")

@Composable
private fun CalendarCard(style: CustomStyle, hazeState: HazeState) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasCalendarPermission(context)) }
    var visibleMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var eventsByDay by remember { mutableStateOf<Map<LocalDate, List<CalendarEvent>>>(emptyMap()) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    LaunchedEffect(granted, visibleMonth) {
        if (granted) eventsByDay = withContext(Dispatchers.IO) { queryMonthEvents(context, visibleMonth) }
    }

    Column(Modifier.card(hazeState)) {
        if (!granted) {
            CardTitle("Calendar", style)
            Spacer(Modifier.height(12.dp))
            SetupPrompt("See your month and upcoming events here.", style) {
                permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
            }
            return@Column
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                monthFormatter.format(visibleMonth),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = style.onBackground,
                modifier = Modifier.weight(1f)
            )
            MonthArrow(Icons.AutoMirrored.Filled.KeyboardArrowLeft, style) { visibleMonth = visibleMonth.minusMonths(1) }
            Spacer(Modifier.width(6.dp))
            MonthArrow(Icons.AutoMirrored.Filled.KeyboardArrowRight, style) { visibleMonth = visibleMonth.plusMonths(1) }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            weekdayLabels.forEach { label ->
                Text(
                    label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = style.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(34.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        val grid = remember(visibleMonth) { monthGridDates(visibleMonth) }
        grid.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                week.forEach { date ->
                    DayCell(
                        date = date,
                        inMonth = date.month == visibleMonth.month,
                        selected = date == selectedDate,
                        hasEvents = eventsByDay[date]?.isNotEmpty() == true,
                        style = style,
                        onClick = { selectedDate = date }
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val dayFormatter = remember { DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.getDefault()) }
        Text(dayFormatter.format(selectedDate), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = style.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        val selectedEvents = eventsByDay[selectedDate].orEmpty()
        if (selectedEvents.isEmpty()) {
            Text("Nothing scheduled", fontSize = 13.sp, color = style.onSurfaceVariant)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                selectedEvents.forEach { event ->
                    val time = remember(event) {
                        if (event.allDay) "All day" else SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(event.startMillis))
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(width = 3.dp, height = 28.dp).background(style.accent, RoundedCornerShape(2.dp)))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(event.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = style.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(time, fontSize = 12.sp, color = style.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, style: CustomStyle, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = style.onBackground, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun DayCell(date: LocalDate, inMonth: Boolean, selected: Boolean, hasEvents: Boolean, style: CustomStyle, onClick: () -> Unit) {
    val today = date == LocalDate.now()
    Column(
        Modifier
            .width(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(
                    when {
                        selected -> style.accent
                        today -> style.accent.copy(alpha = 0.22f)
                        else -> Color.Transparent
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                date.dayOfMonth.toString(),
                fontSize = 13.sp,
                fontWeight = if (today || selected) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    selected -> style.background
                    !inMonth -> style.onSurfaceVariant.copy(alpha = 0.35f)
                    else -> style.onBackground
                }
            )
        }
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(4.dp)
                .background(if (hasEvents && !selected) style.accent else Color.Transparent, CircleShape)
        )
    }
}

// ---------------------------------------------------------------- Tweets

// X (Twitter) pulled its free trends/search access years ago — the cheapest tier that
// still includes it is a paid developer plan (~$100+/month), with no free or legitimate
// scraping path. So this shows clearly-labelled sample content in the shape real tweets
// would take; swap dummyTweets for a real API response here (and only here) once paid
// access exists.
private data class MockTweet(val name: String, val handle: String, val text: String, val timeAgo: String)

private val dummyTweets = listOf(
    MockTweet("MoneyControl", "@moneycontrol", "Sensex opens 340 pts higher, Nifty above 24,900 as gold also rallies to a fresh high #Sensex #GoldPrice", "12m"),
    MockTweet("ESPNcricinfo", "@ESPNcricinfo", "WICKET! Bumrah strikes again — India need 3 more to wrap up the innings #INDvAUS", "24m"),
    MockTweet("Bloomberg", "@Bloomberg", "Gold extends rally past \$2,650/oz as investors seek safe havens amid rate-cut bets #GoldPrice", "41m"),
    MockTweet("IPL", "@IPL", "Squads announced for the 2026 mega auction — full list of retained players inside #IPL2026", "1h"),
    MockTweet("CNBC-TV18", "@CNBCTV18Live", "Sensex, Nifty extend gains for 4th straight session; IT and banking stocks lead #Sensex", "1h"),
    MockTweet("BBC Sport", "@BBCSport", "Full scorecard and highlights from today's thrilling run chase #Cricket", "2h"),
    MockTweet("Reuters", "@Reuters", "Gold prices set for best week in two months on softer dollar #GoldPrice", "2h"),
    MockTweet("ANI", "@ANI", "Union Budget 2026 session dates announced by the Finance Ministry #Budget2026", "3h"),
    MockTweet("Variety", "@Variety", "Trailer for the year's biggest Bollywood release crosses 50M views in 24 hours #Bollywood", "4h"),
    MockTweet("The Weather Channel", "@weatherchannel", "Climate summit negotiators reach draft agreement on emissions targets #ClimateSummit", "5h")
)

private const val COLLAPSED_TWEETS = 3

@Composable
private fun TweetsCard(style: CustomStyle, hazeState: HazeState) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.card(hazeState)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle("Top tweets", style, Modifier.weight(1f))
            Text(
                "SAMPLE",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = style.onSurfaceVariant,
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            dummyTweets.take(if (expanded) dummyTweets.size else COLLAPSED_TWEETS).forEach { tweet ->
                Row {
                    Box(
                        Modifier
                            .size(34.dp)
                            .background(style.accent.copy(alpha = 0.22f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(tweet.name.first().toString(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = style.accent)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(tweet.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = style.onBackground, maxLines = 1)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "${tweet.handle} · ${tweet.timeAgo}",
                                fontSize = 12.sp,
                                color = style.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(tweet.text, fontSize = 13.sp, color = style.onBackground, lineHeight = 18.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (expanded) "Show less" else "Show all ${dummyTweets.size}",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = style.accent,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 6.dp)
        )
    }
}
