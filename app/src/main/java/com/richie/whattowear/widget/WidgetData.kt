package com.richie.whattowear.widget

import android.content.Context
import com.richie.whattowear.Ahead
import com.richie.whattowear.data.Logs
import com.richie.whattowear.data.Settings
import com.richie.whattowear.data.WeatherClient
import com.richie.whattowear.model.Recommender
import com.richie.whattowear.model.Wardrobe
import com.richie.whattowear.model.WeatherCodes
import com.richie.whattowear.model.deg
import com.richie.whattowear.model.quickLogClothes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate

/** What the widget shows, saved so it can be drawn instantly without going online. */
data class WidgetState(
    val itemIds: List<String>,
    /** True if the clothes come from a log made earlier today rather than from the suggestion. */
    val fromTodaysLog: Boolean,
    val weatherLine: String,
    val date: String,
) {
    val isToday: Boolean get() = date == LocalDate.now().toString()
    val clothesLine: String get() = itemIds.mapNotNull { Wardrobe.byId(it)?.name }.joinToString(" · ")
}

object WidgetData {
    private fun prefs(context: Context) = context.getSharedPreferences("widget", Context.MODE_PRIVATE)

    fun read(context: Context): WidgetState {
        val p = prefs(context)
        return WidgetState(
            itemIds = p.getString("items", "").orEmpty().split(",").filter { it.isNotBlank() },
            fromTodaysLog = p.getBoolean("from_log", false),
            weatherLine = p.getString("weather", "").orEmpty(),
            date = p.getString("date", "").orEmpty(),
        )
    }

    /** Works out today's suggestion the same way the Today tab does, and saves it for the widget. */
    suspend fun refresh(context: Context) = withContext(Dispatchers.IO) {
        val place = Settings(context).place ?: return@withContext
        // If the note can't be read, still show a suggestion: the app's starter guess.
        val logs = runCatching { Logs.load(context).entries }.getOrDefault(emptyList())
        val forecast = WeatherClient.forecast(place.latitude, place.longitude)
        val (from, to) = Ahead.REST_OF_TODAY.range(Instant.now(), forecast.zone)
        val summary = forecast.summarise(from, to) ?: return@withContext
        val suggestion = Recommender(logs).suggest(summary)
        val quick = quickLogClothes(logs, LocalDate.now(), suggestion.items.map { it.id })
        val (emoji, _) = WeatherCodes.describe(summary.weatherCode, night = summary.dayShare < 0.5)
        prefs(context).edit()
            .putString("items", quick.itemIds.joinToString(","))
            .putBoolean("from_log", quick.fromTodaysLog)
            .putString("weather", "$emoji feels like ${deg(summary.dressTempC)} · ${place.name}")
            .putString("date", LocalDate.now().toString())
            .apply()
    }
}
