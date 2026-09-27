package com.zenfold.launcher.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import com.zenfold.launcher.style.CustomStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

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

/** Every widget one app offers, like the groups in Android's own widget picker. */
private class WidgetApp(val packageName: String, val label: String, val widgets: List<AppWidgetProviderInfo>)

private fun loadWidgetApps(context: Context): List<WidgetApp> {
    val pm = context.packageManager
    return AppWidgetManager.getInstance(context).installedProviders
        .groupBy { it.provider.packageName }
        .map { (pkg, widgets) ->
            val label = try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                pkg
            }
            WidgetApp(pkg, label, widgets.sortedBy { it.loadLabel(pm).lowercase() })
        }
        .sortedBy { it.label.lowercase() }
}

private const val MAX_PREVIEW_PX = 480

// Some drawables (plain colours, vectors without a size) report no intrinsic size.
private fun Drawable.toImage(): ImageBitmap {
    val width = intrinsicWidth.takeIf { it > 0 } ?: MAX_PREVIEW_PX
    val height = intrinsicHeight.takeIf { it > 0 } ?: MAX_PREVIEW_PX
    val scale = minOf(1f, MAX_PREVIEW_PX.toFloat() / maxOf(width, height))
    return toBitmap((width * scale).roundToInt().coerceAtLeast(1), (height * scale).roundToInt().coerceAtLeast(1)).asImageBitmap()
}

// The widget's own preview picture, else its icon. Either can fail inside the other app's
// resources (missing density, bad drawable), in which case there's simply no picture.
private fun loadPreview(context: Context, info: AppWidgetProviderInfo): ImageBitmap? = try {
    val density = context.resources.displayMetrics.densityDpi
    (info.loadPreviewImage(context, density) ?: info.loadIcon(context, density))?.toImage()
} catch (e: RuntimeException) {
    null
}

private fun loadAppIcon(context: Context, pkg: String): ImageBitmap? = try {
    context.packageManager.getApplicationIcon(pkg).toImage()
} catch (e: PackageManager.NameNotFoundException) {
    null
} catch (e: RuntimeException) {
    null
}

// "2 × 1"-style home-grid size, as Android's picker shows it: the provider's own target
// size on Android 12+, else the usual estimate from its minimum size (70dp cells, 30dp gaps).
private fun cellSize(info: AppWidgetProviderInfo, dpPerPx: Float): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.targetCellWidth > 0 && info.targetCellHeight > 0) {
        return "${info.targetCellWidth} × ${info.targetCellHeight}"
    }
    fun cells(px: Int) = ((px * dpPerPx + 30) / 70).toInt().coerceIn(1, 5)
    return "${cells(info.minWidth)} × ${cells(info.minHeight)}"
}

/**
 * The Android-widgets half of the picker: the ones already on Home, then every app that
 * offers widgets — tap an app to see its widgets' previews, tap a preview to add it.
 */
@Composable
fun AndroidWidgetsSection(
    style: CustomStyle,
    hostedWidgetIds: List<Int>,
    onAdd: (AppWidgetProviderInfo) -> Unit,
    onRemove: (Int) -> Unit
) {
    val context = LocalContext.current
    val manager = remember { AppWidgetManager.getInstance(context) }
    val widgetApps by produceState<List<WidgetApp>?>(null) {
        value = withContext(Dispatchers.IO) { loadWidgetApps(context) }
    }
    var expandedApp by remember { mutableStateOf<String?>(null) }
    val pm = context.packageManager

    if (hostedWidgetIds.isNotEmpty()) {
        SectionHeading("On your Home", style)
        hostedWidgetIds.forEach { id ->
            val label = manager.getAppWidgetInfo(id)?.loadLabel(pm) ?: "Widget no longer available"
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 14.sp, color = style.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                TextButton(onClick = { onRemove(id) }) { Text("Remove", color = style.accent, fontWeight = FontWeight.SemiBold) }
            }
        }
    }

    SectionHeading("App widgets", style)
    val apps = widgetApps
    when {
        apps == null -> Text("Loading widgets…", fontSize = 13.sp, color = style.onSurfaceVariant)
        apps.isEmpty() -> Text("No apps offer widgets right now.", fontSize = 13.sp, color = style.onSurfaceVariant)
        else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            apps.forEach { app ->
                WidgetAppGroup(
                    app = app,
                    expanded = expandedApp == app.packageName,
                    style = style,
                    onToggle = { expandedApp = if (expandedApp == app.packageName) null else app.packageName },
                    onAdd = onAdd
                )
            }
        }
    }
}

@Composable
private fun WidgetAppGroup(
    app: WidgetApp,
    expanded: Boolean,
    style: CustomStyle,
    onToggle: () -> Unit,
    onAdd: (AppWidgetProviderInfo) -> Unit
) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, app.packageName) {
        value = withContext(Dispatchers.IO) { loadAppIcon(context, app.packageName) }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = if (expanded) 0.08f else 0.04f))
            .animateContentSize()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(36.dp)) {
                icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(36.dp)) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = style.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val count = app.widgets.size
                Text(if (count == 1) "1 widget" else "$count widgets", fontSize = 12.sp, color = style.onSurfaceVariant)
            }
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = style.onSurfaceVariant
            )
        }
        // Previews only load once their app is opened, so the picker opens instantly
        // however many widget apps are installed.
        if (expanded) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp)
            ) {
                items(app.widgets, key = { it.provider.flattenToString() }) { info ->
                    WidgetPreviewCard(info, style) { onAdd(info) }
                }
            }
        }
    }
}

@Composable
private fun WidgetPreviewCard(info: AppWidgetProviderInfo, style: CustomStyle, onClick: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val preview by produceState<ImageBitmap?>(null, info.provider) {
        value = withContext(Dispatchers.IO) { loadPreview(context, info) }
    }
    val label = remember(info) { info.loadLabel(context.packageManager) }
    val size = remember(info) { cellSize(info, 1f / density.density) }
    Column(
        Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            preview?.let { Image(it, contentDescription = label, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(94.dp)) }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = style.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(size, fontSize = 11.sp, color = style.onSurfaceVariant)
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
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
    )
}
