package com.vrk.dialer

import android.Manifest.permission.CALL_PHONE
import android.Manifest.permission.READ_PHONE_STATE
import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.Uri
import android.os.Bundle
import android.provider.BlockedNumberContract
import android.provider.BlockedNumberContract.BlockedNumbers
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.PhoneLookup
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.core.content.ContextCompat

data class Contact(
    val name: String,
    val number: String,
    val photo: String? = null,
    val starred: Boolean = false
)

data class Recent(
    val name: String?,
    val number: String,
    val type: Int,
    val date: Long,
    val duration: Long = 0,
    val accountId: String? = null,
    val photo: String? = null,
    val ids: List<Long> = emptyList(),
    val count: Int = 1
)

fun String.digits() = filter { it.isDigit() }

/** Same number whatever the formatting: "+91 98480 22338" and "098480-22338" both match. */
fun String.numberKey() = digits().takeLast(10)

private fun granted(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PERMISSION_GRANTED
private fun telecom(ctx: Context) = ctx.getSystemService(TelecomManager::class.java)

fun isDefaultDialer(ctx: Context): Boolean =
    ctx.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_DIALER)

// ---------- Contacts & call log ----------

fun loadContacts(ctx: Context): List<Contact> = runCatching {
    val out = mutableListOf<Contact>()
    ctx.contentResolver.query(
        Phone.CONTENT_URI,
        arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER, Phone.CONTACT_ID, Phone.STARRED),
        null, null, "${Phone.DISPLAY_NAME} COLLATE NOCASE ASC"
    )?.use { c ->
        while (c.moveToNext()) {
            val name = c.getString(0) ?: continue
            val num = c.getString(1) ?: continue
            out += Contact(name, num, contactPhotoUri(c.getLong(2)), c.getInt(3) == 1)
        }
    }
    out.distinctBy { it.name to it.number.numberKey() }
}.getOrDefault(emptyList())

/**
 * The contact's photo through the aggregated Contacts row — the same one Google Contacts
 * and every stock app show — rather than Phone.PHOTO_THUMBNAIL_URI. That column belongs to
 * whichever single raw contact this phone-number row came from; when a number was synced
 * from a *different* raw contact than the one carrying the Google photo (a common shape
 * once a number is merged from more than one source), it's simply null and the photo
 * silently "isn't there" even though the contact clearly has one everywhere else.
 */
private fun contactPhotoUri(contactId: Long): String =
    Uri.withAppendedPath(
        ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId),
        ContactsContract.Contacts.Photo.CONTENT_DIRECTORY
    ).toString()

/** Newest first, one row per call (not grouped) — details screens filter this by number. */
fun loadCallLog(ctx: Context): List<Recent> = runCatching {
    val out = mutableListOf<Recent>()
    ctx.contentResolver.query(
        CallLog.Calls.CONTENT_URI,
        arrayOf(
            CallLog.Calls._ID, CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER, CallLog.Calls.TYPE,
            CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.PHONE_ACCOUNT_ID,
            CallLog.Calls.CACHED_PHOTO_URI
        ),
        null, null, "${CallLog.Calls.DATE} DESC"
    )?.use { c ->
        while (c.moveToNext() && out.size < 1000) {
            out += Recent(
                name = c.getString(1)?.takeIf { it.isNotBlank() },
                number = c.getString(2) ?: "",
                type = c.getInt(3),
                date = c.getLong(4),
                duration = c.getLong(5),
                accountId = c.getString(6),
                photo = c.getString(7),
                ids = listOf(c.getLong(0))
            )
        }
    }
    out
}.getOrDefault(emptyList())

/** MIUI style: back-to-back calls with the same number & type collapse into one row "(3)". */
fun List<Recent>.grouped(): List<Recent> {
    val acc = mutableListOf<Recent>()
    for (r in this) {
        val last = acc.lastOrNull()
        if (last != null && last.number.numberKey() == r.number.numberKey() && last.type == r.type) {
            acc[acc.lastIndex] = last.copy(count = last.count + 1, ids = last.ids + r.ids)
        } else acc += r
    }
    return acc
}

fun isMissed(type: Int) = type == CallLog.Calls.MISSED_TYPE || type == CallLog.Calls.REJECTED_TYPE

