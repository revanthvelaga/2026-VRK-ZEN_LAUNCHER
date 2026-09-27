package com.zenfold.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony

/**
 * One installed, launchable app.
 */
data class AppEntry(
    val label: String,
    val packageName: String,
    val icon: Drawable
)

/** What the home screen shows: the glass dock at the bottom, and the icon grid above it. */
data class HomeApps(val dock: List<AppEntry>, val grid: List<AppEntry>)

/** A cell in the home screen's icon grid — user-movable, not a fixed list order. */
data class GridPos(val row: Int, val col: Int)

const val GRID_COLUMNS = 4
const val GRID_ROWS = 4
private const val GRID_SIZE = GRID_COLUMNS * GRID_ROWS

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

    /**
     * Dock = the phone's own default dialer, SMS app, camera and browser. Grid = other
     * everyday defaults (gallery, maps, music...), then recently used apps, then the rest.
     * Each slot tries the system default first and falls back to well-known packages.
     */
    fun homeApps(context: Context, apps: List<AppEntry>, recentPackages: List<String>): HomeApps {
        val pm = context.packageManager
        val byPackage = apps.associateBy { it.packageName }
        fun firstInstalled(candidates: List<String?>): AppEntry? =
            candidates.firstNotNullOfOrNull { pkg -> pkg?.let(byPackage::get) }

        val dock = listOfNotNull(
            firstInstalled(handlers(pm, Intent(Intent.ACTION_DIAL)) + "com.google.android.dialer"),
            firstInstalled(listOf(Telephony.Sms.getDefaultSmsPackage(context), "com.google.android.apps.messaging")),
            firstInstalled(
                handlers(pm, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) +
                    handlers(pm, Intent(MediaStore.ACTION_IMAGE_CAPTURE))
            ),
            firstInstalled(handlers(pm, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))) + "com.android.chrome")
        ).distinctBy { it.packageName }

        val used = dock.mapTo(mutableSetOf()) { it.packageName }
        val grid = mutableListOf<AppEntry>()
        fun add(entry: AppEntry?) {
            if (entry != null && grid.size < GRID_SIZE && used.add(entry.packageName)) grid += entry
        }

        add(firstInstalled(handlers(pm, category(Intent.CATEGORY_APP_GALLERY)) + "com.google.android.apps.photos"))
        add(firstInstalled(handlers(pm, category(Intent.CATEGORY_APP_MAPS)) + "com.google.android.apps.maps"))
        add(firstInstalled(handlers(pm, category(Intent.CATEGORY_APP_MUSIC)) + "com.google.android.apps.youtube.music"))
        add(firstInstalled(handlers(pm, category(Intent.CATEGORY_APP_CALENDAR)) + "com.google.android.calendar"))
        add(firstInstalled(handlers(pm, category(Intent.CATEGORY_APP_EMAIL)) + "com.google.android.gm"))
        add(firstInstalled(handlers(pm, Intent(AlarmClock.ACTION_SHOW_ALARMS)) + "com.google.android.deskclock"))
        add(firstInstalled(listOf("com.android.vending")))
        add(firstInstalled(handlers(pm, Intent(Settings.ACTION_SETTINGS)) + "com.android.settings"))
        recentPackages.forEach { add(byPackage[it]) }
        apps.forEach { add(it) }

        return HomeApps(dock = dock, grid = grid)
    }

    private fun category(category: String) = Intent(Intent.ACTION_MAIN).addCategory(category)

    // The user's chosen default first (skipping the system chooser), then any other handler.
    private fun handlers(pm: PackageManager, intent: Intent): List<String> {
        val default = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        val all = pm.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }
        return (listOfNotNull(default) + all).distinct()
    }
}
