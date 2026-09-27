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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.zenfold.launcher.style.CustomStyle
import com.zenfold.launcher.style.StatusBarStyle
import com.zenfold.launcher.style.StylePresets
import com.zenfold.launcher.style.StylePreferences
import com.zenfold.launcher.ui.HomeScreen
import com.zenfold.launcher.ui.SettingsScreen
import com.zenfold.launcher.ui.theme.ZenFoldTheme
import com.zenfold.launcher.widgets.WidgetType
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val stylePreferences by lazy { StylePreferences(applicationContext) }

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
            var showSettings by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            // A launcher has nothing "behind" it: Back on the home page does nothing.
            // Registered first, so the Settings and drawer handlers below take priority.
            BackHandler(enabled = true) {}
            BackHandler(enabled = showSettings) { showSettings = false }

            LaunchedEffect(homeSignal) { showSettings = false }

            LaunchedEffect(style.statusBarStyle) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = style.statusBarStyle == StatusBarStyle.DARK_ICONS
            }

            ZenFoldTheme(style = style) {
                if (showSettings) {
                    SettingsScreen(
                        style = style,
                        enabledWidgets = enabledWidgets,
                        onApplyPreset = { preset: CustomStyle -> scope.launch { stylePreferences.applyPreset(preset) } },
                        onChange = { next: CustomStyle -> scope.launch { stylePreferences.update { next } } },
                        onWidgetToggle = { widget, enabled -> scope.launch { stylePreferences.setWidgetEnabled(widget, enabled) } },
                        onDone = { showSettings = false }
                    )
                } else {
                    HomeScreen(
                        apps = apps,
                        style = style,
                        enabledWidgets = enabledWidgets,
                        recentPackages = recentPackages,
                        homeLayout = homeLayout,
                        noteText = noteText,
                        homeSignal = homeSignal,
                        onNoteChange = { text -> scope.launch { stylePreferences.setNoteText(text) } },
                        onMoveApp = { pkg, pos -> scope.launch { stylePreferences.setHomePosition(pkg, pos) } },
                        onLaunch = { app ->
                            packageManager.getLaunchIntentForPackage(app.packageName)?.let {
                                startActivity(it)
                                scope.launch { stylePreferences.recordLaunch(app.packageName) }
                            }
                        },
                        onOpenSettings = { showSettings = true }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homeSignal++
    }
}