/** Only the default Phone app may delete call history (it holds WRITE_CALL_LOG). */
fun deleteCalls(ctx: Context, ids: List<Long>): Boolean = runCatching {
    if (ids.isEmpty()) return true
    ids.chunked(500).forEach { chunk ->
        ctx.contentResolver.delete(
            CallLog.Calls.CONTENT_URI,
            "${CallLog.Calls._ID} IN (${chunk.joinToString(",") { "?" }})",
            chunk.map { it.toString() }.toTypedArray()
        )
    }
    true
}.getOrDefault(false)

fun clearCallLog(ctx: Context): Boolean =
    runCatching { ctx.contentResolver.delete(CallLog.Calls.CONTENT_URI, null, null); true }.getOrDefault(false)

fun lookupName(ctx: Context, number: String): String? = runCatching {
    if (number.isBlank()) return null
    val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
    ctx.contentResolver.query(uri, arrayOf(PhoneLookup.DISPLAY_NAME), null, null, null)
        ?.use { if (it.moveToFirst()) it.getString(0) else null }
}.getOrNull()

/** The saved contact for a number, to open it in the Contacts app. Null if it isn't saved. */
fun contactUriFor(ctx: Context, number: String): Uri? = runCatching {
    if (number.isBlank()) return null
    val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
    ctx.contentResolver.query(uri, arrayOf(PhoneLookup._ID, PhoneLookup.LOOKUP_KEY), null, null, null)?.use {
        if (it.moveToFirst()) ContactsContract.Contacts.getLookupUri(it.getLong(0), it.getString(1)) else null
    }
}.getOrNull()

// ---------- Blocked numbers (the system list; only the default Phone app may edit it) ----------

fun canBlock(ctx: Context): Boolean =
    runCatching { BlockedNumberContract.canCurrentUserBlockNumbers(ctx) }.getOrDefault(false)

fun isBlocked(ctx: Context, number: String): Boolean =
    runCatching { BlockedNumberContract.isBlocked(ctx, number) }.getOrDefault(false)

fun blockNumber(ctx: Context, number: String): Boolean = runCatching {
    ctx.contentResolver.insert(
        BlockedNumbers.CONTENT_URI,
        ContentValues().apply { put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number) }
    ) != null
}.getOrDefault(false)

fun unblockNumber(ctx: Context, number: String): Boolean =
    runCatching { BlockedNumberContract.unblock(ctx, number) > 0 }.getOrDefault(false)

fun loadBlocked(ctx: Context): List<String> = runCatching {
    val out = mutableListOf<String>()
    ctx.contentResolver.query(
        BlockedNumbers.CONTENT_URI, arrayOf(BlockedNumbers.COLUMN_ORIGINAL_NUMBER), null, null, null
    )?.use { c -> while (c.moveToNext()) c.getString(0)?.let { out += it } }
    out
}.getOrDefault(emptyList())

// ---------- T9 search (type 7-2-6 to find "Ram") ----------

/** A contact's name, pre-normalized to T9 form once, so a keystroke re-checks it in O(1)
 * string operations instead of redoing the whole conversion for every contact every time. */
class T9Entry internal constructor(val contact: Contact, private val t9: String, private val words: List<String>) {
    fun matches(query: String): Boolean {
        if (query.isEmpty()) return true
        if (contact.number.digits().contains(query)) return true
        return words.any { it.startsWith(query) } ||
            t9.startsWith(query) ||
            words.joinToString("") { it.take(1) }.startsWith(query) // initials
    }
}

fun buildT9Index(contacts: List<Contact>): List<T9Entry> = contacts.map { c ->
    val t9 = c.name.toT9()
    T9Entry(c, t9.replace(" ", ""), t9.split(' ').filter { it.isNotEmpty() })
}

private val T9: Map<Char, Char> = buildMap {
    listOf("2abc", "3def", "4ghi", "5jkl", "6mno", "7pqrs", "8tuv", "9wxyz")
        .forEach { s -> s.drop(1).forEach { put(it, s[0]) } }
}

private fun String.toT9() = lowercase().map { T9[it] ?: if (it.isDigit()) it else ' ' }.joinToString("")

// ---------- SIM & placing calls ----------

@SuppressLint("MissingPermission")
fun simAccounts(ctx: Context): List<PhoneAccountHandle> =
    if (granted(ctx, READ_PHONE_STATE)) telecom(ctx).callCapablePhoneAccounts else emptyList()

@SuppressLint("MissingPermission")
fun defaultSim(ctx: Context): PhoneAccountHandle? =
    if (granted(ctx, READ_PHONE_STATE)) telecom(ctx).getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL) else null

