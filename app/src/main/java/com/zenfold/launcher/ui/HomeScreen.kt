package com.zenfold.launcher.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.zenfold.launcher.AppEntry
import com.zenfold.launcher.AppRepository
import com.zenfold.launcher.GRID_COLUMNS
import com.zenfold.launcher.GRID_ROWS
import com.zenfold.launcher.GridPos
import com.zenfold.launcher.feeds.FeedApiKeys
import com.zenfold.launcher.style.CustomStyle
import com.zenfold.launcher.tasks.TaskItem
import com.zenfold.launcher.widgets.WidgetArea
import com.zenfold.launcher.widgets.WidgetPickerOverlay
import com.zenfold.launcher.widgets.WidgetType
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    apps: List<AppEntry>,
    style: CustomStyle,
    enabledWidgets: Set<WidgetType>,
    recentPackages: List<String>,
    homeLayout: Map<String, GridPos>,
    hiddenHomeApps: Set<String>,
    noteText: String,
    tasks: List<TaskItem>,
    feedKeys: FeedApiKeys,
    homeSignal: Int,
    onNoteChange: (String) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onMoveApp: (String, GridPos) -> Unit,
    onHideFromHome: (String) -> Unit,
    onWidgetToggle: (WidgetType, Boolean) -> Unit,
    onAddTask: (String) -> Unit,
    onToggleTask: (String, Boolean) -> Unit,
    onRemoveTask: (String) -> Unit
) {
    val context = LocalContext.current
    var focusSearch by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var widgetPickerOpen by remember { mutableStateOf(false) }
    val hazeState = remember { HazeState() }
    val now = rememberCurrentTimeMillis()
    val scope = rememberCoroutineScope()
    // Page 0: Today (calendar, tasks, feeds). Page 1: the actual home screen — that's
    // the default. Page 2: all apps, MIUI-style — swipe right from home to reach it,
    // the same place "swipe up" / tapping Search lands you (AppDrawer is just this page).
    val pagerState = rememberPagerState(initialPage = 1) { 3 }
    val drawerOpen by remember { derivedStateOf { pagerState.currentPage == 2 } }

    val home = remember(apps, recentPackages) { AppRepository.homeApps(context, apps, recentPackages) }
    // Recent apps first, topped up with the dock/grid defaults so the row is never empty.
    val suggested = remember(apps, recentPackages, home) {
        (recentPackages.mapNotNull { pkg -> apps.find { it.packageName == pkg } } + home.dock + home.grid)
            .distinctBy { it.packageName }
            .take(4)
    }
    // Apps keep a position the user dragged them to; anything else fills the remaining
    // cells in order, so a freshly installed app (or first run) still shows up somewhere.
    // "Removed from home" apps (long-press → Remove) are filtered out before that happens.
    val gridApps = remember(home.grid, hiddenHomeApps) { home.grid.filterNot { it.packageName in hiddenHomeApps } }
    val slots = remember(gridApps, homeLayout) { layoutGrid(gridApps, homeLayout) }

    fun openDrawer(withKeyboard: Boolean) {
        query = ""
        focusSearch = withKeyboard
        scope.launch { pagerState.animateScrollToPage(2) }
    }

    fun closeDrawer() {
        focusSearch = false
        scope.launch { pagerState.animateScrollToPage(1) }
    }

    // Pressing Home while ZenFold is already showing should drop back to the home page.
    LaunchedEffect(homeSignal) {
        focusSearch = false
        widgetPickerOpen = false
        pagerState.scrollToPage(1)
    }
    BackHandler(enabled = drawerOpen) { closeDrawer() }
    BackHandler(enabled = widgetPickerOpen) { widgetPickerOpen = false }

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

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> TodayPanel(
                    style = style,
                    hazeState = hazeState,
                    tasks = tasks,
                    feedKeys = feedKeys,
                    onAddTask = onAddTask,
                    onToggleTask = onToggleTask,
                    onRemoveTask = onRemoveTask
                )
                1 -> Column(
                    Modifier
                        .fillMaxSize()
                        .nestedScroll(swipeUpToOpen)
                        .pointerInput(Unit) { detectTapGestures(onLongPress = { widgetPickerOpen = true }) }
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
                        FavoritesGrid(
                            slots = slots,
                            style = style,
                            onLaunch = onLaunch,
                            onMove = { app, from, to ->
                                val occupant = slots[to]?.takeIf { it.packageName != app.packageName }
                                onMoveApp(app.packageName, to)
                                occupant?.let { onMoveApp(it.packageName, from) }
                            },
                            onRemove = { onHideFromHome(it.packageName) }
                        )
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
                // Page 2: every installed app — reached by swiping right from Home, the
                // same as "swipe up" or tapping Search (both just animate the pager here).
                else -> AppDrawer(
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

        if (widgetPickerOpen) {
            WidgetPickerOverlay(
                style = style,
                hazeState = hazeState,
                enabled = enabledWidgets,
                onToggle = onWidgetToggle,
                onDismiss = { widgetPickerOpen = false }
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

// Apps with a saved position keep it; everyone else fills the remaining cells in order
// (row-major), so newly-surfaced defaults or recents just appear in the first open spot.
private fun layoutGrid(candidates: List<AppEntry>, saved: Map<String, GridPos>): Map<GridPos, AppEntry> {
    val slots = mutableMapOf<GridPos, AppEntry>()
    val placed = mutableSetOf<String>()
    candidates.forEach { app ->
        val pos = saved[app.packageName]
        if (pos != null && pos.row in 0 until GRID_ROWS && pos.col in 0 until GRID_COLUMNS && pos !in slots) {
            slots[pos] = app
            placed += app.packageName
        }
    }
    var cursor = 0
    candidates.forEach { app ->
        if (app.packageName in placed) return@forEach
        while (cursor < GRID_ROWS * GRID_COLUMNS && GridPos(cursor / GRID_COLUMNS, cursor % GRID_COLUMNS) in slots) cursor++
        if (cursor >= GRID_ROWS * GRID_COLUMNS) return@forEach
        slots[GridPos(cursor / GRID_COLUMNS, cursor % GRID_COLUMNS)] = app
        cursor++
    }
    return slots
}

private fun openAppInfo(context: android.content.Context, app: AppEntry) {
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null)))
}

private fun uninstallApp(context: android.content.Context, app: AppEntry) {
    context.startActivity(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null)))
}

