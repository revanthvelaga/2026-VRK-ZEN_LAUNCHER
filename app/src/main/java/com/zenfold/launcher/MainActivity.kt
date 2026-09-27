package com.zenfold.launcher

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.view.WindowCompat
import com.zenfold.launcher.feeds.FeedApiKeys
import com.zenfold.launcher.feeds.FeedPreferences
import com.zenfold.launcher.style.StatusBarStyle
import com.zenfold.launcher.style.StylePresets
import com.zenfold.launcher.style.StylePreferences
import com.zenfold.launcher.tasks.TaskPreferences
import com.zenfold.launcher.ui.HomeScreen
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
            val apps = remember { AppRepository.installedApps(this) }
            val style by stylePreferences.customStyle.collectAsState(initial = StylePresets.default)
            val enabledWidgets by stylePreferences.enabledWidgets.collectAsState(initial = setOf(WidgetType.GLANCE))
            val noteText by stylePreferences.noteText.collectAsState(initial = "")
            val recentPackages by stylePreferences.recentPackages.collectAsState(initial = emptyList())
            val homeLayout by stylePreferences.homeLayout.collectAsState(initial = emptyMap())
            val hiddenHomeApps by stylePreferences.hiddenHomeApps.collectAsState(initial = emptySet())
            val tasks by taskPreferences.tasks.collectAsState(initial = emptyList())
            val feedKeys by feedPreferences.apiKeys.collectAsState(initial = FeedApiKeys())
            val scope = rememberCoroutineScope()

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
                    homeLayout = homeLayout,
                    hiddenHomeApps = hiddenHomeApps,
                    noteText = noteText,
                    tasks = tasks,
                    feedKeys = feedKeys,
                    homeSignal = homeSignal,
                    onNoteChange = { text -> scope.launch { stylePreferences.setNoteText(text) } },
                    onMoveApp = { pkg, pos -> scope.launch { stylePreferences.setHomePosition(pkg, pos) } },
                    onHideFromHome = { pkg -> scope.launch { stylePreferences.setHiddenFromHome(pkg, true) } },
                    onWidgetToggle = { widget, enabled -> scope.launch { stylePreferences.setWidgetEnabled(widget, enabled) } },
                    onAddTask = { text -> scope.launch { taskPreferences.add(text) } },
                    onToggleTask = { id, done -> scope.launch { taskPreferences.setDone(id, done) } },
                    onRemoveTask = { id -> scope.launch { taskPreferences.remove(id) } },
                    onLaunch = { app ->
                        packageManager.getLaunchIntentForPackage(app.packageName)?.let {
                            startActivity(it)
                            scope.launch { stylePreferences.recordLaunch(app.packageName) }
                        }
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homeSignal++
    }
}
