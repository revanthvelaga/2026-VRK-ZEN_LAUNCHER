package com.zenfold.launcher.widgets

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AlarmOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.zenfold.launcher.AppEntry
import com.zenfold.launcher.notifications.ZenFoldNotificationListener
import com.zenfold.launcher.style.CustomStyle
import com.zenfold.launcher.ui.AppIcon
import com.zenfold.launcher.ui.glass
import dev.chrisbanes.haze.HazeState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A first-party widget the user can add to or remove from the home screen. */
enum class WidgetType(val id: String, val title: String) {
    GLANCE("glance", "Glance"),
    CALENDAR("calendar", "Calendar"),
    NOTE("note", "Note"),
    RECENTS("recents", "Recent apps"),
    NOTIFICATIONS("notifications", "Notifications")
}

// Long-pressing the home screen opens this instead of full Settings — a quick way to
// turn widgets on/off without leaving the home screen. Full Settings (presets, colors,
// icon shape...) lives in its own app now; see SettingsActivity.
@Composable
fun WidgetPickerOverlay(
    style: CustomStyle,
    hazeState: HazeState,
    enabled: Set<WidgetType>,
    onToggle: (WidgetType, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .padding(32.dp)
                .fillMaxWidth()
                .glass(hazeState, RoundedCornerShape(28.dp))
                // Swallows taps so they don't fall through to the scrim behind and dismiss.
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(20.dp)
        ) {
            Text("Widgets", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = style.onBackground)
            Text(
                "Choose what shows on your home screen",
                fontSize = 12.sp,
                color = style.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
            )
            WidgetType.entries.forEach { widget ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(widget.title, fontSize = 14.sp, color = style.onBackground)
                    Switch(checked = widget in enabled, onCheckedChange = { onToggle(widget, it) })
                }
            }
        }
    }
}

@Composable
fun WidgetArea(
    enabled: Set<WidgetType>,
    style: CustomStyle,
    hazeState: HazeState,
    now: Long,
    apps: List<AppEntry>,
    recentPackages: List<String>,
    noteText: String,
    onNoteChange: (String) -> Unit,
    onLaunch: (AppEntry) -> Unit
) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (WidgetType.GLANCE in enabled) GlanceWidget(style, hazeState, now)
        if (WidgetType.CALENDAR in enabled) CalendarWidget(style, hazeState, now)
        if (WidgetType.NOTE in enabled) NoteWidget(style, hazeState, noteText, onNoteChange)
        if (WidgetType.RECENTS in enabled) RecentsWidget(style, hazeState, apps, recentPackages, onLaunch)
        if (WidgetType.NOTIFICATIONS in enabled) NotificationsWidget(style, hazeState)
    }
}

private val cardShape = RoundedCornerShape(24.dp)

private fun Modifier.card(hazeState: HazeState) = this
    .fillMaxWidth()
    .glass(hazeState, cardShape)
    .padding(horizontal = 16.dp, vertical = 14.dp)

@Composable
private fun WidgetLabel(text: String, style: CustomStyle) {
    Text(
        text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        color = style.onSurfaceVariant
    )
}

