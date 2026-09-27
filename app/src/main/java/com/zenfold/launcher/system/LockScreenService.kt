package com.zenfold.launcher.system

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import java.lang.ref.WeakReference

/**
 * Powers "double-tap Home to turn the screen off". An accessibility service is the only way
 * a regular app can lock the screen without becoming a device admin (which would disable
 * fingerprint/face unlock on the next wake). It reads no events and no screen content.
 */
class LockScreenService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = WeakReference(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        private var instance: WeakReference<LockScreenService>? = null

        /** False when the user hasn't turned the service on, or on Android 8 (no lock action). */
        fun lockScreen(): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
            return instance?.get()?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) == true
        }

        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, LockScreenService::class.java)
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        fun openSettings(context: Context) {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
