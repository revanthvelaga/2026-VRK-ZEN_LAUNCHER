package com.zenfold.launcher.ui

import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.zenfold.launcher.AppEntry
import com.zenfold.launcher.AppRepository
import com.zenfold.launcher.icons.IconPack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class IconPackHolder {
    var packageName: String? = null
    var pack: IconPack? = null
}

/**
 * Installed apps, kept current: the launcher's activity lives for days, so apps installed,
 * updated or uninstalled meanwhile must show up (or disappear) without a restart. Icons come
 * from [iconPackPackage] when one is chosen.
 */
@Composable
fun rememberInstalledApps(iconPackPackage: String? = null): List<AppEntry> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val iconPack = remember { IconPackHolder() }
    var apps by remember { mutableStateOf(AppRepository.installedApps(context)) }

    fun reload() {
        scope.launch { apps = withContext(Dispatchers.IO) { AppRepository.installedApps(context, iconPack.pack) } }
    }

    LaunchedEffect(iconPackPackage) {
        if (iconPackPackage == iconPack.packageName) return@LaunchedEffect
        apps = withContext(Dispatchers.IO) {
            iconPack.pack = iconPackPackage?.let { IconPack.load(context, it) }
            iconPack.packageName = iconPackPackage
            AppRepository.installedApps(context, iconPack.pack)
        }
    }

    DisposableEffect(context) {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val callback = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) = reload()
            override fun onPackageAdded(packageName: String, user: UserHandle) = reload()
            override fun onPackageChanged(packageName: String, user: UserHandle) = reload()
            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
        }
        launcherApps?.registerCallback(callback, Handler(Looper.getMainLooper()))
        onDispose { launcherApps?.unregisterCallback(callback) }
    }
    return apps
}
