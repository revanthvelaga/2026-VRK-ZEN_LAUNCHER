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
import androidx.compose.foundation.layout.weight
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.zenfold.launcher.style.CustomStyle
import com.zenfold.launcher.tasks.TaskItem
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// The "-1" panel to the left of the home screen: today's tasks, upcoming calendar
// events, and device storage — real, first-party data, not a news feed. There's no
// "Mail" section here: reading a real inbox needs Gmail/OAuth account integration,
// a separate project from anything a launcher can do on its own.
@Composable
fun TodayPanel(
    style: CustomStyle,
    hazeState: HazeState,
    tasks: List<TaskItem>,
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

// CalendarContract.Instances expands recurring events into their actual occurrences —
// querying Events directly would miss "every Monday" style meetings.
private fun queryUpcomingEvents(context: android.content.Context): List<CalendarEvent> {
    val now = System.currentTimeMillis()
    val end = now + TimeUnit.DAYS.toMillis(14)
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
        .appendPath(now.toString())
        .appendPath(end.toString())
        .build()
    val projection = arrayOf(
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.ALL_DAY
    )
    val events = mutableListOf<CalendarEvent>()
    context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { cursor ->
        while (cursor.moveToNext() && events.size < 5) {
            events += CalendarEvent(
                title = cursor.getString(0) ?: "Untitled event",
                startMillis = cursor.getLong(1),
                allDay = cursor.getInt(2) != 0
            )
        }
    }
    return events
}

@Composable
private fun CalendarCard(style: CustomStyle, hazeState: HazeState) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasCalendarPermission(context)) }
    var events by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    LaunchedEffect(granted) {
        if (granted) events = withContext(Dispatchers.IO) { queryUpcomingEvents(context) }
    }

    Column(Modifier.card(hazeState)) {
        CardTitle("Calendar", style)
        Spacer(Modifier.height(10.dp))
        when {
            !granted -> Text(
                "Tap to allow calendar access",
                fontSize = 14.sp,
                color = style.onBackground,
                modifier = Modifier.clickable { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) }
            )
            events.isEmpty() -> Text("Nothing coming up in the next two weeks", fontSize = 13.sp, color = style.onSurfaceVariant)
            else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                events.forEach { event ->
                    val when_ = remember(event) {
                        if (event.allDay) {
                            SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(event.startMillis))
                        } else {
                            SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault()).format(Date(event.startMillis))
                        }
                    }
                    Column {
                        Text(event.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = style.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(when_, fontSize = 12.sp, color = style.onSurfaceVariant)
                    }
                }
            }
        }
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
