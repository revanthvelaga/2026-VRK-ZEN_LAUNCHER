package com.zenfold.launcher.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.TelephonyManager
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
import com.zenfold.launcher.feeds.CricketMatch
import com.zenfold.launcher.feeds.GoldPrice
import com.zenfold.launcher.feeds.MarketIndex
import com.zenfold.launcher.feeds.Trend
import com.zenfold.launcher.feeds.fetchCricketScores
import com.zenfold.launcher.feeds.fetchGoldPrice
import com.zenfold.launcher.feeds.fetchSensex
import com.zenfold.launcher.feeds.fetchTrends
import com.zenfold.launcher.style.CustomStyle
import com.zenfold.launcher.tasks.TaskItem
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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

// The "-1" page to the left of Home: a greeting, live market tiles, a cricket scoreboard,
// a real month calendar, tasks, and what's trending — all live, from public keyless feeds
// (feeds/FeedApi.kt), so there's nothing to set up. No "Mail" section: a real inbox needs
// Gmail/OAuth integration, a separate project from anything a launcher can do on its own.
@Composable
fun TodayPanel(
    style: CustomStyle,
    hazeState: HazeState,
    tasks: List<TaskItem>,
    onAddTask: (String) -> Unit,
    onToggleTask: (String, Boolean) -> Unit,
    onRemoveTask: (String) -> Unit
) {
    val context = LocalContext.current
    val country = remember { deviceCountry(context) }
    val feeds = rememberFeeds(country)

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
        StatTiles(style, hazeState, feeds)
        ScoreboardCard(style, hazeState, feeds)
        CalendarCard(style, hazeState)
        TasksCard(style, hazeState, tasks, onAddTask, onToggleTask, onRemoveTask)
        TrendingCard(style, hazeState, feeds, country)
        Spacer(Modifier.height(24.dp))
    }
}

private val cardShape = RoundedCornerShape(24.dp)

private fun Modifier.card(hazeState: HazeState) = this
    .fillMaxWidth()
    .glass(hazeState, cardShape)
    .padding(18.dp)

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        // No browser installed: nothing sensible to open it in.
    }
}

// The network's country first (what the phone is actually in), then the SIM's, then the
// language setting — plenty of phones in India run en-US.
private fun deviceCountry(context: Context): String {
    val telephony = context.getSystemService(TelephonyManager::class.java)
    return listOfNotNull(telephony?.networkCountryIso, telephony?.simCountryIso, Locale.getDefault().country)
        .firstOrNull { it.length == 2 }
        ?.uppercase(Locale.ROOT)
        ?: "IN"
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
    val trends: List<Trend>?,
    val loaded: Boolean
) {
    companion object {
        val EMPTY = FeedState(null, null, null, null, loaded = false)
    }
}

// Survives leaving and re-entering the page, so it shows the last data instantly while
// refreshing instead of flashing "Loading".
private object FeedCache {
    var last: FeedState = FeedState.EMPTY
}

private const val REFRESH_MILLIS = 2 * 60 * 1000L

/** Fetches every feed in parallel, then again every two minutes while this page is on screen. */
@Composable
private fun rememberFeeds(country: String): FeedState {
    var state by remember { mutableStateOf(FeedCache.last) }
    LaunchedEffect(country) {
        while (true) {
            val previous = state
            val fresh = withContext(Dispatchers.IO) {
                coroutineScope {
                    val gold = async { fetchGoldPrice() }
                    val sensex = async { fetchSensex() }
                    val cricket = async { fetchCricketScores() }
                    val trends = async { fetchTrends(country) }
                    // A feed that fails this round keeps its last good value.
                    FeedState(
                        gold = gold.await() ?: previous.gold,
                        sensex = sensex.await() ?: previous.sensex,
                        cricket = cricket.await() ?: previous.cricket,
                        trends = trends.await() ?: previous.trends,
                        loaded = true
                    )
                }
            }
            state = fresh
            FeedCache.last = fresh
            delay(REFRESH_MILLIS)
        }
    }
    return state
}

// ---------------------------------------------------------------- Stat tiles

private fun changeColor(percent: Double?): Color = if ((percent ?: 0.0) >= 0) Positive else Negative

