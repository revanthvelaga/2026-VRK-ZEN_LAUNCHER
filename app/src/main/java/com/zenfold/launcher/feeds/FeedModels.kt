package com.zenfold.launcher.feeds

data class GoldPrice(val pricePerGram: Double, val currency: String)

data class MarketIndex(val name: String, val value: Double, val changePercent: Double)

data class CricketMatch(val name: String, val status: String)
