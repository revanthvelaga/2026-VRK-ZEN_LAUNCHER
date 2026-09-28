package com.vrk.dialer

import android.Manifest.permission.CALL_PHONE
import android.Manifest.permission.READ_PHONE_STATE
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.PhoneLookup
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.core.content.ContextCompat

data class Contact(val name: String, val number: String)
data class Recent(val name: String?, val number: String, val type: Int, val date: Long, val count: Int = 1)

fun String.digits() = filter { it.isDigit() }
private fun String.key() = digits().takeLast(10)
private fun granted(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PERMISSION_GRANTED
private fun telecom(ctx: Context) = ctx.getSystemService(TelecomManager::class.java)

// ---------- Contacts & call log ----------

fun loadContacts(ctx: Context): List<Contact> = runCatching {
    val out = mutableListOf<Contact>()
    ctx.contentResolver.query(
        Phone.CONTENT_URI, arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER),
        null, null, "${Phone.DISPLAY_NAME} COLLATE NOCASE ASC"
    )?.use { c ->
        while (c.moveToNext()) {
            val name = c.getString(0) ?: continue
            val num = c.getString(1) ?: continue
            out += Contact(name, num)
        }
    }
    out.distinctBy { it.name to it.number.key() }
}.getOrDefault(emptyList())

fun loadRecents(ctx: Context): List<Recent> = runCatching {
    val out = mutableListOf<Recent>()
    ctx.contentResolver.query(
        CallLog.Calls.CONTENT_URI,
        arrayOf(CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE),
        null, null, "${CallLog.Calls.DATE} DESC"
    )?.use { c ->
        while (c.moveToNext() && out.size < 500) {
            out += Recent(c.getString(0)?.takeIf { it.isNotBlank() }, c.getString(1) ?: "", c.getInt(2), c.getLong(3))
        }
    }
    out.grouped()
}.getOrDefault(emptyList())

/** MIUI style: back-to-back calls with the same number & type collapse into one row "(3)". */
private fun List<Recent>.grouped(): List<Recent> {
    val acc = mutableListOf<Recent>()
    for (r in this) {
        val last = acc.lastOrNull()
        if (last != null && last.number.key() == r.number.key() && last.type == r.type) {
            acc[acc.lastIndex] = last.copy(count = last.count + 1)
        } else acc += r
    }
    return acc
}

fun lookupName(ctx: Context, number: String): String? = runCatching {
    if (number.isBlank()) return null
    val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
    ctx.contentResolver.query(uri, arrayOf(PhoneLookup.DISPLAY_NAME), null, null, null)
        ?.use { if (it.moveToFirst()) it.getString(0) else null }
}.getOrNull()

// ---------- T9 search (type 7-2-6 to find "Ram") ----------

private val T9: Map<Char, Char> = buildMap {
    listOf("2abc", "3def", "4ghi", "5jkl", "6mno", "7pqrs", "8tuv", "9wxyz")
        .forEach { s -> s.drop(1).forEach { put(it, s[0]) } }
}

private fun String.toT9() = lowercase().map { T9[it] ?: if (it.isDigit()) it else ' ' }.joinToString("")

fun t9Match(query: String, name: String?, number: String): Boolean {
    if (query.isEmpty()) return true
    if (number.digits().contains(query)) return true
    if (name == null) return false
    val t9 = name.toT9()
    val words = t9.split(' ').filter { it.isNotEmpty() }
    return words.any { it.startsWith(query) } ||
        t9.replace(" ", "").startsWith(query) ||
        words.joinToString("") { it.take(1) }.startsWith(query) // initials
}

// ---------- SIM & placing calls ----------

@SuppressLint("MissingPermission")
fun simAccounts(ctx: Context): List<PhoneAccountHandle> =
    if (granted(ctx, READ_PHONE_STATE)) telecom(ctx).callCapablePhoneAccounts else emptyList()

@SuppressLint("MissingPermission")
fun defaultSim(ctx: Context): PhoneAccountHandle? =
    if (granted(ctx, READ_PHONE_STATE)) telecom(ctx).getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL) else null

fun simLabel(ctx: Context, h: PhoneAccountHandle): String =
    telecom(ctx).getPhoneAccount(h)?.label?.toString() ?: "SIM"

@SuppressLint("MissingPermission")
fun placeCall(ctx: Context, number: String, sim: PhoneAccountHandle?) {
    if (number.isBlank()) return
    if (!granted(ctx, CALL_PHONE)) {
        Toast.makeText(ctx, "Allow Phone permission to make calls", Toast.LENGTH_SHORT).show()
        return
    }
    val extras = Bundle().apply {
        sim?.let { putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, it) }
    }
    telecom(ctx).placeCall(Uri.fromParts("tel", number, null), extras)
}
