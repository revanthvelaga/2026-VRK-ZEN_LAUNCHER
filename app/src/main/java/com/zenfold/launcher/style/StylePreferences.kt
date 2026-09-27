package com.zenfold.launcher.style

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zenfold.launcher.GridPos
import com.zenfold.launcher.home.DEFAULT_GRID
import com.zenfold.launcher.home.GridSpec
import com.zenfold.launcher.home.HomeItem
import com.zenfold.launcher.home.HomeLayout
import com.zenfold.launcher.widgets.WidgetType
import kotlinx.coroutines.flow.map

private val Context.launcherPrefs by preferencesDataStore(name = "launcher_prefs")

private object Keys {
    val BACKGROUND = intPreferencesKey("background")
    val SURFACE = intPreferencesKey("surface")
    val ACCENT = intPreferencesKey("accent")
    val SECONDARY = intPreferencesKey("secondary")
    val ON_BACKGROUND = intPreferencesKey("on_background")
    val ON_SURFACE_VARIANT = intPreferencesKey("on_surface_variant")
    val GLOW_TOP_START = intPreferencesKey("glow_top_start")
    val GLOW_TOP_END = intPreferencesKey("glow_top_end")
    val GLOW_BOTTOM_END = intPreferencesKey("glow_bottom_end")
    val ICON_SHAPE = stringPreferencesKey("icon_shape")
    val ICON_STYLE = stringPreferencesKey("icon_style")
    val ICON_SIZE = stringPreferencesKey("icon_size")
    val FONT_SCALE = stringPreferencesKey("font_scale")
    val SHOW_LABELS = booleanPreferencesKey("show_labels")
    val CLOCK_CENTERED = booleanPreferencesKey("clock_centered")
    val CLOCK_WEIGHT = intPreferencesKey("clock_weight")
    val STATUS_BAR = stringPreferencesKey("status_bar")
    val WIDGETS = stringPreferencesKey("widgets")
    val NOTE_TEXT = stringPreferencesKey("note_text")
    val RECENTS = stringPreferencesKey("recents")
    // Pre-folders layout (per-app positions + "removed from home"); only read to seed HOME_ITEMS.
    val HOME_LAYOUT = stringPreferencesKey("home_layout")
    val HIDDEN_HOME = stringPreferencesKey("hidden_home")
    val HOME_ITEMS = stringPreferencesKey("home_items")
    val HIDDEN_APPS = stringPreferencesKey("hidden_apps")
    val GRID_COLUMNS = intPreferencesKey("grid_columns")
    val GRID_ROWS = intPreferencesKey("grid_rows")
    val DOCK = stringPreferencesKey("dock")
}

const val MAX_DOCK_APPS = 5

private const val MAX_RECENTS = 8

private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
    name?.let { stored -> enumValues<T>().firstOrNull { it.name == stored } } ?: default

private fun Preferences.toCustomStyle(): CustomStyle {
    val default = StylePresets.default
    return CustomStyle(
        background = this[Keys.BACKGROUND]?.let { Color(it) } ?: default.background,
        surface = this[Keys.SURFACE]?.let { Color(it) } ?: default.surface,
        accent = this[Keys.ACCENT]?.let { Color(it) } ?: default.accent,
        secondary = this[Keys.SECONDARY]?.let { Color(it) } ?: default.secondary,
        onBackground = this[Keys.ON_BACKGROUND]?.let { Color(it) } ?: default.onBackground,
        onSurfaceVariant = this[Keys.ON_SURFACE_VARIANT]?.let { Color(it) } ?: default.onSurfaceVariant,
        glowTopStart = this[Keys.GLOW_TOP_START]?.let { Color(it) } ?: default.glowTopStart,
        glowTopEnd = this[Keys.GLOW_TOP_END]?.let { Color(it) } ?: default.glowTopEnd,
        glowBottomEnd = this[Keys.GLOW_BOTTOM_END]?.let { Color(it) } ?: default.glowBottomEnd,
        iconShapeKind = enumOrDefault(this[Keys.ICON_SHAPE], default.iconShapeKind),
        iconStyle = enumOrDefault(this[Keys.ICON_STYLE], default.iconStyle),
        iconSize = enumOrDefault(this[Keys.ICON_SIZE], default.iconSize),
        fontScale = enumOrDefault(this[Keys.FONT_SCALE], default.fontScale),
        showHomeLabels = this[Keys.SHOW_LABELS] ?: default.showHomeLabels,
        clockCentered = this[Keys.CLOCK_CENTERED] ?: default.clockCentered,
        clockWeight = this[Keys.CLOCK_WEIGHT]?.let { FontWeight(it) } ?: default.clockWeight,
        statusBarStyle = enumOrDefault(this[Keys.STATUS_BAR], default.statusBarStyle)
    )
}

