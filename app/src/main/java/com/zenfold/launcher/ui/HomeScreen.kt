package com.zenfold.launcher.ui

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenfold.launcher.AppEntry
import com.zenfold.launcher.AppRepository
import com.zenfold.launcher.style.CustomStyle
import com.zenfold.launcher.widgets.WidgetArea
import com.zenfold.launcher.widgets.WidgetType
import dev.chrisbanes.haze.HazeState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    apps: List<AppEntry>,
    style: CustomStyle,
    enabledWidgets: Set<WidgetType>,
    recentPackages: List<String>,
    noteText: String,
    homeSignal: Int,
    onNoteChange: (String) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    var drawerOpen by remember { mutableStateOf(false) }
    var focusSearch by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val hazeState = remember { HazeState() }
    val now = rememberCurrentTimeMillis()

    val home = remember(apps, recentPackages) { AppRepository.homeApps(context, apps, recentPackages) }
    val suggested = remember(apps, recentPackages) {
        recentPackages.mapNotNull { pkg -> apps.find { it.packageName == pkg } }.take(4)
    }

    fun openDrawer(withKeyboard: Boolean) {
        query = ""
        focusSearch = withKeyboard
        drawerOpen = true
    }

    fun closeDrawer() {
        drawerOpen = false
        focusSearch = false
    }

    // Pressing Home while ZenFold is already showing should drop back to the home page.
    LaunchedEffect(homeSignal) { closeDrawer() }
    BackHandler(enabled = drawerOpen) { closeDrawer() }

    val openThreshold = with(LocalDensity.current) { 72.dp.toPx() }
    val swipeUpToOpen = remember(openThreshold) {
        object : NestedScrollConnection {
            var pulled = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.Drag && available.y < 0f) {
                    pulled -= available.y
                    if (pulled > openThreshold) {
                        pulled = 0f
                        openDrawer(withKeyboard = false)
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                pulled = 0f
                return Velocity.Zero
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Wallpaper(style, hazeState, Modifier.fillMaxSize())

        Column(
            Modifier
                .fillMaxSize()
                .nestedScroll(swipeUpToOpen)
                .pointerInput(Unit) { detectTapGestures(onLongPress = { onOpenSettings() }) }
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Spacer(Modifier.height(20.dp))
                ClockBlock(style, now)
                WidgetArea(
                    enabled = enabledWidgets,
                    style = style,
                    hazeState = hazeState,
                    now = now,
                    apps = apps,
                    recentPackages = recentPackages,
                    noteText = noteText,
                    onNoteChange = onNoteChange,
                    onLaunch = onLaunch
                )
                FavoritesGrid(home.grid, style, onLaunch)
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(12.dp))
            SearchPill(style, hazeState, onClick = { openDrawer(withKeyboard = true) })
            if (home.dock.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Dock(home.dock, style, hazeState, onLaunch)
            }
            Spacer(Modifier.height(10.dp))
        }

        AnimatedVisibility(
            visible = drawerOpen,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 4 })
        ) {
            AppDrawer(
                apps = apps,
                suggested = suggested,
                style = style,
                hazeState = hazeState,
                query = query,
                onQueryChange = { query = it },
                focusSearch = focusSearch,
                onLaunch = { app ->
                    onLaunch(app)
                    closeDrawer()
                },
                onDismiss = { closeDrawer() }
            )
        }
    }
}

@Composable
private fun ClockBlock(style: CustomStyle, now: Long) {
    val context = LocalContext.current
    val is24Hour = DateFormat.is24HourFormat(context)
    val time = remember(now, is24Hour) {
        SimpleDateFormat(if (is24Hour) "H:mm" else "h:mm", Locale.getDefault()).format(Date(now))
    }
    val date = remember(now) { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date(now)) }
    val clockSize = 72 * style.fontScale.scale

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (style.clockCentered) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Text(
            text = time,
            style = TextStyle(
                fontSize = clockSize.sp,
                lineHeight = (clockSize * 1.1f).sp,
                fontWeight = style.clockWeight,
                letterSpacing = (-1).sp,
                color = style.onBackground,
                shadow = Shadow(color = style.secondary.copy(alpha = 0.35f), blurRadius = 60f)
            )
        )
        Text(
            text = date,
            fontSize = (14 * style.fontScale.scale).sp,
            fontWeight = FontWeight.Medium,
            color = style.onSurfaceVariant
        )
    }
}

@Composable
private fun FavoritesGrid(apps: List<AppEntry>, style: CustomStyle, onLaunch: (AppEntry) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        apps.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { app ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        AppIcon(app, style = style, showLabel = style.showHomeLabels, onClick = { onLaunch(app) })
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SearchPill(style: CustomStyle, hazeState: HazeState, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .glass(hazeState, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = style.onBackground.copy(alpha = 0.72f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "Search",
            fontSize = 14.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = style.onBackground.copy(alpha = 0.72f)
        )
    }
}

@Composable
private fun Dock(apps: List<AppEntry>, style: CustomStyle, hazeState: HazeState, onLaunch: (AppEntry) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(78.dp)
            .glass(hazeState, RoundedCornerShape(30.dp))
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        apps.forEach { app ->
            AppIcon(app, style = style, showLabel = false, onClick = { onLaunch(app) })
        }
    }
}