// Long-press and drag any icon to an empty cell, or onto another icon to swap places —
// positions are saved per app, not a fixed list order. Long-press WITHOUT moving it
// (release near where you picked it up) opens a menu instead: App info / Remove /
// Uninstall.
@Composable
private fun FavoritesGrid(
    slots: Map<GridPos, AppEntry>,
    style: CustomStyle,
    onLaunch: (AppEntry) -> Unit,
    onMove: (app: AppEntry, from: GridPos, to: GridPos) -> Unit,
    onRemove: (AppEntry) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var dragging by remember { mutableStateOf<AppEntry?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragOrigin by remember { mutableStateOf(GridPos(0, 0)) }
    var hoverTarget by remember { mutableStateOf<GridPos?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    val haptics = LocalHapticFeedback.current
    val tapSlopPx = with(density) { 12.dp.toPx() }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cellPx = with(density) { maxWidth.toPx() } / GRID_COLUMNS
        val rowPx = cellPx * 1.3f
        val cellDp = with(density) { cellPx.toDp() }
        val rowDp = with(density) { rowPx.toDp() }

        Box(
            Modifier
                .fillMaxWidth()
                .height(rowDp * GRID_ROWS)
        ) {
            hoverTarget?.takeIf { it != dragOrigin }?.let { target ->
                Box(
                    Modifier
                        .offset { IntOffset((target.col * cellPx).roundToInt(), (target.row * rowPx).roundToInt()) }
                        .size(cellDp, rowDp)
                        .padding(4.dp)
                        .background(style.accent.copy(alpha = 0.22f), RoundedCornerShape(20.dp))
                )
            }

            slots.forEach { (pos, app) ->
                val isDragging = dragging?.packageName == app.packageName
                val baseX = pos.col * cellPx
                val baseY = pos.row * rowPx

                Box(
                    Modifier
                        .offset {
                            if (isDragging) {
                                IntOffset((baseX + dragOffset.x).roundToInt(), (baseY + dragOffset.y).roundToInt())
                            } else {
                                IntOffset(baseX.roundToInt(), baseY.roundToInt())
                            }
                        }
                        .size(cellDp, rowDp)
                        .zIndex(if (isDragging) 1f else 0f)
                        // Keyed on the app's position too: after a swap, this cell's
                        // coordinates change, and the gesture detector needs to restart
                        // to pick up the new baseX/baseY rather than keep stale ones.
                        .pointerInput(app.packageName, pos) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    dragging = app
                                    dragOrigin = pos
                                    dragOffset = Offset.Zero
                                    hoverTarget = pos
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount
                                    val col = ((baseX + dragOffset.x + cellPx / 2) / cellPx).toInt().coerceIn(0, GRID_COLUMNS - 1)
                                    val row = ((baseY + dragOffset.y + rowPx / 2) / rowPx).toInt().coerceIn(0, GRID_ROWS - 1)
                                    hoverTarget = GridPos(row, col)
                                },
                                onDragEnd = {
                                    val moved = hypot(dragOffset.x, dragOffset.y) > tapSlopPx
                                    if (moved) {
                                        hoverTarget?.takeIf { it != dragOrigin }?.let { onMove(app, dragOrigin, it) }
                                    } else {
                                        menuFor = app.packageName
                                    }
                                    dragging = null
                                    hoverTarget = null
                                    dragOffset = Offset.Zero
                                },
                                onDragCancel = {
                                    dragging = null
                                    hoverTarget = null
                                    dragOffset = Offset.Zero
                                }
                            )
                        },
                    contentAlignment = Alignment.TopCenter
                ) {
                    AppIcon(app, style = style, showLabel = style.showHomeLabels, onClick = { onLaunch(app) })
                    DropdownMenu(expanded = menuFor == app.packageName, onDismissRequest = { menuFor = null }) {
                        DropdownMenuItem(text = { Text("App info") }, onClick = { menuFor = null; openAppInfo(context, app) })
                        DropdownMenuItem(text = { Text("Remove from Home") }, onClick = { menuFor = null; onRemove(app) })
                        DropdownMenuItem(text = { Text("Uninstall") }, onClick = { menuFor = null; uninstallApp(context, app) })
                    }
                }
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
