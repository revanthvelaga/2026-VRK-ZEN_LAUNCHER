package com.vrk.dialer

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

enum class PhoneTheme(val title: String, val description: String) {
    SAPPHIRE("Sapphire", "Pearl surfaces, sapphire accents and sculpted keys"),
    FLOW("Flow", "Open keys, a recent-call shelf and expressive caller photos"),
    LUMINOUS("Luminous", "Soft mint light, emerald accents and rounded keys")
}
enum class DisplayMode(val title: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }
data class Appearance(val theme: PhoneTheme = PhoneTheme.SAPPHIRE, val mode: DisplayMode = DisplayMode.SYSTEM,
    val haptics: Boolean = true, val tones: Boolean = true, val motion: Boolean = true)
val LocalAppearance = staticCompositionLocalOf { Appearance() }
fun appearancePrefs(ctx: Context): SharedPreferences = ctx.getSharedPreferences("appearance", Context.MODE_PRIVATE)
fun readAppearance(p: SharedPreferences) = Appearance(
    PhoneTheme.entries.firstOrNull { it.name == p.getString("theme", null) } ?: PhoneTheme.SAPPHIRE,
    DisplayMode.entries.firstOrNull { it.name == p.getString("mode", null) } ?: DisplayMode.SYSTEM,
    p.getBoolean("haptics", true), p.getBoolean("tones", true), p.getBoolean("motion", true))
@Composable fun rememberAppearance(ctx: Context): Appearance {
    val prefs = remember(ctx) { appearancePrefs(ctx) }
    var state by remember(prefs) { mutableStateOf(readAppearance(prefs)) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> state = readAppearance(prefs) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        state = readAppearance(prefs)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}

@Composable fun AppearanceScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val saved = LocalAppearance.current
    var preview by rememberSaveable { mutableStateOf(saved.theme) }
    val dark = when (saved.mode) { DisplayMode.SYSTEM -> isSystemInDarkTheme(); DisplayMode.DARK -> true; else -> false }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Appearance", style = MaterialTheme.typography.headlineMedium)
        }
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text("Make it yours", style = MaterialTheme.typography.headlineLarge)
                Text("Preview a theme, then apply it to every screen.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                MaterialTheme(colorScheme = themeColors(preview, dark)) {
                    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${preview.title} preview", style = MaterialTheme.typography.titleMedium)
                            Text("98765 43210", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(12.dp))
                            listOf(listOf("1", "2", "3"), listOf("4", "5", "6")).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                    row.forEach { digit ->
                                        Box(Modifier.padding(vertical = 4.dp).size(52.dp).background(
                                            if (preview == PhoneTheme.FLOW) Color.Transparent else MaterialTheme.colorScheme.surface,
                                            if (preview == PhoneTheme.LUMINOUS) RoundedCornerShape(18.dp) else CircleShape), contentAlignment = Alignment.Center) {
                                            Text(digit, style = MaterialTheme.typography.headlineMedium)
                                        }
                                    }
                                }
                            }
                            Box(Modifier.padding(top = 12.dp).width(180.dp).height(44.dp).background(CallGreen, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Call, null, tint = Color.White)
                            }
                        }
                    }
                }
            }
            PhoneTheme.entries.forEach { theme -> item {
                Surface(shape = RoundedCornerShape(22.dp), color = if (theme == preview) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                    ListItem(headlineContent = { Text(theme.title) }, supportingContent = { Text(theme.description) },
                        leadingContent = { RadioButton(selected = preview == theme, onClick = null) },
                        trailingContent = { if (saved.theme == theme) Icon(Icons.Default.Check, "Applied") },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { preview = theme })
                }
            } }
            item { Button(onClick = { appearancePrefs(ctx).edit().putString("theme", preview.name).apply() },
                enabled = preview != saved.theme, modifier = Modifier.fillMaxWidth()) {
                Text(if (preview == saved.theme) "${preview.title} applied" else "Apply ${preview.title}")
            } }
            item {
                Text("Display", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DisplayMode.entries.forEach { mode -> FilterChip(selected = saved.mode == mode,
                        onClick = { appearancePrefs(ctx).edit().putString("mode", mode.name).apply() }, label = { Text(mode.title) }) }
                }
            }
            item { PreferenceSwitch("Touch vibration", "Feedback on keys and call controls", saved.haptics) { appearancePrefs(ctx).edit().putBoolean("haptics", it).apply() } }
            item { PreferenceSwitch("Keypad sounds", "Touch tones while entering a number", saved.tones) { appearancePrefs(ctx).edit().putBoolean("tones", it).apply() } }
            item { PreferenceSwitch("Decorative motion", "Caller pulse and key animations", saved.motion) { appearancePrefs(ctx).edit().putBoolean("motion", it).apply() } }
        }
    }
}
@Composable private fun PreferenceSwitch(title: String, detail: String, enabled: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(headlineContent = { Text(title) }, supportingContent = { Text(detail) }, trailingContent = {
        Switch(checked = enabled, onCheckedChange = onChange, modifier = Modifier.semantics { contentDescription = title })
    })
}
