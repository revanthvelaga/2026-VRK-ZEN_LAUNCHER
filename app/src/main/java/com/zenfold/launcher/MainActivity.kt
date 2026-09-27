package com.zenfold.launcher

import android.app.ActivityOptions
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.zenfold.launcher.home.DEFAULT_GRID
import com.zenfold.launcher.home.HomeLayout
import com.zenfold.launcher.style.StatusBarStyle
import com.zenfold.launcher.style.StylePresets
import com.zenfold.launcher.style.StylePreferences
import com.zenfold.launcher.tasks.TaskPreferences
import com.zenfold.launcher.ui.HomeScreen
import com.zenfold.launcher.ui.LaunchBounds
import com.zenfold.launcher.ui.rememberInstalledApps
import com.zenfold.launcher.ui.theme.ZenFoldTheme
import com.zenfold.launcher.widgets.APPWIDGET_HOST_ID
import com.zenfold.launcher.widgets.WidgetType
import kotlinx.coroutines.launch

private const val REQUEST_CONFIGURE_WIDGET = 0x5A47

class MainActivity : ComponentActivity() {

    private val stylePreferences by lazy { StylePreferences(applicationContext) }
    private val taskPreferences by lazy { TaskPreferences(applicationContext) }
    private val appWidgetHost by lazy { AppWidgetHost(applicationContext, APPWIDGET_HOST_ID) }
    private val appWidgetManager by lazy { AppWidgetManager.getInstance(applicationContext) }

    // Bumped each time the Home button is pressed while ZenFold is already in front.
    private var homeSignal by mutableIntStateOf(0)

    // A widget id mid-way through Android's bind/configure steps (see addAndroidWidget).
    private var pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private val bindWidget = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) configureOrSaveWidget(pendingWidgetId) else discardPendingWidget()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Fully transparent bars so the wallpaper runs edge to edge (the default
        // gives 3-button navigation a light scrim).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        setContent {
            val iconPack by stylePreferences.iconPack.collectAsState(initial = null)
            val apps = rememberInstalledApps(iconPack)
            val style by stylePreferences.customStyle.collectAsState(initial = StylePresets.default)
            val enabledWidgets by stylePreferences.enabledWidgets.collectAsState(initial = setOf(WidgetType.GLANCE))
            val noteText by stylePreferences.noteText.collectAsState(initial = "")
            val recentPackages by stylePreferences.recentPackages.collectAsState(initial = emptyList())
            val homeItems by stylePreferences.homeItems.collectAsState(initial = emptyList())
            val gridSpec by stylePreferences.gridSpec.collectAsState(initial = DEFAULT_GRID)
            val dockPackages by stylePreferences.dockPackages.collectAsState(initial = emptyList())
            val hiddenApps by stylePreferences.hiddenApps.collectAsState(initial = emptySet())
            val hostedWidgets by stylePreferences.hostedWidgets.collectAsState(initial = emptyList())
            val tasks by taskPreferences.tasks.collectAsState(initial = emptyList())
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
                    appWidgetHost = appWidgetHost,
                    hostedWidgetIds = hostedWidgets,
                    noteText = noteText,
                    tasks = tasks,
                    homeSignal = homeSignal,
                    onNoteChange = { text -> scope.launch { stylePreferences.setNoteText(text) } },
                    onHomeEdit = { transform -> scope.launch { stylePreferences.editHomeItems(transform) } },
                    onDockEdit = { transform -> scope.launch { stylePreferences.editDock(transform) } },
                    onHideApp = { pkg -> scope.launch { stylePreferences.setAppHidden(pkg, true) } },
                    onWidgetToggle = { widget, enabled -> scope.launch { stylePreferences.setWidgetEnabled(widget, enabled) } },
                    onAddAndroidWidget = ::addAndroidWidget,
                    onRemoveAndroidWidget = ::removeAndroidWidget,
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

    // Hosted widgets only receive updates while the host is listening.
    override fun onStart() {
        super.onStart()
        try {
            appWidgetHost.startListening()
        } catch (e: RuntimeException) {
            // A widget provider crashing mid-bind must not take the launcher down.
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            appWidgetHost.stopListening()
        } catch (e: RuntimeException) {
            // Same as above.
        }
    }

    /**
     * Android's add-a-widget dance: allocate an id, get permission to bind it (a one-time
     * system dialog per provider unless the user ticked "always allow"), then run the
     * widget's own setup screen if it has one — only then does it go on Home.
     */
    private fun addAndroidWidget(provider: AppWidgetProviderInfo) {
        val id = appWidgetHost.allocateAppWidgetId()
        pendingWidgetId = id
        if (appWidgetManager.bindAppWidgetIdIfAllowed(id, provider.provider)) {
            configureOrSaveWidget(id)
        } else {
            bindWidget.launch(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
            )
        }
    }

    private fun configureOrSaveWidget(id: Int) {
        val info = appWidgetManager.getAppWidgetInfo(id) ?: return discardPendingWidget()
        if (info.configure == null) return saveWidget(id)
        try {
            appWidgetHost.startAppWidgetConfigureActivityForResult(this, id, 0, REQUEST_CONFIGURE_WIDGET, null)
        } catch (e: RuntimeException) {
            // No usable setup screen (ActivityNotFound/Security): place it with defaults.
            saveWidget(id)
        }
    }

    @Deprecated("Widget setup screens can only report back through onActivityResult.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CONFIGURE_WIDGET) return
        if (resultCode == RESULT_OK) saveWidget(pendingWidgetId) else discardPendingWidget()
    }

    private fun saveWidget(id: Int) {
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        lifecycleScope.launch { stylePreferences.addHostedWidget(id) }
    }

    private fun discardPendingWidget() {
        if (pendingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) appWidgetHost.deleteAppWidgetId(pendingWidgetId)
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    }

    private fun removeAndroidWidget(id: Int) {
        appWidgetHost.deleteAppWidgetId(id)
        lifecycleScope.launch { stylePreferences.removeHostedWidget(id) }
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
