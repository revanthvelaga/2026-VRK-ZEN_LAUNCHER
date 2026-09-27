package com.zenfold.launcher.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.StatFs
import android.provider.CalendarContract
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

// The "-1" panel to the left of the home screen: tasks, a real monthly calendar, device
// storage, and (once the user adds their own API keys in Settings → Feeds) gold/Sensex/
// cricket data. The tweets card stays sample content — see TweetsCard below for why.
// There's no "Mail" section: reading a real inbox needs Gmail/OAuth account integration,
// a separate project from anything a launcher can do on its own.
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
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(6.dp))
        Text("Today", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = style.onBackground)

        TasksCard(style, hazeState, tasks, onAddTask, onToggleTask, onRemoveTask)
        CalendarCard(style, hazeState)
        FeedsCard(style, hazeState, feedKeys)
        TweetsCard(style, hazeState)
        StorageCard(style, hazeState)

        Spacer(Modifier.height(20.dp))
    }
}

private val cardShape = RoundedCornerShape(24.dp)

private fun Modifier.card(hazeState: HazeState) = this
    .fillMaxWidth()
    .glass(hazeState, cardShape)
    .padding(18.dp)

@Composable
private fun CardTitle(text: String, style: CustomStyle) {
    Text(
        text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        color = style.onSurfaceVariant
    )
}

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
        CardTitle("Tasks", style)
        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                if (input.isEmpty()) {
                    Text("Add a task…", fontSize = 14.sp, color = style.onSurfaceVariant)
                }
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tasks.forEach { task ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(if (task.done) style.accent else style.surface.copy(alpha = 0.6f))
                                .clickable { onToggle(task.id, !task.done) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (task.done) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = style.background, modifier = Modifier.size(13.dp))
                            }
                        }
                        Spacer(Modifier.width(10.dp))
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

private data class CalendarEvent(val title: String, val startMillis: Long, val allDay: Boolean)

private fun hasCalendarPermission(context: android.content.Context) =
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
private fun queryMonthEvents(context: android.content.Context, month: YearMonth): Map<LocalDate, List<CalendarEvent>> {
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
            val startMillisEvent = cursor.getLong(1)
            val date = Instant.ofEpochMilli(startMillisEvent).atZone(zone).toLocalDate()
            byDay.getOrPut(date) { mutableListOf() } += CalendarEvent(
                title = cursor.getString(0) ?: "Untitled event",
                startMillis = startMillisEvent,
                allDay = cursor.getInt(2) != 0
            )
        }
    }
    return byDay
}

