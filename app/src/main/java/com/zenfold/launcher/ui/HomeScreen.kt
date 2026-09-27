package com.zenfold.launcher.ui

import android.annotation.SuppressLint
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.zenfold.launcher.AppEntry
import com.zenfold.launcher.GridPos
import com.zenfold.launcher.SettingsActivity
import com.zenfold.launcher.feeds.FeedApiKeys
import com.zenfold.launcher.home.GridSpec
import com.zenfold.launcher.home.HomeApp
import com.zenfold.launcher.home.HomeFolder
import com.zenfold.launcher.home.HomeItem
import com.zenfold.launcher.home.HomeLayout
import com.zenfold.launcher.notifications.ZenFoldNotificationListener
import com.zenfold.launcher.style.CustomStyle
import com.zenfold.launcher.style.MAX_DOCK_APPS
import com.zenfold.launcher.system.LockScreenService
import com.zenfold.launcher.tasks.TaskItem
import com.zenfold.launcher.widgets.HostedWidget
import com.zenfold.launcher.widgets.WidgetArea
import com.zenfold.launcher.widgets.WidgetPickerOverlay
import com.zenfold.launcher.widgets.WidgetType
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.hypot
import kotlin.math.roundToInt

/** A home grid cell, resolved to installed apps and ready to draw. */
private sealed interface HomeCell {
    data class App(val app: AppEntry) : HomeCell
    data class Folder(val name: String, val apps: List<AppEntry>) : HomeCell
}

// Page dots + dock, drawn over the bottom of every home page; pages leave this much room.
private val DOCK_AREA_HEIGHT = 112.dp

private fun lerp(start: Float, stop: Float, fraction: Float) = start + (stop - start) * fraction