private fun MutablePreferences.writeCustomStyle(style: CustomStyle) {
    this[Keys.BACKGROUND] = style.background.toArgb()
    this[Keys.SURFACE] = style.surface.toArgb()
    this[Keys.ACCENT] = style.accent.toArgb()
    this[Keys.SECONDARY] = style.secondary.toArgb()
    this[Keys.ON_BACKGROUND] = style.onBackground.toArgb()
    this[Keys.ON_SURFACE_VARIANT] = style.onSurfaceVariant.toArgb()
    this[Keys.GLOW_TOP_START] = style.glowTopStart.toArgb()
    this[Keys.GLOW_TOP_END] = style.glowTopEnd.toArgb()
    this[Keys.GLOW_BOTTOM_END] = style.glowBottomEnd.toArgb()
    this[Keys.ICON_SHAPE] = style.iconShapeKind.name
    this[Keys.ICON_STYLE] = style.iconStyle.name
    this[Keys.ICON_SIZE] = style.iconSize.name
    this[Keys.FONT_SCALE] = style.fontScale.name
    this[Keys.SHOW_LABELS] = style.showHomeLabels
    this[Keys.CLOCK_CENTERED] = style.clockCentered
    this[Keys.CLOCK_WEIGHT] = style.clockWeight.weight
    this[Keys.STATUS_BAR] = style.statusBarStyle.name
}

private fun Preferences.toWidgetSet(): Set<WidgetType> {
    val raw = this[Keys.WIDGETS] ?: return setOf(WidgetType.GLANCE)
    if (raw.isBlank()) return emptySet()
    return raw.split(",").mapNotNull { id -> WidgetType.entries.firstOrNull { it.id == id } }.toSet()
}

class StylePreferences(private val context: Context) {

    val customStyle = context.launcherPrefs.data.map { it.toCustomStyle() }
    val enabledWidgets = context.launcherPrefs.data.map { it.toWidgetSet() }
    val noteText = context.launcherPrefs.data.map { it[Keys.NOTE_TEXT] ?: "" }
    val recentPackages = context.launcherPrefs.data.map { it.toRecentPackages() }
    val homeItems = context.launcherPrefs.data.map { prefs -> prefs[Keys.HOME_ITEMS]?.let(HomeLayout::decode) ?: emptyList() }
    val hiddenApps = context.launcherPrefs.data.map { it.toPackageSet(Keys.HIDDEN_APPS) }
    val gridSpec = context.launcherPrefs.data.map { prefs ->
        GridSpec(
            columns = prefs[Keys.GRID_COLUMNS] ?: DEFAULT_GRID.columns,
            rows = prefs[Keys.GRID_ROWS] ?: DEFAULT_GRID.rows
        )
    }
    val dockPackages = context.launcherPrefs.data.map { prefs -> prefs.toPackageList(Keys.DOCK) }

    suspend fun setGridSpec(spec: GridSpec) {
        context.launcherPrefs.edit { prefs ->
            prefs[Keys.GRID_COLUMNS] = spec.columns
            prefs[Keys.GRID_ROWS] = spec.rows
        }
    }

    suspend fun editDock(transform: (List<String>) -> List<String>) {
        context.launcherPrefs.edit { prefs ->
            prefs[Keys.DOCK] = transform(prefs.toPackageList(Keys.DOCK)).distinct().take(MAX_DOCK_APPS).joinToString(",")
        }
    }

    suspend fun applyPreset(style: CustomStyle) {
        context.launcherPrefs.edit { prefs -> prefs.writeCustomStyle(style) }
    }