private val monthDayFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
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
        CardTitle("Calendar", style)
        Spacer(Modifier.height(10.dp))
        if (!granted) {
            Text(
                "Tap to allow calendar access",
                fontSize = 14.sp,
                color = style.onBackground,
                modifier = Modifier.clickable { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) }
            )
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(monthDayFormatter.format(visibleMonth), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = style.onBackground)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "‹",
                        fontSize = 18.sp,
                        color = style.onSurfaceVariant,
                        modifier = Modifier
                            .size(28.dp)
                            .clickable { visibleMonth = visibleMonth.minusMonths(1) },
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Text(
                        "›",
                        fontSize = 18.sp,
                        color = style.onSurfaceVariant,
                        modifier = Modifier
                            .size(28.dp)
                            .clickable { visibleMonth = visibleMonth.plusMonths(1) },
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                weekdayLabels.forEach { label ->
                    Text(
                        label,
                        fontSize = 11.sp,
                        color = style.onSurfaceVariant,
                        modifier = Modifier.width(30.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
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
            val dayFormatter = remember { DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()) }
            Text(dayFormatter.format(selectedDate), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = style.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            val selectedEvents = eventsByDay[selectedDate].orEmpty()
            if (selectedEvents.isEmpty()) {
                Text("No events", fontSize = 13.sp, color = style.onSurfaceVariant)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    selectedEvents.forEach { event ->
                        val when_ = remember(event) {
                            if (event.allDay) "All day" else SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(event.startMillis))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(event.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = style.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(when_, fontSize = 12.sp, color = style.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, inMonth: Boolean, selected: Boolean, hasEvents: Boolean, style: CustomStyle, onClick: () -> Unit) {
    Column(
        Modifier
            .width(30.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (selected) style.accent else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                date.dayOfMonth.toString(),
                fontSize = 12.sp,
                fontWeight = if (date == LocalDate.now()) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    selected -> style.background
                    !inMonth -> style.onSurfaceVariant.copy(alpha = 0.4f)
                    else -> style.onBackground
                }
            )
        }
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(4.dp)
                .clip(CircleShape)
                .background(if (hasEvents && !selected) style.accent else Color.Transparent)
        )
    }
}

@Composable
private fun StorageCard(style: CustomStyle, hazeState: HazeState) {
    val context = LocalContext.current
    val stats = remember {
        val stat = StatFs(android.os.Environment.getDataDirectory().path)
        val total = stat.totalBytes
        val free = stat.availableBytes
        Triple(total, free, total - free)
    }
    val (total, _, used) = stats
    val fraction = if (total > 0) used.toFloat() / total else 0f
    fun gb(bytes: Long) = bytes / 1_000_000_000.0

    Column(Modifier.card(hazeState)) {
        CardTitle("Storage", style)
        Spacer(Modifier.height(10.dp))
        Text(
            "%.1f GB used of %.1f GB".format(gb(used), gb(total)),
            fontSize = 14.sp,
            color = style.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(style.surface.copy(alpha = 0.5f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(style.accent)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Manage storage",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = style.accent,
            modifier = Modifier.clickable {
                context.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
            }
        )
    }
}

// Gold and Sensex go through the user's own API keys (Settings → Feeds), straight to
// goldapi.io / twelvedata.com — nothing is fetched until a key is entered.
@Composable
private fun FeedsCard(style: CustomStyle, hazeState: HazeState, keys: FeedApiKeys) {
    var gold by remember { mutableStateOf<GoldPrice?>(null) }
    var sensex by remember { mutableStateOf<MarketIndex?>(null) }
    var cricket by remember { mutableStateOf<List<CricketMatch>?>(null) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(keys) {
        loaded = false
        withContext(Dispatchers.IO) {
            gold = keys.goldApiKey.takeIf { it.isNotBlank() }?.let { fetchGoldPrice(it) }
            sensex = keys.marketApiKey.takeIf { it.isNotBlank() }?.let { fetchSensex(it) }
            cricket = keys.cricketApiKey.takeIf { it.isNotBlank() }?.let { fetchCricketMatches(it) }
        }
        loaded = true
    }

    Column(Modifier.card(hazeState)) {
        CardTitle("Gold & markets", style)
        Spacer(Modifier.height(10.dp))
        if (keys.goldApiKey.isBlank() && keys.marketApiKey.isBlank()) {
            Text(
                "Add a goldapi.io and Twelve Data key in Settings → Feeds to show live prices here.",
                fontSize = 13.sp,
                color = style.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (keys.goldApiKey.isNotBlank()) {
                    FeedLine(
                        label = "Gold (24k)",
                        value = gold?.let { "₹%.0f/g".format(it.pricePerGram) } ?: if (loaded) "Unavailable" else "Loading…",
                        style = style
                    )
                }
                if (keys.marketApiKey.isNotBlank()) {
                    FeedLine(
                        label = sensex?.name ?: "Sensex",
                        value = sensex?.let { "%.2f (%+.2f%%)".format(it.value, it.changePercent) }
                            ?: if (loaded) "Unavailable" else "Loading…",
                        style = style
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        CardTitle("Cricket", style)
        Spacer(Modifier.height(10.dp))
        when {
            keys.cricketApiKey.isBlank() -> Text(
                "Add a CricAPI key in Settings → Feeds for live scores.",
                fontSize = 13.sp,
                color = style.onSurfaceVariant
            )
            !loaded -> Text("Loading…", fontSize = 13.sp, color = style.onSurfaceVariant)
            cricket.isNullOrEmpty() -> Text("No live matches right now", fontSize = 13.sp, color = style.onSurfaceVariant)
            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cricket!!.forEach { match ->
                    Column {
                        Text(
                            match.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = style.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            match.status,
                            fontSize = 12.sp,
                            color = style.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedLine(label: String, value: String, style: CustomStyle) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 14.sp, color = style.onBackground)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = style.onBackground)
    }
}

// X (Twitter) pulled its free trends/search access years ago — the cheapest tier that
// still includes it is a paid developer plan (~$100+/month), with no free or legitimate
// scraping path. Rather than leave the feature out, this shows what real tweets would
// look like with clearly-fake sample content — swap dummyTweets for a real API response
// here (and only here) once paid access exists.
private data class MockTweet(val name: String, val handle: String, val text: String, val timeAgo: String)

private val dummyTweets = listOf(
    MockTweet("MoneyControl", "@moneycontrol", "Sensex opens 340 pts higher, Nifty above 24,900 as gold also rallies to a fresh high #Sensex #GoldPrice", "12m"),
    MockTweet("ESPNcricinfo", "@ESPNcricinfo", "WICKET! Bumrah strikes again — India need 3 more to wrap up the innings #INDvAUS", "24m"),
    MockTweet("Bloomberg", "@Bloomberg", "Gold extends rally past $2,650/oz as investors seek safe havens amid rate-cut bets #GoldPrice", "41m"),
    MockTweet("IPL", "@IPL", "Squads announced for the 2026 mega auction — full list of retained players inside #IPL2026", "1h"),
    MockTweet("CNBC-TV18", "@CNBCTV18Live", "Sensex, Nifty extend gains for 4th straight session; IT and banking stocks lead #Sensex", "1h"),
    MockTweet("BBC Sport", "@BBCSport", "Full scorecard and highlights from today's thrilling run chase #Cricket", "2h"),
    MockTweet("Reuters", "@Reuters", "Gold prices set for best week in two months on softer dollar #GoldPrice", "2h"),
    MockTweet("ANI", "@ANI", "Union Budget 2026 session dates announced by the Finance Ministry #Budget2026", "3h"),
    MockTweet("Variety", "@Variety", "Trailer for the year's biggest Bollywood release crosses 50M views in 24 hours #Bollywood", "4h"),
    MockTweet("The Weather Channel", "@weatherchannel", "Climate summit negotiators reach draft agreement on emissions targets #ClimateSummit", "5h")
)

@Composable
private fun TweetsCard(style: CustomStyle, hazeState: HazeState) {
    Column(Modifier.card(hazeState)) {
        CardTitle("Top tweets", style)
        Spacer(Modifier.height(4.dp))
        Text(
            "Sample content — X has no free API for trending tweets, so these aren't live.",
            fontSize = 11.5.sp,
            color = style.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            dummyTweets.forEach { tweet ->
                Row {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(style.accent.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            tweet.name.first().toString(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = style.accent
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(tweet.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = style.onBackground)
                            Spacer(Modifier.width(6.dp))
                            Text(tweet.handle, fontSize = 12.sp, color = style.onSurfaceVariant)
                            Spacer(Modifier.width(6.dp))
                            Text("· ${tweet.timeAgo}", fontSize = 12.sp, color = style.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(tweet.text, fontSize = 13.sp, color = style.onBackground, lineHeight = 17.sp)
                    }
                }
            }
        }
    }
}
