package com.zenfold.launcher.style

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight

/** Named quick-apply presets. Picking one fills in every field of [CustomStyle] at once. */
enum class LauncherStyle(val displayName: String, val tagline: String) {
    ZEN("Zen", "Crystal glass over a violet-cyan glow"),
    PIXEL("Pixel", "Clean Material You, circular icons"),
    SAMSUNG("Samsung", "Bold clock, soft squircle icons"),
    ONEPLUS("OxygenOS", "Minimal glass, hidden labels"),
    MIUI("Mi", "Vivid color, rounded-square icons")
}

enum class IconShapeKind(val shape: Shape, val label: String) {
    SQUIRCLE(RoundedCornerShape(32), "Squircle"),
    CIRCLE(CircleShape, "Circle"),
    ROUNDED_SQUARE(RoundedCornerShape(20), "Rounded square"),
    SQUARE(RoundedCornerShape(6), "Square")
}

enum class IconStyle(val label: String) {
    CRYSTAL("Crystal"),
    ORIGINAL("Original")
}

enum class IconSize(val sizeDp: Int, val label: String) {
    SMALL(48, "Small"),
    MEDIUM(56, "Medium"),
    LARGE(64, "Large")
}

enum class FontScale(val scale: Float, val label: String) {
    SMALL(0.88f, "Small"),
    MEDIUM(1f, "Medium"),
    LARGE(1.16f, "Large")
}

enum class StatusBarStyle(val label: String) {
    LIGHT_ICONS("Light icons"),
    DARK_ICONS("Dark icons")
}

/** Every independently customizable piece of the launcher's look. */
data class CustomStyle(
    val background: Color,
    val surface: Color,
    val accent: Color,
    val secondary: Color,
    val onBackground: Color,
    val onSurfaceVariant: Color,
    // Wallpaper glows (alpha baked in): top-left, top-right, bottom-right.
    val glowTopStart: Color,
    val glowTopEnd: Color,
    val glowBottomEnd: Color,
    val iconShapeKind: IconShapeKind,
    val iconStyle: IconStyle,
    val iconSize: IconSize,
    val fontScale: FontScale,
    val showHomeLabels: Boolean,
    val clockCentered: Boolean,
    val clockWeight: FontWeight,
    val statusBarStyle: StatusBarStyle,
    val showAppPages: Boolean = true,
    val drawerColumns: Int = 4,
    val searchOnSwipe: Boolean = false,
    val swipeDownSearch: Boolean = false,
    val showSearchPill: Boolean = true
)

/** A short list of accent swatches offered in Settings, independent of any preset. */
object AccentSwatches {
    val all = listOf(
        Color(0xFF8A9A80), // moss
        Color(0xFF5EC8FF), // ice blue
        Color(0xFF4FA3FF), // sapphire
        Color(0xFFFF6B35), // ember
        Color(0xFFA78BFA), // violet
        Color(0xFF22E0A0)  // emerald
    )
}

// Colors match the per-preset values in the HTML design preview.
object StylePresets {
    val Zen = CustomStyle(
        background = Color(0xFF0A0E1A),
        surface = Color(0xFF1A1230),
        accent = Color(0xFF8A9A80),
        secondary = Color(0xFF5EC8FF),
        onBackground = Color(0xFFF4F2FF),
        onSurfaceVariant = Color(0xFFA6A4B8),
        glowTopStart = Color(0x8C8B5CF6),
        glowTopEnd = Color(0x5222D3EE),
        glowBottomEnd = Color(0x33F59E0B),
        iconShapeKind = IconShapeKind.SQUIRCLE,
        iconStyle = IconStyle.CRYSTAL,
        iconSize = IconSize.MEDIUM,
        fontScale = FontScale.MEDIUM,
        showHomeLabels = true,
        clockCentered = true,
        clockWeight = FontWeight.Light,
        statusBarStyle = StatusBarStyle.LIGHT_ICONS
    )

