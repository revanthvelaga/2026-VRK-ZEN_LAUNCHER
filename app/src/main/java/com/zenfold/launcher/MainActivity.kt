package com.zenfold.launcher

import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.zenfold.launcher.feeds.FeedApiKeys
import com.zenfold.launcher.feeds.FeedPreferences
import com.zenfold.launcher.home.DEFAULT_GRID
import com.zenfold.launcher.home.HomeLayout
import com.zenfold.launcher.ui.LaunchBounds
import com.zenfold.launcher.style.StatusBarStyle
import com.zenfold.launcher.style.StylePresets
import com.zenfold.launcher.style.StylePreferences
import com.zenfold.launcher.tasks.TaskPreferences
import com.zenfold.launcher.ui.HomeScreen
import com.zenfold.launcher.ui.rememberInstalledApps
import com.zenfold.launcher.ui.theme.ZenFoldTheme
import com.zenfold.launcher.widgets.WidgetType
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val stylePreferences by lazy { StylePreferences(applicationContext) }
    private val taskPreferences by lazy { TaskPreferences(applicationContext) }
    private val feedPreferences by lazy { FeedPreferences(applicationContext) }

    // Bumped each time the Home button is pressed while ZenFold is already in front.
    private var homeSignal by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Fully transparent bars so the wallpaper runs edge to edge (the default
        // gives 3-button navigation a light scrim).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        setContent {
            val apps = rememberInstalledApps()
            val style by stylePreferences.customStyle.collectAsState(initial = StylePresets.default)
            val enabledWidgets by stylePreferences.enabledWidgets.collectAsState(initial = setOf(WidgetType.GLANCE))
            val noteText by stylePreferences.noteText.collectAsState(initial = "")
            val recentPackages by stylePreferences.recentPackages.collectAsState(initial = emptyList())
            val homeItems by stylePreferences.homeItems.collectAsState(initial = emptyList())
            val gridSpec by stylePreferences.gridSpec.collectAsState(initial = DEFAULT_GRID)
            val dockPackages by stylePreferences.dockPackages.collectAsState(initial = emptyList())
            val hiddenApps by stylePreferences.hiddenApps.collectAsState(initial = emptySet())
            val tasks by taskPreferences.tasks.collectAsState(initial = emptyList())
            val feedKeys by feedPreferences.apiKeys.collectAsState(initial = FeedApiKeys())
            val scope = rememberCoroutineScope()

            // One-time: the default Home and dock on a fresh install, or the older layout carried over.
            LaunchedEffect(Unit) {
                val defaults = AppRepository.homeApps(this@MainActivity, apps)
                stylePreferences.seedHomeIfNeeded(
                    homeSeed = { oldPositions, oldRemoved ->
                        val grid = defaults.grid.map { it.packageName }.filterNot { it in oldRemoved }
                        HomeLayout.seed(grid, oldPositions, DEFAULT_GRID)
                    },
                    dockSeed = { defaults.dock.map { it.packageName } }
                )
            }

            // A launcher has nothing "behind" it: Back on the home page does nothing
            // (HomeScreen's own BackHandlers for the drawer/widget picker take priority).
            BackHandler(enabled = true) {}

            LaunchedEffect(style.statusBarStyle) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = style.statusBarStyle == StatusBarStyle.DARK_ICONS
            }

            ZenFoldTheme(style = style) {
                HomeScreen(
                    apps = apps,
                    style = style,
                    enabledWidgets = enabledWidgets,
                    recentPackages = recentPackages,
                    homeItems = homeItems,
                    gridSpec = gridSpec,
                    dockPackages = dockPackages,
                    hiddenApps = hiddenApps,
                    noteText = noteText,
                    tasks = tasks,
                    feedKeys = feedKeys,
                    homeSignal = homeSignal,
                    onNoteChange = { text -> scope.launch { stylePreferences.setNoteText(text) } },
                    onHomeEdit = { transform -> scope.launch { stylePreferences.editHomeItems(transform) } },
                    onDockEdit = { transform -> scope.launch { stylePreferences.editDock(transform) } },
                    onHideApp = { pkg -> scope.launch { stylePreferences.setAppHidden(pkg, true) } },
                    onWidgetToggle = { widget, enabled -> scope.launch { stylePreferences.setWidgetEnabled(widget, enabled) } },
                    onAddTask = { text -> scope.launch { taskPreferences.add(text) } },
                    onToggleTask = { id, done -> scope.launch { taskPreferences.setDone(id, done) } },
                    onRemoveTask = { id -> scope.launch { taskPreferences.remove(id) } },
                    onLaunch = { app ->
                        packageManager.getLaunchIntentForPackage(app.packageName)?.let { intent ->
                            startActivity(intent, zoomFromIcon(intent))
                            scope.launch { stylePreferences.recordLaunch(app.packageName) }
                        }
                    }
                )
            }
        }
    }

    // The app opens growing out of the icon that was tapped (MIUI/Pixel-style) instead of
    // the default window slide — and knows where it came from via sourceBounds.
    private fun zoomFromIcon(intent: Intent): Bundle? {
        val bounds = LaunchBounds.take()?.takeIf { !it.isEmpty } ?: return null
        intent.sourceBounds = bounds
        return ActivityOptions
            .makeScaleUpAnimation(window.decorView, bounds.left, bounds.top, bounds.width(), bounds.height())
            .toBundle()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homeSignal++
    }
}
