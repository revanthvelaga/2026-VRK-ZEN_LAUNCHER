package com.vrk.dialer

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService

/** System binds to this while any call exists, because we're the default Phone app. */
class CallService : InCallService() {

    override fun onCallAdded(call: Call) {
        CallManager.service = this
        CallManager.add(call)
        // InCallService is exempt from background-activity-start limits
        startActivity(
            Intent(this, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    override fun onCallRemoved(call: Call) {
        CallManager.remove(call)
    }

    override fun onDestroy() {
        CallManager.service = null
        super.onDestroy()
    }
}
