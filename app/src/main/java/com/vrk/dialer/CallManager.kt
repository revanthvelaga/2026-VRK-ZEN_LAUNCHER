@file:Suppress("DEPRECATION") // Call.getState(): Details.getState() only exists from API 31, minSdk is 29

package com.vrk.dialer

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.PhoneAccountHandle
import android.telecom.VideoProfile
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Every call Telecom has handed us, and the audio route. Several calls can exist at once:
 * call waiting (one active + one ringing), two calls with one on hold, or a conference.
 */
object CallManager {
    /** Top-level calls; a conference's participants are shown as part of the conference. */
    val calls = MutableStateFlow<List<Call>>(emptyList())

    /** Bumped on every change. Call objects mutate in place, so the list alone can't signal it. */
    val version = MutableStateFlow(0)

    val audio = MutableStateFlow<CallAudioState?>(null)
    var service: CallService? = null

    private val all = mutableListOf<Call>()

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = changed()
        override fun onDetailsChanged(call: Call, details: Call.Details) = changed()
        override fun onParentChanged(call: Call, parent: Call?) = changed()
        override fun onChildrenChanged(call: Call, children: List<Call>) = changed()
        override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: List<Call>) = changed()
    }

    private fun changed() {
        calls.value = all.filter { it.parent == null }
        version.value++
        service?.refreshNotification()
    }

    fun add(call: Call) {
        call.registerCallback(callback)
        all += call
        changed()
    }

    fun remove(call: Call) {
        call.unregisterCallback(callback)
        all -= call
        changed()
    }

    // ---------- Which call is which ----------

    fun ringing(): Call? = calls.value.firstOrNull { it.state == Call.STATE_RINGING }
    fun active(): Call? = calls.value.firstOrNull { it.state == Call.STATE_ACTIVE }
    fun held(): Call? = calls.value.firstOrNull { it.state == Call.STATE_HOLDING }

    /** The call the screen is about: a ringing one first (so call waiting shows), then active. */
    fun primary(): Call? = ringing() ?: active()
        ?: calls.value.firstOrNull { it.state in OUTGOING_STATES }
        ?: calls.value.firstOrNull()

    private val OUTGOING_STATES = setOf(
        Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_PULLING_CALL,
        Call.STATE_SELECT_PHONE_ACCOUNT, Call.STATE_NEW
    )

    // ---------- Actions ----------

    fun answer(call: Call? = ringing()) {
        call?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    /** With [message], Telecom texts the caller (the MIUI/OxygenOS "decline with message"). */
    fun decline(call: Call? = ringing(), message: String? = null) {
        call?.reject(message != null, message)
    }

    fun hangUp(call: Call? = primary()) {
        call ?: return
        if (call.state == Call.STATE_RINGING) call.reject(false, null) else call.disconnect()
    }

    /** Call waiting: "End & answer". (Plain answer = "Hold & answer": Telecom holds the other.) */
    fun endActiveAndAnswer() {
        val incoming = ringing() ?: return
        active()?.disconnect()
        incoming.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    fun toggleHold(call: Call? = active() ?: held()) {
        call ?: return
        if (call.state == Call.STATE_HOLDING) call.unhold() else call.hold()
    }

    /** Unholding the held call makes Telecom hold the active one first — that's the swap. */
    fun swap() {
        held()?.unhold()
    }

    fun canMerge(): Boolean {
        val call = active() ?: return false
        return call.conferenceableCalls.isNotEmpty() ||
            call.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE)
    }

    fun merge() {
        val call = active() ?: return
        val other = call.conferenceableCalls.firstOrNull()
        if (other != null) call.conference(other) else call.mergeConference()
    }

    fun dtmf(ch: Char) {
        primary()?.let {
            it.playDtmfTone(ch)
            it.stopDtmfTone()
        }
    }

    fun setMute(on: Boolean) {
        service?.setMuted(on)
    }

    fun setRoute(route: Int) {
        service?.setAudioRoute(route)
    }

    /** Used when a call was started without a SIM and the phone is set to "ask every time". */
    fun selectSim(handle: PhoneAccountHandle) {
        calls.value.firstOrNull { it.state == Call.STATE_SELECT_PHONE_ACCOUNT }
            ?.phoneAccountSelected(handle, false)
    }
}

fun Call.number(): String = details.handle?.schemeSpecificPart.orEmpty()

val Call.isConference: Boolean
    get() = details.hasProperty(Call.Details.PROPERTY_CONFERENCE) || children.isNotEmpty()

/** Call.Details.can(capabilities, capability) is a static method, not an instance one. */
fun Call.Details.can(capability: Int): Boolean = Call.Details.can(callCapabilities, capability)
