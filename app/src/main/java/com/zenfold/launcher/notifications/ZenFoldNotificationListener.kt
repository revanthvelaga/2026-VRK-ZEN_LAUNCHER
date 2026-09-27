package com.zenfold.launcher.notifications

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.NotificationListenerService.RankingMap
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

    // An app turning its notification-dot setting on/off arrives as a ranking change.
    override fun onNotificationRankingUpdate(rankingMap: RankingMap) {
        publish()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        _previews.value = emptyList()
        _badgedPackages.value = emptySet()
    }

    private fun publish() {
        val active = activeNotifications ?: return
        val ranking = Ranking()
        val rankings = currentRanking
        // Same rule as the system launcher: no dots for ongoing notifications (music,
        // navigation...) or for channels the app or user marked "don't show dot".
        _badgedPackages.value = active
            .filter { sbn ->
                sbn.packageName != packageName && !sbn.isOngoing &&
                    (rankings == null || (rankings.getRanking(sbn.key, ranking) && ranking.canShowBadge()))
            }
            .mapTo(mutableSetOf()) { it.packageName }

        _previews.value = active
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

        private val _badgedPackages = MutableStateFlow<Set<String>>(emptySet())
        /** Apps that should show a notification dot on their icon. */
        val badgedPackages: StateFlow<Set<String>> = _badgedPackages.asStateFlow()

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
