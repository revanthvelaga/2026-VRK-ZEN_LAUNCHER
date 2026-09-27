package com.zenfold.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val apps = remember { AppRepository.installedApps(this) }
            val style by stylePreferences.customStyle.collectAsState(initial = StylePresets.default)
            val enabledWidgets by stylePreferences.enabledWidgets.collectAsState(initial = setOf(WidgetType.GLANCE))
            val noteText by stylePreferences.noteText.collectAsState(initial = "")
            val recentPackages by stylePreferences.recentPackages.collectAsState(initial = emptyList())
            var showSettings by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            BackHandler(enabled = showSettings) { showSettings = false }

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
                        noteText = noteText,
                        onNoteChange = { text -> scope.launch { stylePreferences.setNoteText(text) } },
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

    // A launcher's Home screen shouldn't disappear on system Back —
    // there's nothing "behind" it to go back to.
    override fun onBackPressed() {
        // Intentionally does nothing.
    }
}
