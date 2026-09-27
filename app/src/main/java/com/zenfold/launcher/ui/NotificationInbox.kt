package com.zenfold.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenfold.launcher.AppEntry
import com.zenfold.launcher.notifications.ZenFoldNotificationListener
import com.zenfold.launcher.style.CustomStyle

/** Optional launcher inbox. Android's notification shade remains the system shade. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationInbox(style: CustomStyle, apps: List<AppEntry>, hiddenPackages: Set<String>, onLaunch: (AppEntry) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val notifications by ZenFoldNotificationListener.previews.collectAsState()
    val appMap = remember(apps) { apps.associateBy { it.packageName } }
    val groups = remember(notifications, hiddenPackages) {
        notifications.filterNot { it.packageName in hiddenPackages }.groupBy { it.packageName }
    }
    var showText by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = style.background, contentColor = style.onBackground) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp)) {
            Text("Your inbox", fontSize = 30.sp, fontWeight = FontWeight.Light)
            Text("Notifications, grouped by app", fontSize = 13.sp, color = style.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Show message previews", Modifier.weight(1f))
                Switch(checked = showText, onCheckedChange = { showText = it })
            }
            if (!ZenFoldNotificationListener.isEnabled(context)) {
                Text("Allow notification access to see updates here. You can use ZenFold without it.", color = style.onSurfaceVariant)
                TextButton(onClick = { ZenFoldNotificationListener.openSettings(context) }) { Text("Open notification access") }
            } else if (groups.isEmpty()) {
                Text("All caught up", Modifier.padding(vertical = 36.dp), color = style.onSurfaceVariant)
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(groups.entries.toList(), key = { it.key }) { (pkg, entries) ->
                    val app = appMap[pkg]
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(style.surface)
                        .clickable(enabled = app != null) { app?.let(onLaunch); onDismiss() }.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${app?.label ?: pkg} · ${entries.size}", fontWeight = FontWeight.SemiBold, color = style.accent)
                        if (showText) entries.take(3).forEach { entry ->
                            if (entry.title.isNotBlank()) Text(entry.title, fontSize = 14.sp, maxLines = 2)
                            if (entry.text.isNotBlank()) Text(entry.text, fontSize = 13.sp, maxLines = 3, color = style.onSurfaceVariant)
                        } else Text("Content hidden", fontSize = 13.sp, color = style.onSurfaceVariant)
                        if (app != null) Text("Open app →", fontSize = 12.sp, color = style.accent)
                    }
                }
            }
        }
    }
}