// Same layout as the preview's glance card (icon tile | two stats), but with real
// data that needs no permissions: the next alarm and the battery level.
@Composable
private fun GlanceWidget(style: CustomStyle, hazeState: HazeState, now: Long) {
    val context = LocalContext.current
    val nextAlarm = remember(now) {
        context.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime
    }
    val alarmText = nextAlarm?.let {
        val flags = if (DateUtils.isToday(it)) {
            DateUtils.FORMAT_SHOW_TIME
        } else {
            DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY
        }
        DateUtils.formatDateTime(context, it, flags)
    }
    val battery = rememberBattery()

    Row(
        Modifier.card(hazeState),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(style.iconShapeKind.shape)
                .background(Brush.linearGradient(listOf(style.accent, Color(0xFF1C3A5E)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (alarmText != null) Icons.Outlined.Alarm else Icons.Outlined.AlarmOff,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                alarmText ?: "No alarm",
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Bold,
                color = style.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text("Next alarm", fontSize = 12.sp, color = style.onSurfaceVariant)
        }
        Box(Modifier.width(1.dp).height(30.dp).background(Color.White.copy(alpha = 0.18f)))
        Column {
            Text(
                battery?.let { "${it.percent}%" } ?: "—",
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Bold,
                color = style.onBackground
            )
            Text(
                if (battery?.charging == true) "Charging" else "Battery",
                fontSize = 12.sp,
                color = style.onSurfaceVariant
            )
        }
    }
}

private data class BatteryInfo(val percent: Int, val charging: Boolean)

@Composable
private fun rememberBattery(): BatteryInfo? {
    val context = LocalContext.current
    var info by remember { mutableStateOf<BatteryInfo?>(null) }
    DisposableEffect(context) {
        fun read(intent: Intent?): BatteryInfo? {
            intent ?: return null
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level < 0 || scale <= 0) return null
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            return BatteryInfo(level * 100 / scale, charging)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                info = read(intent)
            }
        }
        // ACTION_BATTERY_CHANGED is sticky, so registering also returns the current state.
        info = read(
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    return info
}

@Composable
private fun CalendarWidget(style: CustomStyle, hazeState: HazeState, now: Long) {
    val today = remember(now) { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date(now)) }
    Column(Modifier.card(hazeState), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        WidgetLabel("Today", style)
        Text(today, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = style.onBackground)
    }
}

@Composable
private fun NoteWidget(style: CustomStyle, hazeState: HazeState, text: String, onTextChange: (String) -> Unit) {
    Column(Modifier.card(hazeState)) {
        WidgetLabel("Note", style)
        Box(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            if (text.isEmpty()) {
                Text("Tap to write something…", fontSize = 14.sp, color = style.onSurfaceVariant)
            }
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                textStyle = TextStyle(fontSize = 14.sp, color = style.onBackground),
                cursorBrush = SolidColor(style.accent),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// Recently launched apps, tracked by StylePreferences.recordLaunch — not the
// system Overview/Recents screen, which a third-party launcher can't host.
@Composable
private fun RecentsWidget(
    style: CustomStyle,
    hazeState: HazeState,
    apps: List<AppEntry>,
    recentPackages: List<String>,
    onLaunch: (AppEntry) -> Unit
) {
    val recentApps = remember(apps, recentPackages) {
        recentPackages.mapNotNull { pkg -> apps.find { it.packageName == pkg } }
    }
    Column(Modifier.card(hazeState)) {
        WidgetLabel("Recent apps", style)
        if (recentApps.isEmpty()) {
            Text(
                "Apps you open will show up here",
                fontSize = 13.sp,
                color = style.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                recentApps.take(4).forEach { app ->
                    AppIcon(app, style = style, showLabel = false, onClick = { onLaunch(app) })
                }
            }
        }
    }
}

// Previews of active system notifications. Needs the user to grant
// notification-listener access from Settings first (Android doesn't offer a
// runtime dialog for this) — shows a prompt in place of the list until then.
@Composable
private fun NotificationsWidget(style: CustomStyle, hazeState: HazeState) {
    val context = LocalContext.current
    val enabled = ZenFoldNotificationListener.isEnabled(context)
    val previews by ZenFoldNotificationListener.previews.collectAsState()

    Column(Modifier.card(hazeState)) {
        WidgetLabel("Notifications", style)
        when {
            !enabled -> Text(
                "Tap to allow notification access",
                fontSize = 13.sp,
                color = style.onBackground,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clickable { ZenFoldNotificationListener.openSettings(context) }
            )
            previews.isEmpty() -> Text(
                "You're all caught up",
                fontSize = 13.sp,
                color = style.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
            else -> Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 6.dp)
            ) {
                previews.take(3).forEach { preview ->
                    Column {
                        Text(
                            preview.title.ifBlank { preview.packageName },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = style.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (preview.text.isNotBlank()) {
                            Text(
                                preview.text,
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
}
