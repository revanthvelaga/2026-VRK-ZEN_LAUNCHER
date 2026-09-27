package com.zenfold.launcher.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.zenfold.launcher.style.CustomStyle

/** Our AppWidgetHost's id; stable, since the system ties allocated widget ids to it. */
const val APPWIDGET_HOST_ID = 0x5A46

private const val MIN_WIDGET_HEIGHT_DP = 80f

/** Another app's widget (clock, weather, Google search...), placed full-width on Home. */
@Composable
fun HostedWidget(host: AppWidgetHost, appWidgetId: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Null once the widget's app is uninstalled — it just stops showing.
    val info = remember(appWidgetId) { AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId) } ?: return
    val widthDp = LocalConfiguration.current.screenWidthDp - 40
    val heightDp = maxOf(with(LocalDensity.current) { info.minHeight.toDp().value }, MIN_WIDGET_HEIGHT_DP)

    AndroidView(
        factory = { viewContext ->
            host.createView(viewContext, appWidgetId, info).apply {
                // Tells the widget the size it actually gets, so it can pick the right layout.
                @Suppress("DEPRECATION")
                updateAppWidgetSize(Bundle(), widthDp, heightDp.toInt(), widthDp, heightDp.toInt())
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .clip(RoundedCornerShape(24.dp))
    )
}

private class WidgetProvider(val info: AppWidgetProviderInfo, val appLabel: String, val widgetLabel: String)

private fun loadProviders(context: Context): List<WidgetProvider> {
    val pm = context.packageManager
    return AppWidgetManager.getInstance(context).installedProviders.map { info ->
        val appLabel = try {
            pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            info.provider.packageName
        }
        WidgetProvider(info, appLabel, info.loadLabel(pm))
    }.sortedWith(compareBy<WidgetProvider> { it.appLabel.lowercase() }.thenBy { it.widgetLabel.lowercase() })
}

/** Widget picker sections for other apps' widgets: the ones already on Home, then everything installed. */
@Composable
fun AndroidWidgetsSection(
    style: CustomStyle,
    hostedWidgetIds: List<Int>,
    onAdd: (AppWidgetProviderInfo) -> Unit,
    onRemove: (Int) -> Unit
) {
    val context = LocalContext.current
    val manager = remember { AppWidgetManager.getInstance(context) }
    val providers = remember { loadProviders(context) }
    val pm = context.packageManager

    if (hostedWidgetIds.isNotEmpty()) {
        SectionHeading("On your Home", style)
        hostedWidgetIds.forEach { id ->
            val label = manager.getAppWidgetInfo(id)?.loadLabel(pm) ?: "Widget no longer available"
            WidgetRow(label, null, "Remove", style) { onRemove(id) }
        }
    }

    SectionHeading("Add a widget", style)
    if (providers.isEmpty()) {
        Text("No other apps offer widgets right now.", fontSize = 13.sp, color = style.onSurfaceVariant)
    }
    providers.forEach { provider ->
        WidgetRow(provider.widgetLabel, provider.appLabel, "Add", style) { onAdd(provider.info) }
    }
}

@Composable
private fun SectionHeading(text: String, style: CustomStyle) {
    Text(
        text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        color = style.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun WidgetRow(title: String, subtitle: String?, action: String, style: CustomStyle, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = style.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, fontSize = 12.sp, color = style.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        TextButton(onClick = onAction) { Text(action, color = style.accent, fontWeight = FontWeight.SemiBold) }
    }
}
