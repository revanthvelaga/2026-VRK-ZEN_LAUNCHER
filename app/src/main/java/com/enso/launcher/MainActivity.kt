package com.enso.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.enso.launcher.ui.HomeScreen
import com.enso.launcher.ui.theme.EnsoTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            EnsoTheme {
                val apps = remember { AppRepository.installedApps(this) }

                HomeScreen(
                    apps = apps,
                    onLaunch = { app ->
                        packageManager.getLaunchIntentForPackage(app.packageName)?.let {
                            startActivity(it)
                        }
                    }
                )
            }
        }
    }

    // A launcher's Home screen shouldn't disappear on system Back —
    // there's nothing "behind" it to go back to.
    override fun onBackPressed() {
        // Intentionally does nothing.
    }
}
