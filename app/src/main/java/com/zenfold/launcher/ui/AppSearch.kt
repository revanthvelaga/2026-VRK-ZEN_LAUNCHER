package com.zenfold.launcher.ui

import java.text.Normalizer
import java.util.Locale

/** Device-local matching; queries never leave the launcher. */
internal object AppSearch {
    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("(?<=\\p{IsLatin})\\p{M}+"), "")
        .lowercase(Locale.ROOT).trim()

    /** Lower scores rank first. Null means no match. Supports initials and multiple words. */
    fun score(label: String, query: String): Int? {
        val name = normalize(label)
        val q = normalize(query)
        if (q.isEmpty()) return null
        // Marks stay inside words: in scripts like Telugu or Hindi a vowel sign is part of the
        // word, not a break (Latin accents were already stripped by normalize).
        val words = name.split(Regex("[^\\p{L}\\p{M}\\p{N}]+")).filter { it.isNotEmpty() }
        return when {
            name == q -> 0
            name.startsWith(q) -> 1
            words.any { it.startsWith(q) } -> 2
            words.size > 1 && words.joinToString("") { it.take(1) }.startsWith(q) -> 3
            name.contains(q) -> 4
            q.split(Regex("\\s+")).all { token -> words.any { it.startsWith(token) } } -> 5
            else -> null
        }
    }
}
