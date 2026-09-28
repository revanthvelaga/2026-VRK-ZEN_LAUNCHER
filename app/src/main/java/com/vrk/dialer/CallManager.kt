package com.vrk.dialer

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.PhoneAccountHandle
import android.telecom.VideoProfile
import kotlinx.coroutines.flow.MutableStateFlow

/** Single source of truth for the current call. v1 handles one call at a time. */
@Suppress("DEPRECATION")
object CallManager {
    val call = MutableStateFlow<Call?>(null)
    val state = MutableStateFlow(Call.STATE_NEW)
    var service: CallService? = null

    private val callback = object : Call.Callback() {
        override fun onStateChanged(c: Call, newState: Int) {
            if (c == call.value) state.value = newState
        }
    }

    fun add(c: Call) {
        c.registerCallback(callback)
        call.value = c
        state.value = c.state
    }

    fun remove(c: Call) {
        c.unregisterCallback(callback)
        if (call.value == c) {
            state.value = Call.STATE_DISCONNECTED
            call.value = null
        }
    }

    fun answer() { call.value?.answer(VideoProfile.STATE_AUDIO_ONLY) }

    fun hangUp() {
        val c = call.value ?: return
        if (c.state == Call.STATE_RINGING) c.reject(false, null) else c.disconnect()
    }

    fun setHold(on: Boolean) { call.value?.let { if (on) it.hold() else it.unhold() } }

    fun dtmf(ch: Char) { call.value?.let { it.playDtmfTone(ch); it.stopDtmfTone() } }

    fun setMute(on: Boolean) { service?.setMuted(on) }

    fun setSpeaker(on: Boolean) {
        service?.setAudioRoute(
            if (on) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_WIRED_OR_EARPIECE
        )
    }

    /** Used when a call was started without a SIM and the phone is set to "ask every time". */
    fun selectSim(handle: PhoneAccountHandle) { call.value?.phoneAccountSelected(handle, false) }
}
