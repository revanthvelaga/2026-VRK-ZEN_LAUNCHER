package com.zenfold.launcher.feeds

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

data class FeedApiKeys(
    val goldApiKey: String = "",
    val marketApiKey: String = "",
    val cricketApiKey: String = ""
)

private val Context.feedPrefs by preferencesDataStore(name = "feeds_prefs")
private val GOLD_KEY = stringPreferencesKey("gold_api_key")
private val MARKET_KEY = stringPreferencesKey("market_api_key")
private val CRICKET_KEY = stringPreferencesKey("cricket_api_key")

// Keys live only in this app-private DataStore file; each one is sent to nowhere but its
// own provider's endpoint (goldapi.io / twelvedata.com / cricapi.com) — see feeds/FeedApi.kt.
class FeedPreferences(private val context: Context) {

    val apiKeys = context.feedPrefs.data.map {
        FeedApiKeys(
            goldApiKey = it[GOLD_KEY] ?: "",
            marketApiKey = it[MARKET_KEY] ?: "",
            cricketApiKey = it[CRICKET_KEY] ?: ""
        )
    }

    suspend fun setGoldApiKey(key: String) {
        context.feedPrefs.edit { it[GOLD_KEY] = key }
    }

    suspend fun setMarketApiKey(key: String) {
        context.feedPrefs.edit { it[MARKET_KEY] = key }
    }

    suspend fun setCricketApiKey(key: String) {
        context.feedPrefs.edit { it[CRICKET_KEY] = key }
    }
}
