package com.zenfold.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenfold.launcher.AppEntry
import com.zenfold.launcher.style.*
import com.zenfold.launcher.ui.theme.ZenFoldTheme
import dev.chrisbanes.haze.HazeState

/** The preview is local until Apply. Cancel never writes preferences. */
@Composable
fun ThemeStudio(
    current: CustomStyle,
    apps: List<AppEntry>,
    onApply: (CustomStyle, Boolean, Boolean) -> Unit,
    onClose: () -> Unit
) {
    var selectedId by rememberSaveable { mutableStateOf(StudioThemes.all.first().id) }
    var matchingWallpaper by rememberSaveable { mutableStateOf(true) }
    var themeIcons by rememberSaveable { mutableStateOf(true) }
    val theme = StudioThemes.all.first { it.id == selectedId }
    val style = theme.style
    val now = rememberCurrentTimeMillis()
    BackHandler(onBack = onClose)
    ZenFoldTheme(style) {
        Column(Modifier.fillMaxSize().background(style.background).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose) { Text("Back") }
                Text("Design studio", Modifier.weight(1f), fontSize = 20.sp, color = style.onBackground)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(theme.name, fontSize = 38.sp, fontWeight = FontWeight.Light, color = style.onBackground)
                Text(theme.description, fontSize = 14.sp, color = style.onSurfaceVariant)
                Box(Modifier.fillMaxWidth().height(if (style.clockDesign == ClockDesign.STACKED) 390.dp else 320.dp)
                    .clip(RoundedCornerShape(28.dp))) {
                    Wallpaper(style, remember { HazeState() }, false, Modifier.fillMaxSize())
                    Column(Modifier.fillMaxSize().padding(24.dp)) {
                        Text("THEME PREVIEW", color = style.onSurfaceVariant, fontSize = 10.sp, letterSpacing = 2.sp)
                        Spacer(Modifier.height(18.dp))
                        StudioClock(style, now, compact = true)
                        Spacer(Modifier.weight(1f))
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(style.surface.copy(alpha = 0.85f))
                            .padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            apps.take(4).forEach { app ->
                                AppIconImage(app, style, Modifier.size(42.dp).clip(style.iconShapeKind.shape))
                            }
                        }
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(StudioThemes.all, key = { it.id }) { item ->
                        FilterChip(selected = item.id == selectedId, onClick = { selectedId = item.id }, label = { Text(item.name) })
                    }
                }
                StudioToggle("Matching home wallpaper", matchingWallpaper, { matchingWallpaper = it })
                StudioToggle("Use theme icons", themeIcons, { themeIcons = it })
                Text("Preview shows the complete theme. Turn options off to keep your wallpaper or installed icon pack. Apps, folders and gestures stay where you put them.",
                    fontSize = 13.sp, color = style.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
            }
            Button(onClick = { onApply(StudioThemes.withCurrentNavigation(style, current), matchingWallpaper, themeIcons); onClose() },
                modifier = Modifier.fillMaxWidth().padding(20.dp).heightIn(min = 52.dp), shape = RoundedCornerShape(18.dp)) {
                Text("Apply ${theme.name}")
            }
        }
    }
}

@Composable
private fun StudioToggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f).padding(end = 12.dp), color = MaterialTheme.colorScheme.onBackground)
        Switch(checked = value, onCheckedChange = onChange)
    }
}