@Composable
private fun StatTiles(style: CustomStyle, hazeState: HazeState, feeds: FeedState) {
    val context = LocalContext.current
    val storage = remember {
        val stat = StatFs(Environment.getDataDirectory().path)
        stat.totalBytes to (stat.totalBytes - stat.availableBytes)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile(
            label = "Gold / g",
            value = feeds.gold?.let { "₹%,.0f".format(it.pricePerGram) },
            detail = feeds.gold?.let { "%+.2f%%".format(it.changePercent) },
            detailColor = changeColor(feeds.gold?.changePercent),
            loading = !feeds.loaded,
            style = style,
            hazeState = hazeState,
            onClick = { openUrl(context, "https://www.google.com/search?q=gold+price+today") }
        )
        StatTile(
            label = "Sensex",
            value = feeds.sensex?.let { "%,.0f".format(it.value) },
            detail = feeds.sensex?.let { "%+.2f%%".format(it.changePercent) },
            detailColor = changeColor(feeds.sensex?.changePercent),
            loading = !feeds.loaded,
            style = style,
            hazeState = hazeState,
            onClick = { openUrl(context, "https://www.google.com/search?q=sensex") }
        )
        val (total, used) = storage
        StatTile(
            label = "Storage",
            value = if (total > 0) "${used * 100 / total}%" else null,
            detail = "%.0f of %.0f GB".format(used / 1e9, total / 1e9),
            detailColor = style.onSurfaceVariant,
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
    loading: Boolean,
    style: CustomStyle,
    hazeState: HazeState,
    onClick: () -> Unit
) {
    Column(
        Modifier
            .weight(1f)
            .glass(hazeState, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp)
    ) {
        CardTitle(label, style)
        Spacer(Modifier.height(8.dp))
        if (value != null) {
            Text(value, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = style.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            detail?.let { Text(it, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = detailColor, maxLines = 1) }
        } else {
            Text("…", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = style.onSurfaceVariant)
            Text(if (loading) "Loading" else "Offline", fontSize = 12.sp, color = style.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------- Cricket scoreboard

private const val COLLAPSED_MATCHES = 3

@Composable
private fun ScoreboardCard(style: CustomStyle, hazeState: HazeState, feeds: FeedState) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.card(hazeState)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.SportsCricket, contentDescription = null, tint = style.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            CardTitle("Cricket", style, Modifier.weight(1f))
            Text("ESPNcricinfo", fontSize = 10.sp, color = style.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        val matches = feeds.cricket
        when {
            matches == null && !feeds.loaded -> Text("Loading scores…", fontSize = 13.sp, color = style.onSurfaceVariant)
            matches == null -> Text("Scores are offline right now — they'll refresh automatically.", fontSize = 13.sp, color = style.onSurfaceVariant)
            matches.isEmpty() -> Text("No matches on right now", fontSize = 13.sp, color = style.onSurfaceVariant)
            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    matches.take(if (expanded) matches.size else COLLAPSED_MATCHES).forEach { match -> MatchScore(match, style) }
                }
                if (matches.size > COLLAPSED_MATCHES) {
                    ShowMoreToggle(expanded, matches.size, style) { expanded = !expanded }
                }
            }
        }
    }
}

@Composable
private fun MatchScore(match: CricketMatch, style: CustomStyle) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .clickable(enabled = match.url != null) { match.url?.let { openUrl(context, it) } }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            match.teams.forEach { team ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    // A small dot marks the side batting right now.
                    Box(
                        Modifier
                            .size(6.dp)
                            .background(if (team.batting) style.accent else Color.Transparent, CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        team.team,
                        fontSize = 15.sp,
                        fontWeight = if (team.batting) FontWeight.Bold else FontWeight.SemiBold,
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
        }
        if (match.live) {
            Spacer(Modifier.width(10.dp))
            Text(
                "LIVE",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .background(LiveRed, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun ShowMoreToggle(expanded: Boolean, total: Int, style: CustomStyle, onToggle: () -> Unit) {
    Spacer(Modifier.height(8.dp))
    Text(
        if (expanded) "Show less" else "Show all $total",
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = style.accent,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp)
    )
}

@Composable
private fun PermissionPrompt(text: String, style: CustomStyle, onAllow: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 13.sp, color = style.onSurfaceVariant, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(
            "Allow",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = style.background,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(style.accent)
                .clickable(onClick = onAllow)
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
            PermissionPrompt("See your month and upcoming events here.", style) {
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

// ---------------------------------------------------------------- Trending

// What people are searching for right now, from Google Trends — the live, no-setup stand-in
// for "trending on X", which has no free or legitimate keyless feed.
private const val COLLAPSED_TRENDS = 5

@Composable
private fun TrendingCard(style: CustomStyle, hazeState: HazeState, feeds: FeedState, country: String) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val place = remember(country) { Locale("", country).displayCountry.ifBlank { country } }
    Column(Modifier.card(hazeState)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle("Trending in $place", style, Modifier.weight(1f))
            Text("Google Trends", fontSize = 10.sp, color = style.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        val trends = feeds.trends
        when {
            trends == null && !feeds.loaded -> Text("Loading…", fontSize = 13.sp, color = style.onSurfaceVariant)
            trends.isNullOrEmpty() -> Text("Trends are offline right now — they'll refresh automatically.", fontSize = 13.sp, color = style.onSurfaceVariant)
            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    trends.take(if (expanded) trends.size else COLLAPSED_TRENDS).forEachIndexed { index, trend ->
                        TrendRow(index + 1, trend, style) { openUrl(context, trend.url) }
                    }
                }
                if (trends.size > COLLAPSED_TRENDS) {
                    ShowMoreToggle(expanded, trends.size, style) { expanded = !expanded }
                }
            }
        }
    }
}

@Composable
private fun TrendRow(rank: Int, trend: Trend, style: CustomStyle, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            "$rank",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = style.accent,
            modifier = Modifier.width(26.dp)
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    trend.title.replaceFirstChar { it.titlecase(Locale.getDefault()) },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = style.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                trend.traffic?.let {
                    Spacer(Modifier.width(8.dp))
                    Text("$it searches", fontSize = 11.sp, color = style.onSurfaceVariant, maxLines = 1)
                }
            }
            trend.headline?.let {
                Text(it, fontSize = 12.sp, color = style.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
            }
        }
    }
}