fun simLabel(ctx: Context, h: PhoneAccountHandle): String =
    telecom(ctx).getPhoneAccount(h)?.label?.toString() ?: "SIM"

/** Call-log account id → SIM name ("Jio", "Airtel"), only when there's more than one SIM. */
fun simLabels(ctx: Context): Map<String, String> {
    val sims = simAccounts(ctx)
    if (sims.size < 2) return emptyMap()
    return sims.associate { it.id to simLabel(ctx, it) }
}

@SuppressLint("MissingPermission")
fun placeCall(ctx: Context, number: String, sim: PhoneAccountHandle?) {
    if (number.isBlank()) return
    placeCall(ctx, Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null), sim)
}

/** Long-press 1, as on every phone. */
fun callVoicemail(ctx: Context) = placeCall(ctx, Uri.fromParts(PhoneAccount.SCHEME_VOICEMAIL, "", null), null)

@SuppressLint("MissingPermission")
private fun placeCall(ctx: Context, uri: Uri, sim: PhoneAccountHandle?) {
    if (!granted(ctx, CALL_PHONE)) {
        Toast.makeText(ctx, "Allow Phone permission to make calls", Toast.LENGTH_SHORT).show()
        return
    }
    val extras = Bundle().apply {
        sim?.let { putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, it) }
    }
    runCatching { telecom(ctx).placeCall(uri, extras) }
        .onFailure { Toast.makeText(ctx, "Couldn't place the call", Toast.LENGTH_SHORT).show() }
}

/** Clears the "missed call" notification once you've looked at Recents. */
// Telecom authorizes the default dialer as an alternative to the signature-only
// MODIFY_PHONE_STATE permission; lint cannot infer a runtime role check.
@SuppressLint("MissingPermission")
fun markMissedCallsRead(ctx: Context) {
    if (!isDefaultDialer(ctx)) return
    runCatching { telecom(ctx).cancelMissedCallsNotification() }
}

// ---------- Speed dial & quick replies (stored on the phone) ----------

private fun prefs(ctx: Context) = ctx.getSharedPreferences("dialer", Context.MODE_PRIVATE)

data class SpeedDial(val name: String, val number: String)

fun speedDial(ctx: Context, digit: Int): SpeedDial? =
    prefs(ctx).getString("speed_$digit", null)?.split('\u001F')?.takeIf { it.size == 2 }
        ?.let { SpeedDial(it[0], it[1]) }

fun setSpeedDial(ctx: Context, digit: Int, entry: SpeedDial?) {
    prefs(ctx).edit().apply {
        if (entry == null) remove("speed_$digit") else putString("speed_$digit", "${entry.name}\u001F${entry.number}")
    }.apply()
}

val DEFAULT_QUICK_REPLIES = listOf(
    "Can't talk now. Call me later?",
    "I'll call you right back.",
    "I'm in a meeting.",
    "I'm driving. I'll call you later."
)

fun quickReplies(ctx: Context): List<String> =
    prefs(ctx).getString("quick_replies", null)?.split('\n')?.filter { it.isNotBlank() }
        ?.takeIf { it.isNotEmpty() } ?: DEFAULT_QUICK_REPLIES

fun setQuickReplies(ctx: Context, replies: List<String>) {
    prefs(ctx).edit().putString("quick_replies", replies.filter { it.isNotBlank() }.joinToString("\n")).apply()
}

// App-only favourites do not modify the user's synced address book.
fun isLocalFavorite(ctx: Context, number: String): Boolean =
    ctx.getSharedPreferences("contact_extras", Context.MODE_PRIVATE).getBoolean("favorite_${number.numberKey()}", false)
fun setLocalFavorite(ctx: Context, number: String, favorite: Boolean) {
    ctx.getSharedPreferences("contact_extras", Context.MODE_PRIVATE).edit()
        .putBoolean("favorite_${number.numberKey()}", favorite)
        .putString("favorite_number_${number.numberKey()}", number).apply()
}

fun localFavoriteNumbers(ctx: Context): List<String> {
    val p = ctx.getSharedPreferences("contact_extras", Context.MODE_PRIVATE)
    return p.all.filter { it.key.startsWith("favorite_") && it.value == true }.keys.mapNotNull {
        p.getString("favorite_number_${it.removePrefix("favorite_")}", null)
    }
}
