@file:Suppress("DEPRECATION") // Call.getState(): minSdk 29

package com.vrk.dialer

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.Call
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** The incoming-call heads-up and the ongoing-call notification (one at a time, one id). */
object CallNotifications {
    private const val CHANNEL_INCOMING = "incoming_calls"
    private const val CHANNEL_ONGOING = "ongoing_calls"
    private const val NOTIFICATION_ID = 1

    // Contact lookups are a database query; a call's notification is rebuilt on every state
    // change, so each number is looked up once.
    private val names = HashMap<String, String?>()

    fun canPost(ctx: Context) = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    private fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        // Telecom plays the ringtone and vibration itself, so both channels stay silent.
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_INCOMING, "Incoming calls", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ONGOING, "Ongoing calls", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    @SuppressLint("MissingPermission") // canPost() checks it
    fun update(ctx: Context) {
        val call = CallManager.primary() ?: return cancel(ctx)
        if (!canPost(ctx)) return
        ensureChannels(ctx)

        val number = call.number()
        val name = when {
            call.isConference -> "Conference call"
            names.containsKey(number) -> names[number]
            else -> lookupName(ctx, number).also { names[number] = it }
        }
        val ringing = call.state == Call.STATE_RINGING
        val open = PendingIntent.getActivity(
            ctx, REQUEST_OPEN, InCallActivity.intent(ctx),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(ctx, if (ringing) CHANNEL_INCOMING else CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_call_notification)
            .setContentTitle(name ?: number.ifEmpty { "Unknown number" })
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setOnlyAlertOnce(!ringing)
            .setContentIntent(open)
            .setColor(0xFF2DBE60.toInt())

        if (ringing) {
            // Answer opens the call screen and answers there: Android 12+ blocks starting an
            // activity from a broadcast fired by a notification action.
            val answer = PendingIntent.getActivity(
                ctx, REQUEST_ANSWER, InCallActivity.intent(ctx, answer = true),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.setContentText(if (name != null) "Incoming call · $number" else "Incoming call")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setFullScreenIntent(open, true)
                .addAction(0, "Decline", CallActionReceiver.pending(ctx, CallActionReceiver.DECLINE))
                .addAction(0, "Answer", answer)
        } else {
            val connected = call.details.connectTimeMillis
            val active = call.state == Call.STATE_ACTIVE && connected > 0
            builder.setContentText(if (call.state == Call.STATE_HOLDING) "On hold" else if (active) "Ongoing call" else "Calling…")
            if (active) builder.setUsesChronometer(true).setWhen(connected).setShowWhen(true)
            builder.addAction(0, "Hang up", CallActionReceiver.pending(ctx, CallActionReceiver.HANG_UP))
        }
        NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, builder.build())
    }

    fun cancel(ctx: Context) {
        NotificationManagerCompat.from(ctx).cancel(NOTIFICATION_ID)
        names.clear()
    }

    private const val REQUEST_OPEN = 1
    private const val REQUEST_ANSWER = 2
}

/** Decline / Hang up from the notification, without opening the app. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            DECLINE -> CallManager.decline()
            HANG_UP -> CallManager.hangUp()
        }
    }

    companion object {
        const val DECLINE = "com.vrk.dialer.DECLINE"
        const val HANG_UP = "com.vrk.dialer.HANG_UP"

        fun pending(ctx: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
            ctx, action.hashCode(),
            Intent(ctx, CallActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
