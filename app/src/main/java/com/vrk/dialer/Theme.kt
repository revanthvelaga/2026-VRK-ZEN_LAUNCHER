package com.vrk.dialer

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
fun DialerTheme(dark: Boolean? = null, theme: PhoneTheme? = null, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val appearance = rememberAppearance(context)
    val selected = theme ?: appearance.theme
    val useDark = dark ?: when (appearance.mode) {
        DisplayMode.SYSTEM -> isSystemInDarkTheme()
        DisplayMode.LIGHT -> false
        DisplayMode.DARK -> true
    }
    val scheme = themeColors(selected, useDark)
    val view = LocalView.current
    SideEffect {
        if (!view.isInEditMode) (context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !useDark
                isAppearanceLightNavigationBars = !useDark
            }
            window.statusBarColor = scheme.background.toArgb()
            window.navigationBarColor = scheme.background.toArgb()
        }
    }
    CompositionLocalProvider(LocalAppearance provides appearance.copy(theme = selected)) {
    MaterialTheme(
        colorScheme = scheme, typography = DialerTypography,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp)),
        content = content
    )
    }
}

fun themeColors(theme: PhoneTheme, dark: Boolean): ColorScheme {
    val base = if (dark) DarkPalette else LightPalette
    return when (theme) {
        PhoneTheme.SAPPHIRE -> base
        PhoneTheme.FLOW -> if (dark) base.copy(
            primary = Color(0xFFD1BDFF), primaryContainer = Color(0xFF443267),
            onPrimary = Color(0xFF29164E), onPrimaryContainer = Color(0xFFEEDFFF),
            background = Color(0xFF14101D), surface = Color(0xFF201A2B),
            surfaceVariant = Color(0xFF2B2438), outlineVariant = Color(0xFF43394F)
        ) else base.copy(
            primary = Color(0xFF6745B5), primaryContainer = Color(0xFFEEE4FF),
            onPrimaryContainer = Color(0xFF352060), background = Color(0xFFFAF7FF),
            surfaceVariant = Color(0xFFF0EAF8), outlineVariant = Color(0xFFE4DAEF)
        )
        PhoneTheme.LUMINOUS -> if (dark) base.copy(
            primary = Color(0xFF78DDB5), onPrimary = Color(0xFF003825),
            primaryContainer = Color(0xFF194D3C), onPrimaryContainer = Color(0xFFBAF4D9),
            secondaryContainer = Color(0xFF29483B), onSecondaryContainer = Color(0xFFD3F2DF),
            background = Color(0xFF101714), surface = Color(0xFF18221C),
            surfaceVariant = Color(0xFF26372D), onSurface = Color(0xFFE9F3EC),
            onBackground = Color(0xFFE9F3EC), onSurfaceVariant = Color(0xFFB5C7BC),
            outline = Color(0xFF8B9E92), outlineVariant = Color(0xFF364C40)
        ) else base.copy(
            primary = Color(0xFF006C49), onPrimary = Color.White,
            primaryContainer = Color(0xFFD7F3E5), onPrimaryContainer = Color(0xFF00452E),
            secondaryContainer = Color(0xFFE0EEE6), onSecondaryContainer = Color(0xFF294B3C),
            background = Color(0xFFFAFBF9), surface = Color.White,
            surfaceVariant = Color(0xFFEDF2EF), onSurface = Color(0xFF17251D),
            onBackground = Color(0xFF17251D), onSurfaceVariant = Color(0xFF4D6456),
            outline = Color(0xFF708679), outlineVariant = Color(0xFFD9E5DC)
        )
    }
}
