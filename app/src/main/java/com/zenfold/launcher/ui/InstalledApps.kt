package com.zenfold.launcher.ui

import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.zenfold.launcher.AppEntry
import com.zenfold.launcher.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Installed apps, kept current: the launcher's activity lives for days, so apps installed,
 * updated or uninstalled meanwhile must show up (or disappear) without a restart.
 */
@Composable
fun rememberInstalledApps(): List<AppEntry> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf(AppRepository.installedApps(context)) }

    DisposableEffect(context) {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val callback = object : LauncherApps.Callback() {
            private fun reload() {
                scope.launch { apps = withContext(Dispatchers.IO) { AppRepository.installedApps(context) } }
            }

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
