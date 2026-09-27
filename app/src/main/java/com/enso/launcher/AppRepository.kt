package com.enso.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

/**
 * One installed, launchable app.
 */
data class AppEntry(
    val label: String,
    val packageName: String,
    val icon: Drawable
)

object AppRepository {

    /**
     * Queries every activity that responds to ACTION_MAIN / CATEGORY_LAUNCHER —
     * i.e. every app that shows up in a normal app drawer — sorted by label.
     */
    fun installedApps(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .asSequence()
            .map { resolveInfo ->
                AppEntry(
                    label = resolveInfo.loadLabel(pm).toString(),
                    packageName = resolveInfo.activityInfo.packageName,
                    icon = resolveInfo.loadIcon(pm)
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
    }
}
