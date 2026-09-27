package com.zenfold.launcher.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenfold.launcher.AppEntry
import com.zenfold.launcher.home.DEFAULT_FOLDER_NAME
import com.zenfold.launcher.style.CustomStyle
import dev.chrisbanes.haze.HazeState

private const val FOLDER_COLUMNS = 4

/** A folder on the home grid: a glass tile previewing its first four apps in a 2×2 grid. */
@Composable
fun FolderIcon(
    name: String,
    apps: List<AppEntry>,
    style: CustomStyle,
    showLabel: Boolean,
    badged: Boolean,
    onClick: () -> Unit
) {
    val size = style.iconSize.sizeDp.dp
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Column(
                Modifier
                    .size(size)
                    .glassTint(style.iconShapeKind.shape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable(onClick = onClick)
                    .padding(size * 0.12f),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                apps.take(4).chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        row.forEach { app ->
                            AppIconImage(app, style, Modifier.size(size * 0.34f).clip(style.iconShapeKind.shape))
                        }
                    }
                }
            }
            if (badged) NotificationDot(style, Modifier.align(Alignment.TopEnd))
        }
        if (showLabel) IconLabel(name, style)
    }
}

/**
 * An open folder: its apps over a blurred backdrop, with an editable title. The caller
 * fades the home screen out underneath — Haze blurs only the wallpaper, so anything left
 * visible on Home would otherwise show through this glass unblurred.
 */
@Composable
fun FolderOverlay(
    name: String,
    apps: List<AppEntry>,
    style: CustomStyle,
    hazeState: HazeState,
    badged: Set<String>,
    menuActions: (AppEntry) -> List<MenuAction>,
    onLaunch: (AppEntry) -> Unit,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember(name) { mutableStateOf(name) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current

    fun saveTitle() {
        if (title.trim() != name) onRename(title)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.25f))
            .pointerInput(Unit) {
                detectTapGestures {
                    saveTitle()
                    onDismiss()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .glass(hazeState, RoundedCornerShape(32.dp))
                // Swallows taps so they don't reach the scrim behind and close the folder.
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BasicTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = style.onBackground,
                    textAlign = TextAlign.Center
                ),
                cursorBrush = SolidColor(style.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    saveTitle()
                    focusManager.clearFocus()
                }),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(18.dp))
            apps.chunked(FOLDER_COLUMNS).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    row.forEach { app ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                            AppIcon(
                                app,
                                style = style,
                                badged = app.packageName in badged,
                                onLongClick = { menuFor = app.packageName },
                                onClick = { onLaunch(app) }
                            )
                            AppActionsMenu(
                                app,
                                expanded = menuFor == app.packageName,
                                onDismiss = { menuFor = null },
                                actions = if (menuFor == app.packageName) menuActions(app) else emptyList()
                            )
                        }
                    }
                    repeat(FOLDER_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * Names a new folder the way Pixel/One UI do: after the apps' shared Play Store category
 * ("Games", "Social & Communication"...), or plain "Folder" when they don't share one.
 */
fun folderNameFor(context: Context, packages: List<String>): String {
    val pm = context.packageManager
    val category = packages.map { pkg ->
        try {
            pm.getApplicationInfo(pkg, 0).category
        } catch (e: PackageManager.NameNotFoundException) {
            ApplicationInfo.CATEGORY_UNDEFINED
        }
    }.distinct().singleOrNull()?.takeIf { it != ApplicationInfo.CATEGORY_UNDEFINED }
    return category?.let { ApplicationInfo.getCategoryTitle(context, it)?.toString() } ?: DEFAULT_FOLDER_NAME
}
