package com.richie.whattowear.model

import java.time.LocalDate

/**
 * One log the app learns from: a row of the temperatures note, or (before the note is connected)
 * a log kept inside the app. Rows from before the app have no date or place.
 */
data class Entry(
    /** How to find it again to delete it: the note's line, or "log:<id>" for a log kept in the app. */
    val key: String,
    val date: LocalDate?,
    /** "14:35", "all day", "morning"… or blank. */
    val whenLabel: String,
    val place: String,
    val realTempC: Double?,
    /** The feels-like temperature the app dresses for. */
    val dressTempC: Double,
    /** Words like "Clear", "Rain" or "Partly cloudy, rain likely". Blank for rows from before the app. */
    val weather: String,
    val itemIds: List<String>,
    val feeling: Feeling,
    /** His own words for what he wore (or the app's names, for rows the app wrote). */
    val wore: String = "",
    /** His own words for how it felt. */
    val felt: String = "",
    /** Clothes the app counts, when his words aren't the app's names. Blank if not needed. */
    val countedAs: String = "",
    /** Names in the clothes text that the app doesn't recognise, so they weren't counted. */
    val unknownClothes: List<String> = emptyList(),
) {
    val items: List<ClothingItem> get() = itemIds.mapNotNull(Wardrobe::byId)

    // Rows from before the app have no weather, so they count as dry and not sunny.
    val rainy: Boolean get() = Regex("rain|drizzle|shower|thunder|snow", RegexOption.IGNORE_CASE).containsMatchIn(weather)
    val sunny: Boolean get() = !rainy && !weather.contains("night", ignoreCase = true) &&
        (weather.contains("sunny", ignoreCase = true) || weather.startsWith("Clear", ignoreCase = true) ||
            weather.startsWith("Mostly clear", ignoreCase = true))
}
