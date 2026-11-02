package com.richie.whattowear.model

import java.time.LocalDate

/** The clothes a quick log starts with, and whether they came from a log made earlier today. */
data class QuickClothes(val itemIds: List<String>, val fromTodaysLog: Boolean)

/**
 * If you've already logged today you're probably still wearing that outfit, so start from it.
 * Otherwise start from today's suggestion.
 */
fun quickLogClothes(entries: List<Entry>, today: LocalDate, suggestion: List<String>): QuickClothes {
    val latestToday = entries.filter { it.date == today }.maxByOrNull { it.whenLabel }
    return if (latestToday != null) QuickClothes(latestToday.itemIds, true) else QuickClothes(suggestion, false)
}
