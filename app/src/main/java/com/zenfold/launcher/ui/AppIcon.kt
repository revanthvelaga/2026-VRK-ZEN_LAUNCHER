package com.zenfold.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenfold.launcher.AppEntry
import com.zenfold.launcher.style.CustomStyle
import com.zenfold.launcher.toUnmaskedBitmap

@Composable
fun AppIcon(app: AppEntry, style: CustomStyle, showLabel: Boolean = true, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val bitmap = remember(app.packageName) {
            app.icon.toUnmaskedBitmap(128).asImageBitmap()
        }
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(style.iconSize.sizeDp.dp)
                .clip(style.iconShapeKind.shape)
        ) {
            Image(bitmap = bitmap, contentDescription = app.label)
        }
        if (showLabel) {
            Text(
                text = app.label,
                fontSize = (12 * style.fontScale.scale).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = style.onBackground
            )
        }
    }
}
