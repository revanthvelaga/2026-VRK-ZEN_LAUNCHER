package com.zenfold.launcher.style

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

data class StudioTheme(val id: String, val name: String, val description: String, val style: CustomStyle)

/** Original coordinated palettes; themes change appearance, never the user's navigation or layout. */
object StudioThemes {
    val all = listOf(
        StudioTheme("dune", "Dune", "Warm sand. Sculpted shapes. A quieter home.", StylePresets.Zen.copy(
            background = Color(0xFF201A17), surface = Color(0xFF372B25),
            accent = Color(0xFFF2C49B), secondary = Color(0xFFC79977),
            onBackground = Color(0xFFFFF4E9), onSurfaceVariant = Color(0xFFD6C4B8),
            glowTopStart = Color(0x403D221C), glowTopEnd = Color(0x405A4633), glowBottomEnd = Color.Transparent,
            iconStyle = IconStyle.TONAL, clockCentered = false, clockWeight = FontWeight.Light,
            wallpaperArt = WallpaperArt.DUNE, clockDesign = ClockDesign.EDITORIAL
        )),
        StudioTheme("orbit", "Orbit", "Midnight blue. A luminous edge.", StylePresets.Zen.copy(
            background = Color(0xFF080F21), surface = Color(0xFF16243D),
            accent = Color(0xFFC1D9FF), secondary = Color(0xFF7A9ECA),
            onBackground = Color(0xFFF1F5FF), onSurfaceVariant = Color(0xFFB7C8E1),
            glowTopStart = Color(0x402B428A), glowTopEnd = Color(0x40458ECA), glowBottomEnd = Color.Transparent,
            iconStyle = IconStyle.TONAL, iconShapeKind = IconShapeKind.CIRCLE,
            clockCentered = false, wallpaperArt = WallpaperArt.ORBIT, clockDesign = ClockDesign.STACKED
        )),
        StudioTheme("moss", "Moss", "Forest tones. Soft light. Familiar icons.", StylePresets.Zen.copy(
            background = Color(0xFF101D19), surface = Color(0xFF20352B),
            accent = Color(0xFFD2E3B8), secondary = Color(0xFF7DAE93),
            onBackground = Color(0xFFF1F5E9), onSurfaceVariant = Color(0xFFBACFC0),
            glowTopStart = Color(0x3056A67C), glowTopEnd = Color(0x40485C31), glowBottomEnd = Color.Transparent,
            iconStyle = IconStyle.ORIGINAL, clockCentered = false,
            wallpaperArt = WallpaperArt.MIST, clockDesign = ClockDesign.EDITORIAL
        )),
        StudioTheme("porcelain", "Porcelain", "A bright canvas with ink-blue details.", StylePresets.Pixel.copy(
            background = Color(0xFFF4F0E9), surface = Color(0xFFE6E0D7),
            accent = Color(0xFF334F68), secondary = Color(0xFF778DA0),
            onBackground = Color(0xFF202B34), onSurfaceVariant = Color(0xFF4B5961),
            glowTopStart = Color(0x20A8B7C5), glowTopEnd = Color(0x20CBA987), glowBottomEnd = Color.Transparent,
            iconStyle = IconStyle.TONAL, statusBarStyle = StatusBarStyle.DARK_ICONS,
            wallpaperArt = WallpaperArt.DUNE, clockDesign = ClockDesign.EDITORIAL
        ))
    )

    fun withCurrentNavigation(theme: CustomStyle, current: CustomStyle) = theme.copy(
        showAppPages = current.showAppPages, drawerColumns = current.drawerColumns,
        searchOnSwipe = current.searchOnSwipe, swipeDownSearch = current.swipeDownSearch,
        showSearchPill = current.showSearchPill
    )
}
