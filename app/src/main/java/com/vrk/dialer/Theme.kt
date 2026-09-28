package com.vrk.dialer

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// Deliberately stable brand palette: wallpaper colours must not change call affordances.
private val LightPalette = lightColorScheme(
    primary = Color(0xFF176B55), onPrimary = Color.White,
    primaryContainer = Color(0xFFD5F3E6), onPrimaryContainer = Color(0xFF0B4636),
    secondary = Color(0xFF52665E), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5EEE8), onSecondaryContainer = Color(0xFF263D33),
    tertiary = Color(0xFF596A91), tertiaryContainer = Color(0xFFE2E8FA),
    background = Color(0xFFF5F6F2), onBackground = Color(0xFF18221E),
    surface = Color(0xFFFCFDF9), onSurface = Color(0xFF18221E),
    surfaceVariant = Color(0xFFEAF0E9), onSurfaceVariant = Color(0xFF59675F),
    outline = Color(0xFF78867D), outlineVariant = Color(0xFFDCE3DB),
    error = Color(0xFFB83742), onError = Color.White
)
private val DarkPalette = darkColorScheme(
    primary = Color(0xFF96DABD), onPrimary = Color(0xFF003828),
    primaryContainer = Color(0xFF234F40), onPrimaryContainer = Color(0xFFC5F1DE),
    secondary = Color(0xFFB6CCC0), secondaryContainer = Color(0xFF293C33),
    onSecondaryContainer = Color(0xFFDCEAE0),
    tertiary = Color(0xFFBDC9ED), tertiaryContainer = Color(0xFF34425F),
    background = Color(0xFF101713), onBackground = Color(0xFFE3EBE3),
    surface = Color(0xFF19221C), onSurface = Color(0xFFE3EBE3),
    surfaceVariant = Color(0xFF263129), onSurfaceVariant = Color(0xFFB1BFB3),
    outline = Color(0xFF829086), outlineVariant = Color(0xFF344239),
    error = Color(0xFFFFB0B7), onError = Color(0xFF65001B)
)
private val DialerTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 36.sp, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, letterSpacing = (-0.6).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.3.sp)
)

@Composable
fun DialerTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme = if (dark) DarkPalette else LightPalette
    val context = LocalContext.current
    val view = LocalView.current
    SideEffect {
        if (!view.isInEditMode) (context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
            window.statusBarColor = scheme.background.toArgb()
            window.navigationBarColor = scheme.background.toArgb()
        }
    }
    MaterialTheme(
        colorScheme = scheme, typography = DialerTypography,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp)),
        content = content
    )
}
