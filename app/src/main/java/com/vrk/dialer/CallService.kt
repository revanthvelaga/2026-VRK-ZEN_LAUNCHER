@file:Suppress("DEPRECATION") // Call.getState() / onCallAudioStateChanged: minSdk 29

package com.vrk.dialer

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService

/** System binds to this while any call exists, because we're the default Phone app. */
class CallService : InCallService() {

    override fun onCallAdded(call: Call) {
        CallManager.service = this
        CallManager.add(call)
        // Incoming calls go through the notification: a heads-up with Answer/Decline while
        // you're using the phone (as on MIUI/OxygenOS), full screen when it's locked — the
        // system picks from the full-screen intent. Without notification permission there'd
        // be no way to answer, so then (and for outgoing calls) open the call screen directly.
        // InCallService is exempt from background-activity-start limits.
        if (call.state != Call.STATE_RINGING || !CallNotifications.canPost(this)) {
            startActivity(InCallActivity.intent(this))
        }
    }

    override fun onCallRemoved(call: Call) {
        CallManager.remove(call)
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        CallManager.audio.value = audioState
    }

    fun refreshNotification() = CallNotifications.update(this)

    override fun onDestroy() {
        CallNotifications.cancel(this)
        CallManager.service = null
        super.onDestroy()
    }
}
