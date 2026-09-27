package com.zenfold.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import com.zenfold.launcher.feeds.FeedApiKeys
import com.zenfold.launcher.feeds.FeedPreferences
import com.zenfold.launcher.style.CustomStyle
import com.zenfold.launcher.style.StylePresets
import com.zenfold.launcher.style.StylePreferences
import com.zenfold.launcher.ui.SettingsScreen
import com.zenfold.launcher.ui.theme.ZenFoldTheme
import com.zenfold.launcher.widgets.WidgetType
import kotlinx.coroutines.launch

// A plain app-drawer entry (see the manifest: LAUNCHER but not HOME) so ZenFold's
// settings are reached by opening this app, like any other app's settings — not
// through a home-screen long-press.
class SettingsActivity : ComponentActivity() {

    private val stylePreferences by lazy { StylePreferences(applicationContext) }
    private val feedPreferences by lazy { FeedPreferences(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val style by stylePreferences.customStyle.collectAsState(initial = StylePresets.default)
            val enabledWidgets by stylePreferences.enabledWidgets.collectAsState(initial = setOf(WidgetType.GLANCE))
            val feedKeys by feedPreferences.apiKeys.collectAsState(initial = FeedApiKeys())
            val scope = rememberCoroutineScope()

            ZenFoldTheme(style = style) {
                SettingsScreen(
                    style = style,
                    enabledWidgets = enabledWidgets,
                    feedKeys = feedKeys,
                    onApplyPreset = { preset: CustomStyle -> scope.launch { stylePreferences.applyPreset(preset) } },
                    onChange = { next: CustomStyle -> scope.launch { stylePreferences.update { next } } },
                    onWidgetToggle = { widget, enabled -> scope.launch { stylePreferences.setWidgetEnabled(widget, enabled) } },
                    onFeedKeysChange = { next: FeedApiKeys ->
                        scope.launch {
                            feedPreferences.setGoldApiKey(next.goldApiKey)
                            feedPreferences.setMarketApiKey(next.marketApiKey)
                            feedPreferences.setCricketApiKey(next.cricketApiKey)
                        }
                    },
                    onDone = { finish() }
                )
            }
        }
    }
}
