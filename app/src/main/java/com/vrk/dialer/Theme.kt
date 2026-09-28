package com.vrk.dialer

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val SeedGreen = Color(0xFF2DBE60)

// A hand-tuned scheme close to Google's own Phone app, for the many phones without
// Material You (Android <12, or a OEM skin that doesn't expose wallpaper colour extraction).
private val LightFallback = lightColorScheme(
    primary = SeedGreen, onPrimary = Color.White,
    primaryContainer = Color(0xFFB6F2C9), onPrimaryContainer = Color(0xFF002110),
    secondary = Color(0xFF4F6354), secondaryContainer = Color(0xFFD2E8D3),
    background = Color(0xFFFBFDF8), surface = Color(0xFFFBFDF8),
    surfaceVariant = Color(0xFFDDE5DB), onSurfaceVariant = Color(0xFF414942),
    error = Color(0xFFBA1A1A)
)

private val DarkFallback = darkColorScheme(
    primary = Color(0xFF99D9A9), onPrimary = Color(0xFF00391C),
    primaryContainer = Color(0xFF00522A), onPrimaryContainer = Color(0xFFB6F2C9),
    secondary = Color(0xFFB6CCB8), secondaryContainer = Color(0xFF374B3A),
    background = Color(0xFF191C19), surface = Color(0xFF191C19),
    surfaceVariant = Color(0xFF414942), onSurfaceVariant = Color(0xFFC1C9BF),
    error = Color(0xFFFFB4AB)
)

/** Google's own current look: the phone's Material You palette where it's available. */
@Composable
fun DialerTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val dynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme = when {
        dynamic && dark -> dynamicDarkColorScheme(context)
        dynamic -> dynamicLightColorScheme(context)
        dark -> DarkFallback
        else -> LightFallback
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        val activity = context as? Activity
        if (activity != null) {
            val window = activity.window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
            window.statusBarColor = scheme.background.toArgb()
            window.navigationBarColor = scheme.background.toArgb()
        }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
