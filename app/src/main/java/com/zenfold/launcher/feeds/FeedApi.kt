package com.zenfold.launcher.feeds

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

private val client = OkHttpClient()

private fun get(url: String, headers: Map<String, String> = emptyMap()): String? {
    val builder = Request.Builder().url(url)
    headers.forEach { (key, value) -> builder.addHeader(key, value) }
    return try {
        client.newCall(builder.build()).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    } catch (e: IOException) {
        null
    }
}

// goldapi.io: free-tier key, per-gram 24k price. Blocking call — always run on Dispatchers.IO.
fun fetchGoldPrice(apiKey: String): GoldPrice? {
    val body = get(
        "https://www.goldapi.io/api/XAU/INR",
        mapOf("x-access-token" to apiKey, "Content-Type" to "application/json")
    ) ?: return null
    return try {
        val json = JSONObject(body)
        GoldPrice(pricePerGram = json.getDouble("price_gram_24k"), currency = "INR")
    } catch (e: Exception) {
        null
    }
}

// Twelve Data's quote endpoint. Free-tier key from twelvedata.com — the exact symbol
// their index coverage expects can shift between plans; adjust here if "SENSEX" 404s
// for your key (their symbol-search endpoint shows the right one).
fun fetchSensex(apiKey: String): MarketIndex? {
    val body = get("https://api.twelvedata.com/quote?symbol=SENSEX&apikey=$apiKey") ?: return null
    return try {
        val json = JSONObject(body)
        if (json.has("code")) return null // Twelve Data reports errors in-body, not via HTTP status
        MarketIndex(
            name = json.optString("name", "Sensex"),
            value = json.getString("close").toDouble(),
            changePercent = json.getString("percent_change").toDouble()
        )
    } catch (e: Exception) {
        null
    }
}

// CricAPI free tier: whatever's currently live or most recent, capped to 3 for the card.
fun fetchCricketMatches(apiKey: String): List<CricketMatch>? {
    val body = get("https://api.cricapi.com/v1/currentMatches?apikey=$apiKey&offset=0") ?: return null
    return try {
        val json = JSONObject(body)
        val data = json.optJSONArray("data") ?: return emptyList()
        (0 until minOf(data.length(), 3)).map { i ->
            val match = data.getJSONObject(i)
            CricketMatch(
                name = match.optString("name", "Match"),
                status = match.optString("status", "")
            )
        }
    } catch (e: Exception) {
        null
    }
}
