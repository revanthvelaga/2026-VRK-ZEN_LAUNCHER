package com.zenfold.launcher.notifications

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NotificationPreview(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String
)

// The system won't let a launcher read notification content without this
// listener, and the user has to grant it manually from Settings (there's no
// runtime permission dialog for it) — see isEnabled/openSettings below.
class ZenFoldNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        publish()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        publish()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        publish()
    }

    private fun publish() {
        _previews.value = activeNotifications
            .filterNot { it.packageName == packageName }
            .sortedByDescending { it.postTime }
            .mapNotNull { sbn ->
                val extras = sbn.notification.extras
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
                if (title.isBlank() && text.isBlank()) {
                    null
                } else {
                    NotificationPreview(key = sbn.key, packageName = sbn.packageName, title = title, text = text)
                }
            }
    }

    companion object {
        private val _previews = MutableStateFlow<List<NotificationPreview>>(emptyList())
        val previews: StateFlow<List<NotificationPreview>> = _previews.asStateFlow()

        fun isEnabled(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

        fun openSettings(context: Context) {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
