package com.zenfold.launcher.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenfold.launcher.style.CustomStyle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** A first-party widget the user can add to or remove from the home screen. */
enum class WidgetType(val id: String, val title: String) {
    GLANCE("glance", "Glance"),
    CALENDAR("calendar", "Calendar"),
    NOTE("note", "Note")
}

@Composable
fun WidgetArea(
    enabled: Set<WidgetType>,
    style: CustomStyle,
    hazeState: HazeState,
    noteText: String,
    onNoteChange: (String) -> Unit
) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (WidgetType.GLANCE in enabled) GlanceWidget(style, hazeState)
        if (WidgetType.CALENDAR in enabled) CalendarWidget(style, hazeState)
        if (WidgetType.NOTE in enabled) NoteWidget(style, hazeState, noteText, onNoteChange)
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
