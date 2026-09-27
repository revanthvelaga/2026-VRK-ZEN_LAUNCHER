package com.zenfold.launcher.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenfold.launcher.AppEntry
import com.zenfold.launcher.notifications.ZenFoldNotificationListener
import com.zenfold.launcher.style.CustomStyle
import com.zenfold.launcher.ui.AppIcon
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** A first-party widget the user can add to or remove from the home screen. */
enum class WidgetType(val id: String, val title: String) {
    GLANCE("glance", "Glance"),
    CALENDAR("calendar", "Calendar"),
    NOTE("note", "Note"),
    RECENTS("recents", "Recent apps"),
    NOTIFICATIONS("notifications", "Notifications")
}

@Composable
fun WidgetArea(
    enabled: Set<WidgetType>,
    style: CustomStyle,
    hazeState: HazeState,
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
        if (WidgetType.GLANCE in enabled) GlanceWidget(style, hazeState)
        if (WidgetType.CALENDAR in enabled) CalendarWidget(style, hazeState)
        if (WidgetType.NOTE in enabled) NoteWidget(style, hazeState, noteText, onNoteChange)
        if (WidgetType.RECENTS in enabled) RecentsWidget(style, hazeState, apps, recentPackages, onLaunch)
        if (WidgetType.NOTIFICATIONS in enabled) NotificationsWidget(style, hazeState)
    }
}

private val widgetShape = RoundedCornerShape(20.dp)

// hazeChild draws a real-time blur of whatever sits behind (the Wallpaper's
// glow blobs, marked with .haze() in HomeScreen); the translucent background
// on top of it is the tint, same as the earlier HTML mockup's .glass class.
private fun widgetCardModifier(style: CustomStyle, hazeState: HazeState) = Modifier
    .fillMaxWidth()
    .hazeChild(state = hazeState, shape = widgetShape)
    .background(style.surface.copy(alpha = 0.35f), widgetShape)
    .padding(16.dp)

@Composable
private fun GlanceWidget(style: CustomStyle, hazeState: HazeState) {
    Row(widgetCardModifier(style, hazeState), horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text("24° Clear", fontSize = 15.sp, color = style.onBackground)
            Text("High 27° · Low 18°", fontSize = 12.sp, color = style.onSurfaceVariant)
        }
        Column {
            Text("2:00 PM", fontSize = 15.sp, color = style.onBackground)
            Text("Design review", fontSize = 12.sp, color = style.onSurfaceVariant)
        }
    }
}

@Composable
private fun CalendarWidget(style: CustomStyle, hazeState: HazeState) {
    val today = remember { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Calendar.getInstance().time) }
    Column(widgetCardModifier(style, hazeState)) {
        Text("Today", fontSize = 12.sp, color = style.onSurfaceVariant)
        Text(today, fontSize = 16.sp, color = style.onBackground)
    }
}

@Composable
private fun NoteWidget(style: CustomStyle, hazeState: HazeState, text: String, onTextChange: (String) -> Unit) {
    Column(widgetCardModifier(style, hazeState)) {
        Text("Note", fontSize = 12.sp, color = style.onSurfaceVariant)
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            textStyle = TextStyle(fontSize = 14.sp, color = style.onBackground),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
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
    Column(widgetCardModifier(style, hazeState)) {
        Text("Recent apps", fontSize = 12.sp, color = style.onSurfaceVariant)
        if (recentApps.isEmpty()) {
            Text(
                "Nothing opened yet",
                fontSize = 13.sp,
                color = style.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                recentApps.take(6).forEach { app ->
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

    Column(widgetCardModifier(style, hazeState)) {
        Text("Notifications", fontSize = 12.sp, color = style.onSurfaceVariant)
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
                "No notifications",
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
