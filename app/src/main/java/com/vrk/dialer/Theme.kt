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
    primary = Color(0xFF4059E8), onPrimary = Color.White,
    primaryContainer = Color(0xFFE5E9FF), onPrimaryContainer = Color(0xFF2436A0),
    secondary = Color(0xFF67538D), onSecondary = Color.White,
    secondaryContainer = Color(0xFFEEE8FC), onSecondaryContainer = Color(0xFF443563),
    tertiary = Color(0xFF596A91), tertiaryContainer = Color(0xFFE2E8FA),
    background = Color(0xFFF8F9FF), onBackground = Color(0xFF151B3D),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF151B3D),
    surfaceVariant = Color(0xFFF0F3FF), onSurfaceVariant = Color(0xFF657080),
    outline = Color(0xFF78867D), outlineVariant = Color(0xFFE1E6EE),
    error = Color(0xFFB83742), onError = Color.White
)
private val DarkPalette = darkColorScheme(
    primary = Color(0xFFBCC6FF), onPrimary = Color(0xFF162776),
    primaryContainer = Color(0xFF2D397E), onPrimaryContainer = Color(0xFFE1E6FF),
    secondary = Color(0xFFD0C2EE), secondaryContainer = Color(0xFF393047),
    onSecondaryContainer = Color(0xFFEEE5FF),
    tertiary = Color(0xFFBDC9ED), tertiaryContainer = Color(0xFF34425F),
    background = Color(0xFF0B0E13), onBackground = Color(0xFFF1F4FA),
    surface = Color(0xFF141920), onSurface = Color(0xFFF1F4FA),
    surfaceVariant = Color(0xFF202731), onSurfaceVariant = Color(0xFFA6B2C2),
    outline = Color(0xFF829086), outlineVariant = Color(0xFF303B49),
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
