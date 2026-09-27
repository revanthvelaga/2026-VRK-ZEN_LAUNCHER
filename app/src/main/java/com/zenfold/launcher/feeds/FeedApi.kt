package com.zenfold.launcher.feeds

import android.util.Xml
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.StringReader
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

// Every source here is a public, keyless feed — nothing to sign up for or configure.
// All calls block: run them on Dispatchers.IO. Null means "couldn't fetch right now".

private val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
private const val USER_AGENT = "Mozilla/5.0 (Linux; Android) ZenFold"
private const val GRAMS_PER_TROY_OUNCE = 31.1034768

private fun get(url: String): String? = try {
    client.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).execute().use { response ->
        if (response.isSuccessful) response.body?.string() else null
    }
} catch (e: IOException) {
    null
}

private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8")

// ------------------------------------------------------------------ Markets

private class Quote(val price: Double, val changePercent: Double)

// Yahoo Finance's public chart endpoint (the one its own web charts use).
private fun quote(symbol: String): Quote? {
    val body = get("https://query1.finance.yahoo.com/v8/finance/chart/${encode(symbol)}?range=1d&interval=1d") ?: return null
    return try {
        val meta = JSONObject(body).getJSONObject("chart").getJSONArray("result").getJSONObject(0).getJSONObject("meta")
        val price = meta.getDouble("regularMarketPrice")
        val previous = meta.optDouble("chartPreviousClose", Double.NaN)
        val change = meta.optDouble("regularMarketChangePercent", Double.NaN).takeUnless { it.isNaN() }
            ?: if (previous.isNaN() || previous == 0.0) 0.0 else (price - previous) / previous * 100
        Quote(price, change)
    } catch (e: JSONException) {
        null
    }
}

/** COMEX gold (USD per troy ounce) × USD→INR, per gram. */
fun fetchGoldPrice(): GoldPrice? {
    val gold = quote("GC=F") ?: return null
    val usdInr = quote("INR=X") ?: return null
    return GoldPrice(pricePerGram = gold.price * usdInr.price / GRAMS_PER_TROY_OUNCE, changePercent = gold.changePercent)
}

fun fetchSensex(): MarketIndex? = quote("^BSESN")?.let { MarketIndex("Sensex", it.price, it.changePercent) }

// ------------------------------------------------------------------ Cricket

// ESPNcricinfo's public live-scores RSS: one item per match, titled like
// "Glamorgan 505/10 v Essex 351/6 & 129/10 *" — "*" marks the side batting now.
fun fetchCricketScores(): List<CricketMatch>? {
    val body = get("https://static.espncricinfo.com/rss/livescores.xml") ?: return null
    return parseRssItems(body)
        ?.mapNotNull { item -> item["title"]?.let { parseMatch(it, item["link"]) } }
        ?.sortedByDescending { it.live }
}

private val scoreToken = Regex("""^(\d+(/\d+)?d?|&|\*)$""")

private fun parseMatch(title: String, link: String?): CricketMatch? {
    val sides = title.split(" v ")
    if (sides.size != 2) return null
    val teams = sides.map(::parseSide)
    return CricketMatch(
        teams = teams,
        live = teams.any { it.batting },
        url = link?.trim()?.replaceFirst("http://", "https://")
    )
}

// The score is the run of score-like tokens at the end, so team names that contain
// numbers ("India Under-19s") stay intact.
private fun parseSide(side: String): TeamScore {
    val tokens = side.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    var start = tokens.size
    while (start > 0 && scoreToken.matches(tokens[start - 1])) start--
    val scoreTokens = tokens.drop(start)
    val score = scoreTokens.filterNot { it == "*" }.joinToString(" ").trim().takeIf { it.isNotEmpty() }
    val team = tokens.take(start).joinToString(" ").ifEmpty { side.trim() }
    return TeamScore(team, score, batting = "*" in scoreTokens)
}

// ------------------------------------------------------------------ Trending

/** Google Trends' public "trending now" RSS for a country (ISO code, e.g. "IN"). */
fun fetchTrends(country: String): List<Trend>? {
    val body = get("https://trends.google.com/trending/rss?geo=${encode(country)}") ?: return null
    return parseRssItems(body)
        ?.mapNotNull { item ->
            val title = item["title"]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Trend(
                title = title,
                traffic = item["ht:approx_traffic"],
                headline = item["ht:news_item_title"],
                url = item["ht:news_item_url"] ?: "https://www.google.com/search?q=${encode(title)}"
            )
        }
        ?.take(10)
}

// ------------------------------------------------------------------ RSS

// Each <item>'s child elements by tag name (first occurrence wins; namespaced tags keep
// their prefix, e.g. "ht:approx_traffic"). Null if the feed isn't valid XML.
private fun parseRssItems(xml: String): List<Map<String, String>>? {
    val items = mutableListOf<Map<String, String>>()
    var item: MutableMap<String, String>? = null
    var tag: String? = null
    return try {
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    tag = parser.name
                    if (parser.name == "item") item = mutableMapOf()
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim().orEmpty()
                    val key = tag
                    if (text.isNotEmpty() && key != null) item?.putIfAbsent(key, text)
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "item") {
                        item?.let { items.add(it) }
                        item = null
                    }
                    tag = null
                }
            }
            event = parser.next()
        }
        items
    } catch (e: XmlPullParserException) {
        null
    } catch (e: IOException) {
        null
    }
}