@Composable
fun HomeScreen(
    apps: List<AppEntry>,
    style: CustomStyle,
    enabledWidgets: Set<WidgetType>,
    recentPackages: List<String>,
    homeItems: List<HomeItem>,
    gridSpec: GridSpec,
    dockPackages: List<String>,
    hiddenApps: Set<String>,
    appWidgetHost: AppWidgetHost,
    hostedWidgetIds: List<Int>,
    noteText: String,
    tasks: List<TaskItem>,
    feedKeys: FeedApiKeys,
    homeSignal: Int,
    onNoteChange: (String) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onHomeEdit: ((List<HomeItem>) -> List<HomeItem>) -> Unit,
    onDockEdit: ((List<String>) -> List<String>) -> Unit,
    onHideApp: (String) -> Unit,
    onWidgetToggle: (WidgetType, Boolean) -> Unit,
    onAddAndroidWidget: (AppWidgetProviderInfo) -> Unit,
    onRemoveAndroidWidget: (Int) -> Unit,
    onAddTask: (String) -> Unit,
    onToggleTask: (String, Boolean) -> Unit,
    onRemoveTask: (String) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var focusSearch by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var homeMenuOpen by remember { mutableStateOf(false) }
    var widgetPickerOpen by remember { mutableStateOf(false) }
    var appPickerOpen by remember { mutableStateOf(false) }
    var openFolderAt by remember { mutableStateOf<GridPos?>(null) }
    val dragState = remember { mutableStateOf<HomeDrag?>(null) }
    val dragging by remember { derivedStateOf { dragState.value != null } }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val hazeState = remember { HazeState() }
    val now = rememberCurrentTimeMillis()
    val scope = rememberCoroutineScope()
    val badged by ZenFoldNotificationListener.badgedPackages.collectAsState()

    val appsByPackage = remember(apps) { apps.associateBy { it.packageName } }
    val visibleApps = remember(apps, hiddenApps) { apps.filterNot { it.packageName in hiddenApps } }
    val visibleByPackage = remember(visibleApps) { visibleApps.associateBy { it.packageName } }
    // Uninstalled apps drop out here, not in storage — so an app mid-update never loses its
    // spot, and a missing one never leaves an invisible, occupied cell behind.
    val installed = appsByPackage.keys
    val layout = remember(homeItems, installed, gridSpec) {
        HomeLayout.normalize(HomeLayout.prune(homeItems, installed), gridSpec)
    }
    val cells = remember(layout, appsByPackage) {
        layout.associate { item ->
            item.pos to when (item) {
                is HomeApp -> HomeCell.App(appsByPackage.getValue(item.packageName))
                is HomeFolder -> HomeCell.Folder(item.name, item.packages.map(appsByPackage::getValue))
            }
        }
    }
    val onHome = remember(layout) { layout.flatMapTo(HashSet()) { it.packages } }
    val dock = remember(dockPackages, visibleByPackage) { dockPackages.mapNotNull(visibleByPackage::get) }
    // Recent apps first, topped up with the dock and Home so the row is never empty.
    val suggested = remember(visibleByPackage, recentPackages, dock, layout) {
        (recentPackages + dock.map { it.packageName } + layout.flatMap { it.packages })
            .distinct()
            .mapNotNull(visibleByPackage::get)
            .take(4)
    }

    // Pager: 0 = Today, 1..homePages = home pages, last = all apps (swipe right, MIUI-style).
    val homePages = HomeLayout.pageCount(layout)
    val homePagesState = rememberUpdatedState(homePages)
    val pagerState = rememberPagerState(initialPage = 1) { homePagesState.value + 2 }
    val drawerOpen by remember { derivedStateOf { pagerState.currentPage == pagerState.pageCount - 1 } }

    val openFolder = openFolderAt?.let { cells[it] as? HomeCell.Folder }
    val overlayOpen = openFolder != null || widgetPickerOpen || homeMenuOpen || appPickerOpen
    val homeContentAlpha by animateFloatAsState(if (overlayOpen) 0f else 1f, label = "homeContentAlpha")
    // How "on a home page" the pager is (0 on Today/drawer, 1 on Home) — fades the dock.
    val dockAlpha = remember {
        derivedStateOf {
            val position = pagerState.currentPage + pagerState.currentPageOffsetFraction
            val lastHome = (pagerState.pageCount - 2).toFloat()
            val outside = maxOf(0f, 1f - position) + maxOf(0f, position - lastHome)
            (1f - outside).coerceIn(0f, 1f)
        }
    }
    val dockVisible by remember { derivedStateOf { dockAlpha.value > 0.01f } }

    // MIUI's "icons zoom back in" when you return home from an app.
    val homeEnter = remember { Animatable(1f) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                scope.launch {
                    homeEnter.snapTo(0.88f)
                    homeEnter.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    // Every change goes through here as a transform of the stored layout, applied atomically.
    fun edit(transform: (List<HomeItem>) -> List<HomeItem>) = onHomeEdit { stored ->
        transform(HomeLayout.normalize(HomeLayout.prune(stored, installed), gridSpec))
    }

    fun addToHome(app: AppEntry) {
        if (app.packageName in onHome) {
            toast("${app.label} is already on Home")
            return
        }
        val page = HomeLayout.addApp(layout, app.packageName, gridSpec)
            .first { app.packageName in it.packages }.pos.page
        edit { HomeLayout.addApp(it, app.packageName, gridSpec) }
        toast(if (page == 0) "Added ${app.label} to Home" else "Added ${app.label} to Home, page ${page + 1}")
    }

    fun addToDock(app: AppEntry) {
        val current = dock.map { it.packageName }
        when {
            app.packageName in current -> toast("${app.label} is already in the dock")
            current.size >= MAX_DOCK_APPS -> toast("The dock holds $MAX_DOCK_APPS apps — remove one first")
            else -> onDockEdit { current + app.packageName }
        }
    }

    fun scrollToHomePage(page: Int) {
        scope.launch {
            // A brand-new page only exists once the edit that created it has been saved.
            withTimeoutOrNull(1_000) { snapshotFlow { pagerState.pageCount }.first { it > page + 2 } }
            pagerState.animateScrollToPage(page + 1)
        }
    }

    fun moveToPage(from: GridPos, page: Int) {
        val moved = HomeLayout.at(layout, from) ?: return
        val next = HomeLayout.moveToPage(layout, from, page, gridSpec) ?: return toast("Page ${page + 1} is full")
        edit { HomeLayout.moveToPage(it, from, page, gridSpec) ?: it }
        HomeLayout.normalize(next, gridSpec).firstOrNull { it.packages == moved.packages }?.let { scrollToHomePage(it.pos.page) }
    }

    fun openDrawer(withKeyboard: Boolean) {
        query = ""
        focusSearch = withKeyboard
        scope.launch {
            val drawer = pagerState.pageCount - 1
            if (drawer - pagerState.currentPage > 1) pagerState.scrollToPage(drawer - 1)
            pagerState.animateScrollToPage(drawer)
        }
    }

    fun closeDrawer() {
        focusSearch = false
        scope.launch { pagerState.animateScrollToPage(pagerState.pageCount - 2) }
    }

    val statusTopPx = WindowInsets.statusBars.getTop(density)
    val topZoneBottomPx = statusTopPx + with(density) { 88.dp.toPx() }
    val edgePx = with(density) { 28.dp.toPx() }
    fun zoneAt(drag: HomeDrag): DropZone? = when {
        drag.pointer.y < topZoneBottomPx ->
            if (!drag.isFolder && drag.pointer.x > rootSize.width / 2f) DropZone.UNINSTALL else DropZone.REMOVE
        drag.pointer.x < edgePx && drag.from.page > 0 -> DropZone.PREVIOUS_PAGE
        drag.pointer.x > rootSize.width - edgePx -> DropZone.NEXT_PAGE
        else -> null
    }

    fun handleDrop(from: GridPos, pointer: Offset, target: GridPos?) {
        val cell = cells[from] ?: return
        when (zoneAt(HomeDrag(from, cell is HomeCell.Folder, pointer))) {
            DropZone.REMOVE -> {
                edit { HomeLayout.removeAt(it, from) }
                if (cell is HomeCell.Folder) toast("Folder removed — its apps are still in the app drawer")
            }
            DropZone.UNINSTALL -> (cell as? HomeCell.App)?.let { uninstallApp(context, it.app.packageName) }
            DropZone.PREVIOUS_PAGE -> moveToPage(from, from.page - 1)
            DropZone.NEXT_PAGE -> moveToPage(from, from.page + 1)
            null -> if (target != null && target != from) {
                edit { current -> HomeLayout.drop(current, from, target) { folderNameFor(context, it) } }
            }
        }
    }

    // Pressing Home while ZenFold is already showing should drop back to the first home page.
    LaunchedEffect(homeSignal) {
        focusSearch = false
        homeMenuOpen = false
        widgetPickerOpen = false
        appPickerOpen = false
        openFolderAt = null
        pagerState.scrollToPage(1)
    }
    // A folder that stops being a folder (its last-but-one app moved out) closes itself.
    LaunchedEffect(openFolder == null) {
        if (openFolder == null) openFolderAt = null
    }
    BackHandler(enabled = drawerOpen) { closeDrawer() }
    BackHandler(enabled = homeMenuOpen) { homeMenuOpen = false }
    BackHandler(enabled = widgetPickerOpen) { widgetPickerOpen = false }
    BackHandler(enabled = appPickerOpen) { appPickerOpen = false }
    BackHandler(enabled = openFolderAt != null) { openFolderAt = null }

    val swipeThreshold = with(density) { 72.dp.toPx() }
    // Swipe up anywhere on Home opens all apps; swipe down pulls the notification shade.
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

    val homeAppActions: (AppEntry) -> List<MenuAction> = { app ->
        listOfNotNull(
            MenuAction("Remove from Home") { edit { HomeLayout.removeApp(it, app.packageName) } },
            MenuAction("Add to dock") { addToDock(app) }.takeIf { app !in dock }
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { rootSize = it }
    ) {
        Wallpaper(style, hazeState, Modifier.fillMaxSize())

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !overlayOpen && !dragging,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            Box(
                Modifier
                    .fillMaxSize()
                    // MIUI-like depth while swiping: the outgoing page shrinks and dims a little.
                    .graphicsLayer {
                        val offset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                            .absoluteValue.coerceIn(0f, 1f)
                        val scale = lerp(1f, 0.88f, offset)
                        scaleX = scale
                        scaleY = scale
                        alpha = lerp(1f, 0.4f, offset)
                    }
            ) {
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
                    homePages + 1 -> AppDrawer(
                        apps = visibleApps,
                        suggested = suggested,
                        style = style,
                        hazeState = hazeState,
                        badged = badged,
                        query = query,
                        onQueryChange = { query = it },
                        focusSearch = focusSearch,
                        appActions = { app ->
                            listOfNotNull(
                                MenuAction("Add to Home") { addToHome(app) },
                                MenuAction("Add to dock") { addToDock(app) }.takeIf { app !in dock },
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
                    else -> {
                        val homePage = page - 1
                        Column(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val enter = homeEnter.value
                                    scaleX = enter
                                    scaleY = enter
                                    alpha = homeContentAlpha * ((enter - 0.88f) / 0.12f).coerceIn(0.3f, 1f)
                                }
                                .nestedScroll(homeSwipes)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onLongPress = { homeMenuOpen = true },
                                        onDoubleTap = {
                                            if (!LockScreenService.lockScreen()) {
                                                toast("Turn on “Double-tap to lock” in ZenFold Settings")
                                            }
                                        }
                                    )
                                }
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
                                if (homePage == 0) {
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
                                    hostedWidgetIds.forEach { id ->
                                        key(id) { HostedWidget(appWidgetHost, id) }
                                    }
                                }
                                HomeGrid(
                                    page = homePage,
                                    cells = cells,
                                    spec = gridSpec,
                                    style = style,
                                    badged = badged,
                                    inDropZone = { zoneAt(it) != null },
                                    onDragChange = { dragState.value = it },
                                    onDrop = ::handleDrop,
                                    onLaunch = onLaunch,
                                    onOpenFolder = { openFolderAt = it },
                                    appActions = homeAppActions,
                                    onRemoveFolder = { pos ->
                                        edit { HomeLayout.removeAt(it, pos) }
                                        toast("Folder removed — its apps are still in the app drawer")
                                    }
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            Spacer(Modifier.height(DOCK_AREA_HEIGHT))
                        }
                    }
                }
            }
        }

        // MIUI-style bottom: page dots over a plain row of dock apps — no search bar.
        if (dockVisible) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .graphicsLayer {
                        val enter = homeEnter.value
                        scaleX = enter
                        scaleY = enter
                        alpha = dockAlpha.value * homeContentAlpha
                    }
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PageIndicator(homePages, (pagerState.currentPage - 1).coerceIn(0, homePages - 1), style)
                Spacer(Modifier.height(10.dp))
                Dock(
                    apps = dock,
                    style = style,
                    badged = badged,
                    onLaunch = onLaunch,
                    appActions = { app ->
                        listOf(MenuAction("Remove from dock") { onDockEdit { current -> current - app.packageName } })
                    }
                )
            }
        }

        DropTargetsHost(dragState, ::zoneAt, style)

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
                            edit { HomeLayout.moveOutOfFolder(it, folderPos, app.packageName, gridSpec) }
                        },
                        MenuAction("Remove from Home") { edit { HomeLayout.removeApp(it, app.packageName) } }
                    )
                },
                onLaunch = { app ->
                    openFolderAt = null
                    onLaunch(app)
                },
                onRename = { name -> edit { HomeLayout.renameFolder(it, folderPos, name) } },
                onDismiss = { openFolderAt = null }
            )
        }

        if (homeMenuOpen) {
            HomeMenuSheet(
                style = style,
                hazeState = hazeState,
                onAddApps = {
                    homeMenuOpen = false
                    appPickerOpen = true
                },
                onWidgets = {
                    homeMenuOpen = false
                    widgetPickerOpen = true
                },
                onSettings = {
                    homeMenuOpen = false
                    context.startActivity(Intent(context, SettingsActivity::class.java))
                },
                onDismiss = { homeMenuOpen = false }
            )
        }

        if (appPickerOpen) {
            AppPickerOverlay(
                apps = visibleApps,
                onHome = onHome,
                style = style,
                hazeState = hazeState,
                onToggle = { app, add ->
                    edit { if (add) HomeLayout.addApp(it, app.packageName, gridSpec) else HomeLayout.removeApp(it, app.packageName) }
                },
                onDismiss = { appPickerOpen = false }
            )
        }

        if (widgetPickerOpen) {
            WidgetPickerOverlay(
                style = style,
                hazeState = hazeState,
                enabled = enabledWidgets,
                onToggle = onWidgetToggle,
                hostedWidgetIds = hostedWidgetIds,
                onAddAndroidWidget = { provider ->
                    widgetPickerOpen = false
                    onAddAndroidWidget(provider)
                },
                onRemoveAndroidWidget = onRemoveAndroidWidget,
                onDismiss = { widgetPickerOpen = false }
            )
        }
    }
}

