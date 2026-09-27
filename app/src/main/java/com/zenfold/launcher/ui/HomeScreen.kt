package com.zenfold.launcher.ui

import android.annotation.SuppressLint
import android.content.Context
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
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
import com.zenfold.launcher.home.HomeApp
import com.zenfold.launcher.home.HomeFolder
import com.zenfold.launcher.home.HomeItem
import com.zenfold.launcher.home.HomeLayout
import com.zenfold.launcher.notifications.ZenFoldNotificationListener
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

/** A home grid cell, resolved to installed apps and ready to draw. */
private sealed interface HomeCell {
    data class App(val app: AppEntry) : HomeCell
    data class Folder(val name: String, val apps: List<AppEntry>) : HomeCell
}

@Composable
fun HomeScreen(
    apps: List<AppEntry>,
    style: CustomStyle,
    enabledWidgets: Set<WidgetType>,
    recentPackages: List<String>,
    homeItems: List<HomeItem>,
    hiddenApps: Set<String>,
    noteText: String,
    tasks: List<TaskItem>,
    feedKeys: FeedApiKeys,
    homeSignal: Int,
    onNoteChange: (String) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onHomeItemsChange: (List<HomeItem>) -> Unit,
    onHideApp: (String) -> Unit,
    onWidgetToggle: (WidgetType, Boolean) -> Unit,
    onAddTask: (String) -> Unit,
    onToggleTask: (String, Boolean) -> Unit,
    onRemoveTask: (String) -> Unit
) {
    val context = LocalContext.current
    var focusSearch by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var widgetPickerOpen by remember { mutableStateOf(false) }
    var openFolderAt by remember { mutableStateOf<GridPos?>(null) }
    val hazeState = remember { HazeState() }
    val now = rememberCurrentTimeMillis()
    val scope = rememberCoroutineScope()
    val badged by ZenFoldNotificationListener.badgedPackages.collectAsState()
    // Page 0: Today (calendar, tasks, feeds). Page 1: the actual home screen — that's
    // the default. Page 2: all apps, MIUI-style — swipe right from home to reach it,
    // the same place "swipe up" / tapping Search lands you (AppDrawer is just this page).
    val pagerState = rememberPagerState(initialPage = 1) { 3 }
    val drawerOpen by remember { derivedStateOf { pagerState.currentPage == 2 } }

    val appsByPackage = remember(apps) { apps.associateBy { it.packageName } }
    val visibleApps = remember(apps, hiddenApps) { apps.filterNot { it.packageName in hiddenApps } }
    // Uninstalled apps drop out here, not in storage — so an app mid-update never loses
    // its spot, and a missing one never leaves an invisible, occupied cell behind.
    val layout = remember(homeItems, appsByPackage) { HomeLayout.prune(homeItems, appsByPackage.keys) }
    val cells = remember(layout, appsByPackage) {
        layout.associate { item ->
            item.pos to when (item) {
                is HomeApp -> HomeCell.App(appsByPackage.getValue(item.packageName))
                is HomeFolder -> HomeCell.Folder(item.name, item.packages.map(appsByPackage::getValue))
            }
        }
    }
    val dock = remember(apps, hiddenApps) {
        AppRepository.homeApps(context, apps, emptyList()).dock.filterNot { it.packageName in hiddenApps }
    }
    // Recent apps first, topped up with the dock and Home so the row is never empty.
    val suggested = remember(visibleApps, recentPackages, dock, layout) {
        val visible = visibleApps.associateBy { it.packageName }
        (recentPackages + dock.map { it.packageName } + layout.flatMap { it.packages })
            .distinct()
            .mapNotNull(visible::get)
            .take(4)
    }
    val openFolder = openFolderAt?.let { cells[it] as? HomeCell.Folder }
    val overlayOpen = openFolder != null || widgetPickerOpen
    val homeContentAlpha by animateFloatAsState(if (overlayOpen) 0f else 1f, label = "homeContentAlpha")

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    fun edit(next: List<HomeItem>?, whenFull: String = "Home screen is full — drop apps onto each other to make folders") {
        when {
            next == null -> toast(whenFull)
            next != layout -> onHomeItemsChange(next)
        }
    }

    fun addToHome(app: AppEntry) {
        if (HomeLayout.contains(layout, app.packageName)) {
            toast("${app.label} is already on Home")
        } else {
            val next = HomeLayout.addApp(layout, app.packageName)
            edit(next)
            if (next != null) toast("Added ${app.label} to Home")
        }
    }

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
        openFolderAt = null
        pagerState.scrollToPage(1)
    }
    // A folder that stops being a folder (its last-but-one app moved out) closes itself.
    LaunchedEffect(openFolder == null) {
        if (openFolder == null) openFolderAt = null
    }
    BackHandler(enabled = drawerOpen) { closeDrawer() }
    BackHandler(enabled = widgetPickerOpen) { widgetPickerOpen = false }
    BackHandler(enabled = openFolderAt != null) { openFolderAt = null }

    val swipeThreshold = with(LocalDensity.current) { 72.dp.toPx() }
    // Swipe up anywhere on Home opens the drawer; swipe down pulls the notification shade.
    val homeSwipes = remember(swipeThreshold) {
        object : NestedScrollConnection {
            var pulledUp = 0f
            var pulledDown = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.Drag) return Offset.Zero
                if (available.y < 0f) {
                    pulledUp -= available.y
                    if (pulledUp > swipeThreshold) {
                        pulledUp = 0f
                        openDrawer(withKeyboard = false)
                    }
                } else if (available.y > 0f) {
                    pulledDown += available.y
                    if (pulledDown > swipeThreshold) {
                        pulledDown = 0f
                        expandNotificationShade(context)
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                pulledUp = 0f
                pulledDown = 0f
                return Velocity.Zero
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Wallpaper(style, hazeState, Modifier.fillMaxSize())

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !overlayOpen,
            modifier = Modifier.fillMaxSize()
        ) { page ->
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
                        // Faded out under an open folder / the widget picker: Haze only
                        // blurs the wallpaper, so Home would show through their glass.
                        .graphicsLayer { alpha = homeContentAlpha }
                        .nestedScroll(homeSwipes)
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
                        HomeGrid(
                            cells = cells,
                            style = style,
                            badged = badged,
                            onLaunch = onLaunch,
                            onOpenFolder = { openFolderAt = it },
                            onDrop = { from, to ->
                                edit(HomeLayout.drop(layout, from, to) { packages -> folderNameFor(context, packages) })
                            },
                            appActions = { app ->
                                listOf(MenuAction("Remove from Home") { edit(HomeLayout.removeApp(layout, app.packageName)) })
                            },
                            onRemoveFolder = { pos ->
                                edit(HomeLayout.removeAt(layout, pos))
                                toast("Folder removed — its apps are still in the app drawer")
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    Spacer(Modifier.height(12.dp))
                    SearchPill(style, hazeState, onClick = { openDrawer(withKeyboard = true) })
                    if (dock.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Dock(dock, style, hazeState, badged, onLaunch)
                    }
                    Spacer(Modifier.height(10.dp))
                }
                // Page 2: every installed app — reached by swiping right from Home, the
                // same as "swipe up" or tapping Search (both just animate the pager here).
                else -> AppDrawer(
                    apps = visibleApps,
                    suggested = suggested,
                    style = style,
                    hazeState = hazeState,
                    badged = badged,
                    query = query,
                    onQueryChange = { query = it },
                    focusSearch = focusSearch,
                    appActions = { app ->
                        listOf(
                            MenuAction("Add to Home") { addToHome(app) },
                            MenuAction("Hide app") {
                                onHideApp(app.packageName)
                                toast("${app.label} hidden — unhide it in ZenFold Settings")
                            }
                        )
                    },
                    onLaunch = { app ->
                        onLaunch(app)
                        closeDrawer()
                    },
                    onDismiss = { closeDrawer() }
                )
            }
        }

        val folderPos = openFolderAt
        if (folderPos != null && openFolder != null) {
            FolderOverlay(
                name = openFolder.name,
                apps = openFolder.apps,
                style = style,
                hazeState = hazeState,
                badged = badged,
                menuActions = { app ->
                    listOf(
                        MenuAction("Remove from folder") {
                            edit(HomeLayout.moveOutOfFolder(layout, folderPos, app.packageName))
                        },
                        MenuAction("Remove from Home") { edit(HomeLayout.removeApp(layout, app.packageName)) }
                    )
                },
                onLaunch = { app ->
                    openFolderAt = null
                    onLaunch(app)
                },
                onRename = { name -> edit(HomeLayout.renameFolder(layout, folderPos, name)) },
                onDismiss = { openFolderAt = null }
            )
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

// EXPAND_STATUS_BAR is an auto-granted permission, but there's no public API for this —
// it's the same hidden StatusBarManager call every third-party launcher relies on.
@SuppressLint("WrongConstant")
private fun expandNotificationShade(context: Context) {
    try {
        val statusBar = context.getSystemService("statusbar") ?: return
        statusBar.javaClass.getMethod("expandNotificationsPanel").invoke(statusBar)
    } catch (e: ReflectiveOperationException) {
        // A ROM that removed or renamed it: the gesture just does nothing there.
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

// Long-press and drag any icon or folder: onto an empty cell to move it, an app onto
// another app to make a folder, an app onto a folder to add it. Long-press WITHOUT
// moving (release near where you picked it up) opens its menu instead.
@Composable
private fun HomeGrid(
    cells: Map<GridPos, HomeCell>,
    style: CustomStyle,
    badged: Set<String>,
    onLaunch: (AppEntry) -> Unit,
    onOpenFolder: (GridPos) -> Unit,
    onDrop: (from: GridPos, to: GridPos) -> Unit,
    appActions: (AppEntry) -> List<MenuAction>,
    onRemoveFolder: (GridPos) -> Unit
) {
    val density = LocalDensity.current
    var dragFrom by remember { mutableStateOf<GridPos?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var hoverTarget by remember { mutableStateOf<GridPos?>(null) }
    var menuAt by remember { mutableStateOf<GridPos?>(null) }
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
            hoverTarget?.takeIf { it != dragFrom }?.let { target ->
                val shape = RoundedCornerShape(20.dp)
                Box(
                    Modifier
                        .offset { IntOffset((target.col * cellPx).roundToInt(), (target.row * rowPx).roundToInt()) }
                        .size(cellDp, rowDp)
                        .padding(4.dp)
                        // Onto an occupied cell = make or join a folder: a ring, not a fill.
                        .then(
                            if (target in cells) {
                                Modifier.border(2.dp, style.accent.copy(alpha = 0.8f), shape)
                            } else {
                                Modifier.background(style.accent.copy(alpha = 0.22f), shape)
                            }
                        )
                )
            }

            cells.forEach { (pos, cell) ->
                key(pos, cell) {
                    val isDragging = pos == dragFrom
                    val baseX = pos.col * cellPx
                    val baseY = pos.row * rowPx

                    fun resetDrag() {
                        dragFrom = null
                        hoverTarget = null
                        dragOffset = Offset.Zero
                    }

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
                            .pointerInput(pos, cell) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        dragFrom = pos
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
                                        val target = hoverTarget
                                        if (!moved) {
                                            menuAt = pos
                                        } else if (target != null && target != pos) {
                                            onDrop(pos, target)
                                        }
                                        resetDrag()
                                    },
                                    onDragCancel = { resetDrag() }
                                )
                            },
                        contentAlignment = Alignment.TopCenter
                    ) {
                        when (cell) {
                            is HomeCell.App -> {
                                AppIcon(
                                    cell.app,
                                    style = style,
                                    showLabel = style.showHomeLabels,
                                    badged = cell.app.packageName in badged,
                                    onClick = { onLaunch(cell.app) }
                                )
                                AppActionsMenu(
                                    cell.app,
                                    expanded = menuAt == pos,
                                    onDismiss = { menuAt = null },
                                    actions = if (menuAt == pos) appActions(cell.app) else emptyList()
                                )
                            }
                            is HomeCell.Folder -> {
                                FolderIcon(
                                    name = cell.name,
                                    apps = cell.apps,
                                    style = style,
                                    showLabel = style.showHomeLabels,
                                    badged = cell.apps.any { it.packageName in badged },
                                    onClick = { onOpenFolder(pos) }
                                )
                                DropdownMenu(expanded = menuAt == pos, onDismissRequest = { menuAt = null }) {
                                    DropdownMenuItem(text = { Text("Open") }, onClick = { menuAt = null; onOpenFolder(pos) })
                                    DropdownMenuItem(
                                        text = { Text("Remove folder from Home") },
                                        onClick = { menuAt = null; onRemoveFolder(pos) }
                                    )
                                }
                            }
                        }
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
private fun Dock(
    apps: List<AppEntry>,
    style: CustomStyle,
    hazeState: HazeState,
    badged: Set<String>,
    onLaunch: (AppEntry) -> Unit
) {
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
            AppIcon(app, style = style, showLabel = false, badged = app.packageName in badged, onClick = { onLaunch(app) })
        }
    }
}