    suspend fun update(transform: (CustomStyle) -> CustomStyle) {
        context.launcherPrefs.edit { prefs ->
            val next = transform(prefs.toCustomStyle())
            prefs.writeCustomStyle(next)
        }
    }

    suspend fun setWidgetEnabled(type: WidgetType, enabled: Boolean) {
        context.launcherPrefs.edit { prefs ->
            val current = prefs.toWidgetSet().toMutableSet()
            if (enabled) current += type else current -= type
            prefs[Keys.WIDGETS] = current.joinToString(",") { it.id }
        }
    }

    suspend fun setNoteText(text: String) {
        context.launcherPrefs.edit { prefs -> prefs[Keys.NOTE_TEXT] = text }
    }

    suspend fun recordLaunch(packageName: String) {
        context.launcherPrefs.edit { prefs ->
            val next = (listOf(packageName) + prefs.toRecentPackages().filterNot { it == packageName })
                .take(MAX_RECENTS)
            prefs[Keys.RECENTS] = next.joinToString(",")
        }
    }

    /**
     * Applies [transform] to the stored layout inside one DataStore transaction, so rapid
     * edits (ticking several apps in a row) each build on the last instead of racing.
     */
    suspend fun editHomeItems(transform: (List<HomeItem>) -> List<HomeItem>) {
        context.launcherPrefs.edit { prefs ->
            val current = prefs[Keys.HOME_ITEMS]?.let(HomeLayout::decode) ?: emptyList()
            prefs[Keys.HOME_ITEMS] = HomeLayout.encode(transform(current))
        }
    }

    /**
     * Builds Home and the dock once — Home from the pre-folders layout if there is one,
     * else the defaults — and never again: after this, only the user decides what's there.
     */
    suspend fun seedHomeIfNeeded(
        homeSeed: (oldPositions: Map<String, GridPos>, oldRemoved: Set<String>) -> List<HomeItem>,
        dockSeed: () -> List<String>
    ) {
        context.launcherPrefs.edit { prefs ->
            if (prefs[Keys.HOME_ITEMS] == null) {
                prefs[Keys.HOME_ITEMS] = HomeLayout.encode(homeSeed(prefs.toHomeLayout(), prefs.toPackageSet(Keys.HIDDEN_HOME)))
            }
            if (prefs[Keys.DOCK] == null) {
                prefs[Keys.DOCK] = dockSeed().take(MAX_DOCK_APPS).joinToString(",")
            }
        }
    }

    /** Hidden apps leave the drawer, search and Home; they stay installed. */
    suspend fun setAppHidden(packageName: String, hidden: Boolean) {
        context.launcherPrefs.edit { prefs ->
            val current = prefs.toPackageSet(Keys.HIDDEN_APPS).toMutableSet()
            if (hidden) {
                current += packageName
                prefs[Keys.HOME_ITEMS]?.let { raw ->
                    prefs[Keys.HOME_ITEMS] = HomeLayout.encode(HomeLayout.removeApp(HomeLayout.decode(raw), packageName))
                }
                prefs[Keys.DOCK]?.let { prefs[Keys.DOCK] = (prefs.toPackageList(Keys.DOCK) - packageName).joinToString(",") }
            } else {
                current -= packageName
            }
            prefs[Keys.HIDDEN_APPS] = current.joinToString(",")
        }
    }
}

private fun Preferences.toRecentPackages(): List<String> =
    this[Keys.RECENTS]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()

private fun Preferences.toPackageSet(key: Preferences.Key<String>): Set<String> = toPackageList(key).toSet()

private fun Preferences.toPackageList(key: Preferences.Key<String>): List<String> =
    this[key]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()

private fun Preferences.toHomeLayout(): Map<String, GridPos> {
    val raw = this[Keys.HOME_LAYOUT] ?: return emptyMap()
    return raw.split(",").mapNotNull { entry ->
        val parts = entry.split(":")
        val row = parts.getOrNull(1)?.toIntOrNull()
        val col = parts.getOrNull(2)?.toIntOrNull()
        if (parts.size == 3 && row != null && col != null) parts[0] to GridPos(row, col) else null
    }.toMap()
}
