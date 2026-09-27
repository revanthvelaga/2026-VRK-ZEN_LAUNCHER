package com.zenfold.launcher.style

import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

class StudioThemesTest {
    @Test fun themeSelectionPreservesEveryNavigationPreference() {
        val current = StylePresets.Zen.copy(showAppPages = false, drawerColumns = 5,
            searchOnSwipe = true, swipeDownSearch = true, showSearchPill = false)
        StudioThemes.all.forEach { theme ->
            val applied = StudioThemes.withCurrentNavigation(theme.style, current)
            assertFalse(applied.showAppPages)
            assertEquals(5, applied.drawerColumns)
            assertTrue(applied.searchOnSwipe)
            assertTrue(applied.swipeDownSearch)
            assertFalse(applied.showSearchPill)
            assertEquals(theme.style.wallpaperArt, applied.wallpaperArt)
            assertEquals(theme.style.clockDesign, applied.clockDesign)
        }
    }
    @Test fun everyThemeHasUniqueStableIdentity() {
        assertEquals(4, StudioThemes.all.map { it.id }.distinct().size)
    }
    @Test fun themeTextMeetsNormalTextContrastOnOpaqueSurfaces() {
        StudioThemes.all.forEach { theme ->
            val s = theme.style
            listOf(s.background, s.surface).forEach { background ->
                listOf(s.onBackground, s.onSurfaceVariant, s.accent).forEach { text ->
                    val a = background.luminance()
                    val b = text.luminance()
                    val contrast = (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
                    assertTrue("${theme.name} contrast $contrast", contrast >= 4.5f)
                }
            }
        }
    }
}
