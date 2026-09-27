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
}

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
}

private fun Preferences.toRecentPackages(): List<String> =
    this[Keys.RECENTS]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
