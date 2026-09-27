package com.zenfold.launcher.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.net.Uri
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zenfold.launcher.AppEntry
import com.zenfold.launcher.toUnmaskedBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A context-specific entry in an app's long-press menu ("Add to Home", "Remove from folder"...). */
data class MenuAction(val label: String, val icon: ImageVector? = null, val onClick: () -> Unit)

private const val MAX_SHORTCUTS = 4

private class AppShortcut(val info: ShortcutInfo, val label: String, val icon: ImageBitmap?)

/**
 * The long-press menu every launcher has: the app's own shortcuts first ("New message",
 * "Compose"...), then [actions], then App info / Uninstall.
 */
@Composable
fun AppActionsMenu(app: AppEntry, expanded: Boolean, onDismiss: () -> Unit, actions: List<MenuAction>) {
    val context = LocalContext.current
    val shortcuts by produceState(emptyList<AppShortcut>(), app.packageName, expanded) {
        value = if (expanded) withContext(Dispatchers.IO) { loadShortcuts(context, app.packageName) } else emptyList()
    }

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        shortcuts.forEach { shortcut ->
            DropdownMenuItem(
                text = { Text(shortcut.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = shortcut.icon?.let { icon ->
                    { Image(icon, contentDescription = null, modifier = Modifier.size(22.dp).clip(CircleShape)) }
                },
                onClick = {
                    onDismiss()
                    startShortcut(context, shortcut.info)
                }
            )
        }
        if (shortcuts.isNotEmpty()) HorizontalDivider()
        actions.forEach { action ->
            MenuItem(action.label, action.icon) { onDismiss(); action.onClick() }
        }
        MenuItem("App info", Icons.Outlined.Info) { onDismiss(); openAppInfo(context, app.packageName) }
        // Built-in system apps can't be uninstalled, only disabled from App info.
        if (remember(app.packageName) { isUninstallable(context, app.packageName) }) {
            MenuItem("Uninstall", Icons.Outlined.Delete) { onDismiss(); uninstallApp(context, app.packageName) }
        }
    }
}

@Composable
private fun MenuItem(label: String, icon: ImageVector?, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = icon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(20.dp)) } },
        onClick = onClick
    )
}

fun openAppInfo(context: Context, packageName: String) {
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
}

/** Anything the user installed, or an update to a built-in app (which rolls back to the factory version). */
fun isUninstallable(context: Context, packageName: String): Boolean = try {
    val flags = context.packageManager.getApplicationInfo(packageName, 0).flags
    flags and ApplicationInfo.FLAG_SYSTEM == 0 || flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
} catch (e: PackageManager.NameNotFoundException) {
    false
}

// Android's own "Do you want to uninstall this app?" dialog; the app disappears from Home,
// the dock and the app pages by itself once it's gone (see InstalledApps.kt).
fun uninstallApp(context: Context, packageName: String) {
    if (!isUninstallable(context, packageName)) {
        Toast.makeText(context, "Built-in apps can't be uninstalled — you can disable it in App info", Toast.LENGTH_LONG).show()
        return openAppInfo(context, packageName)
    }
    try {
        context.startActivity(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null)))
    } catch (e: ActivityNotFoundException) {
        openAppInfo(context, packageName)
    }
}

// Only the default Home app may read other apps' shortcuts, so this is empty until
// ZenFold is set as the launcher.
private fun loadShortcuts(context: Context, packageName: String): List<AppShortcut> {
    val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return emptyList()
    return try {
        if (!launcherApps.hasShortcutHostPermission()) return emptyList()
        val query = LauncherApps.ShortcutQuery()
            .setPackage(packageName)
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC)
        val density = context.resources.displayMetrics.densityDpi
        launcherApps.getShortcuts(query, Process.myUserHandle()).orEmpty()
            .filter { it.isEnabled }
            .sortedWith(compareBy<ShortcutInfo> { it.isDynamic }.thenBy { it.rank })
            .take(MAX_SHORTCUTS)
            .mapNotNull { info ->
                val label = (info.shortLabel ?: info.longLabel)?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val icon = launcherApps.getShortcutIconDrawable(info, density)?.toUnmaskedBitmap(64)?.asImageBitmap()
                AppShortcut(info, label, icon)
            }
    } catch (e: SecurityException) {
        emptyList()
    } catch (e: IllegalStateException) {
        emptyList()
    }
}

private fun startShortcut(context: Context, shortcut: ShortcutInfo) {
    try {
        context.getSystemService(LauncherApps::class.java)?.startShortcut(shortcut, null, null)
    } catch (e: RuntimeException) {
        // ActivityNotFound / IllegalState (user locked) / Security (no longer default launcher).
        Toast.makeText(context, "Couldn't open that shortcut", Toast.LENGTH_SHORT).show()
    }
}