    val Pixel = CustomStyle(
        background = Color(0xFF1A1C1E),
        surface = Color(0xFF232629),
        accent = Color(0xFFA8C7FA),
        secondary = Color(0xFFFDE293),
        onBackground = Color(0xFFE3E2E6),
        onSurfaceVariant = Color(0xFFC4C6D0),
        glowTopStart = Color(0x4DA8C7FA),
        glowTopEnd = Color(0x2EFDE293),
        glowBottomEnd = Color.Transparent,
        iconShapeKind = IconShapeKind.CIRCLE,
        iconStyle = IconStyle.CRYSTAL,
        iconSize = IconSize.MEDIUM,
        fontScale = FontScale.MEDIUM,
        showHomeLabels = true,
        clockCentered = false,
        clockWeight = FontWeight.Normal,
        statusBarStyle = StatusBarStyle.LIGHT_ICONS
    )

    val Samsung = CustomStyle(
        background = Color(0xFF0B0F14),
        surface = Color(0xFF141A21),
        accent = Color(0xFF4FA3FF),
        secondary = Color(0xFF7C9CBF),
        onBackground = Color(0xFFEDF1F5),
        onSurfaceVariant = Color(0xFFA9B4C0),
        glowTopStart = Color(0x6B4FA3FF),
        glowTopEnd = Color(0x387C9CBF),
        glowBottomEnd = Color.Transparent,
        iconShapeKind = IconShapeKind.SQUIRCLE,
        iconStyle = IconStyle.CRYSTAL,
        iconSize = IconSize.LARGE,
        fontScale = FontScale.LARGE,
        showHomeLabels = true,
        clockCentered = true,
        clockWeight = FontWeight.Light,
        statusBarStyle = StatusBarStyle.LIGHT_ICONS
    )

    val OnePlus = CustomStyle(
        background = Color(0xFF0A0E1A),
        surface = Color(0xFF121A2E),
        accent = Color(0xFF5EC8FF),
        secondary = Color(0xFF8B5CF6),
        onBackground = Color(0xFFF4F2FF),
        onSurfaceVariant = Color(0xFFB7BEDD),
        glowTopStart = Color(0x735EC8FF),
        glowTopEnd = Color(0x4D8B5CF6),
        glowBottomEnd = Color.Transparent,
        iconShapeKind = IconShapeKind.ROUNDED_SQUARE,
        iconStyle = IconStyle.CRYSTAL,
        iconSize = IconSize.MEDIUM,
        fontScale = FontScale.MEDIUM,
        showHomeLabels = false,
        clockCentered = true,
        clockWeight = FontWeight.Light,
        statusBarStyle = StatusBarStyle.LIGHT_ICONS
    )

    val Miui = CustomStyle(
        background = Color(0xFF16110F),
        surface = Color(0xFF241A16),
        accent = Color(0xFFFF6B35),
        secondary = Color(0xFFFFB454),
        onBackground = Color(0xFFFBEFE9),
        onSurfaceVariant = Color(0xFFD9BBAC),
        glowTopStart = Color(0x59FF6B35),
        glowTopEnd = Color(0x38FFB454),
        glowBottomEnd = Color.Transparent,
        iconShapeKind = IconShapeKind.ROUNDED_SQUARE,
        iconStyle = IconStyle.CRYSTAL,
        iconSize = IconSize.MEDIUM,
        fontScale = FontScale.MEDIUM,
        showHomeLabels = true,
        clockCentered = false,
        clockWeight = FontWeight.Bold,
        statusBarStyle = StatusBarStyle.LIGHT_ICONS
    )

    val presets: List<Pair<LauncherStyle, CustomStyle>> = listOf(
        LauncherStyle.ZEN to Zen,
        LauncherStyle.PIXEL to Pixel,
        LauncherStyle.SAMSUNG to Samsung,
        LauncherStyle.ONEPLUS to OnePlus,
        LauncherStyle.MIUI to Miui
    )

    val default = Zen
}
