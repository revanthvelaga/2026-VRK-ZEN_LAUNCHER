package com.zenfold.launcher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Current time in millis, refreshed every minute (ACTION_TIME_TICK), on clock/timezone
 * changes, and whenever the launcher comes back to the foreground — TIME_TICK can lag
 * up to a minute after the screen turns on, which would show a stale clock after unlock.
 */
@Composable
fun rememberCurrentTimeMillis(): Long {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    DisposableEffect(context, lifecycleOwner) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                now = System.currentTimeMillis()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) now = System.currentTimeMillis()
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            context.unregisterReceiver(receiver)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    return now
}
