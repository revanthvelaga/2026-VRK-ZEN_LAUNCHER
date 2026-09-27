package com.zenfold.launcher.ui

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class AppSearchTest {
    @Test fun exactMatchesRankBeforePrefixesAndSubstrings() {
        val labels = listOf("My Maps", "Maps Go", "Maps", "Roadmaps")
        val sorted = labels.sortedBy { AppSearch.score(it, "maps") }
        assertEquals(listOf("Maps", "Maps Go", "My Maps", "Roadmaps"), sorted)
    }
    @Test fun initialsFindMultiWordApps() {
        assertNotNull(AppSearch.score("Google Maps", "gm"))
        assertNull(AppSearch.score("Gallery", "gm"))
    }
    @Test fun matchesAccentsAndTrimmedInput() {
        assertEquals(0, AppSearch.score("Café", "  CAFE  "))
    }
    @Test fun multiWordPrefixesWorkInAnyOrder() {
        assertNotNull(AppSearch.score("Google Play Store", "store goo"))
        assertNull(AppSearch.score("Google Play Store", "store xyz"))
    }
    @Test fun emptyQueriesAndUnrelatedAppsDoNotMatch() {
        assertNull(AppSearch.score("Maps", "  "))
        assertNull(AppSearch.score("Maps", "music"))
    }
    @Test fun matchingDoesNotDependOnTurkishDeviceLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals(0, AppSearch.score("INSTAGRAM", "instagram"))
        } finally { Locale.setDefault(previous) }
    }
    @Test fun nonLatinLabelsRemainSearchable() {
        assertEquals(0, AppSearch.score("తెలుగు", "తెలుగు"))
        assertNull(AppSearch.score("తెలుగు", "తలగ"))
        assertEquals(1, AppSearch.score("地图导航", "地图"))
    }
}