// Reads the per-frame drag position in its own scope, so dragging an icon doesn't
// recompose the whole home screen every frame.
@Composable
private fun DropTargetsHost(dragState: MutableState<HomeDrag?>, zoneAt: (HomeDrag) -> DropZone?, style: CustomStyle) {
    val drag = dragState.value
    DropTargets(drag, drag?.let(zoneAt), style)
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
// another app to make a folder, an app onto a folder to add it, to the top to remove or
// uninstall it, or to the screen's edge to send it to another page. Long-press WITHOUT
// moving (release near where you picked it up) opens its menu instead.
@Composable
private fun HomeGrid(
    page: Int,
    cells: Map<GridPos, HomeCell>,
    spec: GridSpec,
    style: CustomStyle,
    badged: Set<String>,
    inDropZone: (HomeDrag) -> Boolean,
    onDragChange: (HomeDrag?) -> Unit,
    onDrop: (from: GridPos, pointer: Offset, target: GridPos?) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onOpenFolder: (GridPos) -> Unit,
    appActions: (AppEntry) -> List<MenuAction>,
    onRemoveFolder: (GridPos) -> Unit
) {
    val density = LocalDensity.current
    val pageCells = remember(cells, page) { cells.filterKeys { it.page == page } }
    var gridOrigin by remember { mutableStateOf(Offset.Zero) }
    var dragFrom by remember { mutableStateOf<GridPos?>(null) }
    var dragStart by remember { mutableStateOf(Offset.Zero) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var hoverTarget by remember { mutableStateOf<GridPos?>(null) }
    var menuAt by remember { mutableStateOf<GridPos?>(null) }
    val haptics = LocalHapticFeedback.current
    val tapSlopPx = with(density) { 12.dp.toPx() }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cellPx = with(density) { maxWidth.toPx() } / spec.columns
        val rowPx = cellPx * 1.3f
        val cellDp = with(density) { cellPx.toDp() }
        val rowDp = with(density) { rowPx.toDp() }

        Box(
            Modifier
                .fillMaxWidth()
                .height(rowDp * spec.rows)
                .onGloballyPositioned { gridOrigin = it.positionInRoot() }
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
                            if (target in pageCells) {
                                Modifier.border(2.dp, style.accent.copy(alpha = 0.8f), shape)
                            } else {
                                Modifier.background(style.accent.copy(alpha = 0.22f), shape)
                            }
                        )
                )
            }

            pageCells.forEach { (pos, cell) ->
                key(pos, cell) {
                    val isDragging = pos == dragFrom
                    val baseX = pos.col * cellPx
                    val baseY = pos.row * rowPx

                    fun currentDrag() = HomeDrag(
                        from = pos,
                        isFolder = cell is HomeCell.Folder,
                        pointer = gridOrigin + Offset(baseX, baseY) + dragStart + dragOffset
                    )

                    fun resetDrag() {
                        dragFrom = null
                        hoverTarget = null
                        dragOffset = Offset.Zero
                        onDragChange(null)
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
                            .graphicsLayer {
                                val lift = if (isDragging) 1.12f else 1f
                                scaleX = lift
                                scaleY = lift
                            }
                            .pointerInput(pos, cell) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { start ->
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        dragFrom = pos
                                        dragStart = start
                                        dragOffset = Offset.Zero
                                        hoverTarget = pos
                                        onDragChange(currentDrag())
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount
                                        val drag = currentDrag()
                                        onDragChange(drag)
                                        val col = ((baseX + dragOffset.x + cellPx / 2) / cellPx).toInt().coerceIn(0, spec.columns - 1)
                                        val row = ((baseY + dragOffset.y + rowPx / 2) / rowPx).toInt().coerceIn(0, spec.rows - 1)
                                        hoverTarget = if (inDropZone(drag)) null else GridPos(row, col, page)
                                    },
                                    onDragEnd = {
                                        val moved = hypot(dragOffset.x, dragOffset.y) > tapSlopPx
                                        if (moved) onDrop(pos, currentDrag().pointer, hoverTarget) else menuAt = pos
                                        resetDrag()
                                    },
                                    onDragCancel = { resetDrag() }
                                )
                            }
                            // Claims the touch for this cell so a long-press on an app's label
                            // or padding opens the app's menu, not the empty-space home menu.
                            .pointerInput(Unit) {
                                awaitEachGesture { awaitFirstDown(requireUnconsumed = false).consume() }
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
private fun Dock(
    apps: List<AppEntry>,
    style: CustomStyle,
    badged: Set<String>,
    onLaunch: (AppEntry) -> Unit,
    appActions: (AppEntry) -> List<MenuAction>
) {
    var menuFor by remember { mutableStateOf<String?>(null) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(76.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (apps.isEmpty()) {
            Text("Long-press any app → Add to dock", fontSize = 12.sp, color = style.onSurfaceVariant)
        }
        apps.forEach { app ->
            Box {
                AppIcon(
                    app,
                    style = style,
                    showLabel = false,
                    badged = app.packageName in badged,
                    onLongClick = { menuFor = app.packageName },
                    onClick = { onLaunch(app) }
                )
                AppActionsMenu(
                    app,
                    expanded = menuFor == app.packageName,
                    onDismiss = { menuFor = null },
                    actions = if (menuFor == app.packageName) appActions(app) else emptyList()
                )
            }
        }
    }
}
