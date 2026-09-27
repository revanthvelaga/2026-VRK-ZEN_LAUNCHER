package com.zenfold.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.zenfold.launcher.AppEntry

@Composable
fun AppIcon(app: AppEntry, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val bitmap = remember(app.packageName) {
            app.icon.toBitmap(width = 128, height = 128).asImageBitmap()
        }
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(56.dp)
        ) {
            Image(bitmap = bitmap, contentDescription = app.label)
        }
        Text(
            text = app.label,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
