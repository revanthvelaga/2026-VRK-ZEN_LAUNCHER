package com.zenfold.launcher.feeds

data class GoldPrice(val pricePerGram: Double, val currency: String)

data class MarketIndex(val name: String, val value: Double, val changePercent: Double)

/** One side of a scoreboard: "India" → "162/4 (17.2)", or null before they've batted. */
data class TeamScore(val team: String, val score: String?)

data class CricketMatch(
    val name: String,
    val matchType: String,
    val status: String,
    val live: Boolean,
    val teams: List<TeamScore>
)
