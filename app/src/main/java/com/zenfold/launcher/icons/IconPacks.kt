package com.zenfold.launcher.icons

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException

data class IconPackInfo(val packageName: String, val label: String)

// The launcher-theme actions icon packs declare so launchers can find them.
private val ICON_PACK_ACTIONS = listOf(
    "org.adw.launcher.THEMES",
    "com.novalauncher.THEME",
    "com.teslacoilsw.launcher.THEME",
    "com.anddoes.launcher.THEME",
    "com.gau.go.launcherex.theme"
)

fun installedIconPacks(context: Context): List<IconPackInfo> {
    val pm = context.packageManager
    return ICON_PACK_ACTIONS
        .flatMap { action -> pm.queryIntentActivities(Intent(action), 0) }
        .map { it.activityInfo.applicationInfo }
        .distinctBy { it.packageName }
        .map { IconPackInfo(it.packageName, it.loadLabel(pm).toString()) }
        .sortedBy { it.label.lowercase() }
}

/**
 * A loaded icon pack. Every pack ships the de facto standard `appfilter.xml` (as an XML
 * resource or an asset) mapping `ComponentInfo{package/activity}` to a drawable name.
 */
class IconPack private constructor(
    private val packageName: String,
    private val resources: Resources,
    private val drawableByComponent: Map<String, String>
) {

    // Exact activity first; failing that, any entry for the same app (packs often key on
    // an older launcher activity name).
    @SuppressLint("DiscouragedApi")
    fun iconFor(component: ComponentName): Drawable? {
        val name = drawableByComponent["ComponentInfo{${component.packageName}/${component.className}}"]
            ?: drawableByComponent.entries.firstOrNull { it.key.startsWith("ComponentInfo{${component.packageName}/") }?.value
            ?: return null
        val id = resources.getIdentifier(name, "drawable", packageName)
        if (id == 0) return null
        return try {
            ResourcesCompat.getDrawable(resources, id, null)
        } catch (e: Resources.NotFoundException) {
            null
        }
    }

    companion object {
        fun load(context: Context, packageName: String): IconPack? = try {
            val resources = context.packageManager.getResourcesForApplication(packageName)
            IconPack(packageName, resources, parseAppFilter(resources, packageName))
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }

        @SuppressLint("DiscouragedApi")
        private fun parseAppFilter(resources: Resources, packageName: String): Map<String, String> {
            val parser: XmlPullParser = resources.getIdentifier("appfilter", "xml", packageName)
                .takeIf { it != 0 }
                ?.let { resources.getXml(it) }
                ?: try {
                    XmlPullParserFactory.newInstance().newPullParser().apply {
                        setInput(resources.assets.open("appfilter.xml"), "UTF-8")
                    }
                } catch (e: IOException) {
                    return emptyMap()
                } catch (e: XmlPullParserException) {
                    return emptyMap()
                }

            // A malformed pack keeps whatever parsed before the error.
            val map = mutableMapOf<String, String>()
            try {
                while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == XmlPullParser.START_TAG && parser.name == "item") {
                        val component = parser.getAttributeValue(null, "component")
                        val drawable = parser.getAttributeValue(null, "drawable")
                        if (component != null && drawable != null) map.putIfAbsent(component, drawable)
                    }
                    parser.next()
                }
            } catch (e: XmlPullParserException) {
                return map
            } catch (e: IOException) {
                return map
            }
            return map
        }
    }
}
