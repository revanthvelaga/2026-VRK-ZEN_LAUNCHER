package com.zenfold.launcher.feeds

/** International gold price converted to rupees per gram (excludes Indian import duty/GST). */
data class GoldPrice(val pricePerGram: Double, val changePercent: Double)

data class MarketIndex(val name: String, val value: Double, val changePercent: Double)

/** One side of a scoreboard: "India" → "161/1", batting now or not, null score before they bat. */
data class TeamScore(val team: String, val score: String?, val batting: Boolean)

data class CricketMatch(val teams: List<TeamScore>, val live: Boolean, val url: String?)

/** A Google Trends trending search, with the headline that explains it when there is one. */
data class Trend(val title: String, val traffic: String?, val headline: String?, val url: String)
