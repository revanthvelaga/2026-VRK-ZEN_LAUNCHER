package com.enso.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enso.launcher.AppEntry
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun HomeScreen(apps: List<AppEntry>, onLaunch: (AppEntry) -> Unit) {
    var drawerOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    // First run: everyone starts with nothing pinned but the first few apps,
    // so the home screen is never empty. A real build persists the user's
    // own picks (Room / DataStore) instead of recomputing this every launch.
    val favorites = remember(apps) { apps.take(8) }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = 72.dp, start = 24.dp, end = 24.dp)
        ) {
            ClockBlock()
            Spacer(Modifier.height(48.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(favorites) { app ->
                    AppIcon(app, onClick = { onLaunch(app) })
                }
            }
        }

        // Pull-tab: tap (or, in a fuller build, swipe up) to open the drawer.
        TextButton(
            onClick = { drawerOpen = true; query = "" },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            Box(
                Modifier
                    .width(40.dp)
                    .height(5.dp)
                    .background(
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
                        RoundedCornerShape(3.dp)
                    )
            )
        }
    }

    if (drawerOpen) {
        AppDrawer(
            apps = apps,
            query = query,
            onQueryChange = { query = it },
            onLaunch = {
                onLaunch(it)
                drawerOpen = false
            },
            onDismiss = { drawerOpen = false }
        )
    }
}

@Composable
private fun ClockBlock() {
    val now = remember { Calendar.getInstance() }
    val time = remember { SimpleDateFormat("h:mm", Locale.getDefault()).format(now.time) }
    val date = remember { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(now.time) }

    Text(
        text = time,
        fontSize = 64.sp,
        fontWeight = FontWeight.Light,
        color = MaterialTheme.colorScheme.onBackground
    )
    Text(
        text = date,
        fontSize = 16.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
