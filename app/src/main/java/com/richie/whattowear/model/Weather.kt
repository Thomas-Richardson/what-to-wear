package com.richie.whattowear.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** Weather for one hour at one place. */
data class HourWeather(
    val time: Instant,
    val tempC: Double,
    val feelsC: Double,
    val rainChance: Int,
    val rainMm: Double,
    val windKmh: Double,
    val uv: Double,
    val code: Int,
    val isDay: Boolean,
)

/** Hourly weather for a place, plus the place's time zone so "morning" means morning there. */
data class HourlyForecast(val zone: ZoneId, val hours: List<HourWeather>) {

    fun summarise(from: Instant, to: Instant): WeatherSummary? {
        val slice = hours.filter { !it.time.isBefore(from) && it.time.isBefore(to) }
        if (slice.isEmpty()) return null
        val rainChanceMax = slice.maxOf { it.rainChance }
        val rainTotal = slice.sumOf { it.rainMm }
        val daylight = slice.filter { it.isDay }
        val rainy = WeatherSummary.isRainy(rainChanceMax, rainTotal)
        // Show the worst weather if it's wet, otherwise the most common.
        val code = if (rainy) slice.maxOf { it.code }
        else slice.groupingBy { it.code }.eachCount().maxBy { it.value }.key
        return WeatherSummary(
            feelsMeanC = slice.map { it.feelsC }.average().round1(),
            feelsMinC = slice.minOf { it.feelsC },
            feelsMaxC = slice.maxOf { it.feelsC },
            tempMeanC = slice.map { it.tempC }.average().round1(),
            rainChanceMax = rainChanceMax,
            rainTotalMm = rainTotal.round1(),
            windMaxKmh = slice.maxOf { it.windKmh },
            uvMax = slice.maxOf { it.uv },
            sunnyShare = if (daylight.isEmpty()) 0.0
            else (daylight.count { it.code <= 1 }.toDouble() / daylight.size).round1(),
            weatherCode = code,
            dayShare = (daylight.size.toDouble() / slice.size).round1(),
        )
    }

    /**
     * Summarise part of a day, using the place's own clock (this copes with the clocks changing).
     * "Now" is the last hour and this one, because that's the weather you've just been out in.
     */
    fun summarise(date: LocalDate, part: TimeOfDay, now: Instant = Instant.now()): WeatherSummary? {
        if (part == TimeOfDay.NOW) {
            val thisHour = now.truncatedTo(ChronoUnit.HOURS)
            return summarise(thisHour.minus(1, ChronoUnit.HOURS), thisHour.plus(1, ChronoUnit.HOURS))
        }
        val from = date.atTime(part.startHour, 0).atZone(zone).toInstant()
        val to = if (part.endHour >= 24) date.plusDays(1).atStartOfDay(zone).toInstant()
        else date.atTime(part.endHour, 0).atZone(zone).toInstant()
        return summarise(from, to)
    }
}

/** The weather over the time you're out, boiled down to what matters for clothes. */
@Serializable
data class WeatherSummary(
    val feelsMeanC: Double,
    val feelsMinC: Double,
    val feelsMaxC: Double,
    val tempMeanC: Double,
    val rainChanceMax: Int,
    val rainTotalMm: Double,
    val windMaxKmh: Double,
    val uvMax: Double,
    /** Share of the daylight hours that are clear or mostly clear. */
    val sunnyShare: Double,
    val weatherCode: Int,
    /** Share of the hours that are in daylight. Older logs don't have it, so they count as daytime. */
    val dayShare: Double = 1.0,
) {
    /** The temperature the app dresses you for: the average feels-like, pulled towards the coldest hour. */
    val dressTempC: Double get() = 0.6 * feelsMeanC + 0.4 * feelsMinC
    val rainy: Boolean get() = isRainy(rainChanceMax, rainTotalMm)
    val sunny: Boolean get() = !rainy && sunnyShare >= 0.5 && uvMax >= 3.0

    companion object {
        fun isRainy(rainChanceMax: Int, rainTotalMm: Double) = rainChanceMax >= 50 || rainTotalMm >= 1.0

        /** A plain dry, cloudy spell at one temperature. Used to show what the app has learnt. */
        fun steady(feelsC: Double) = WeatherSummary(feelsC, feelsC, feelsC, feelsC, 0, 0.0, 10.0, 1.0, 0.0, 3)
    }
}

/** Parts of the day you can log. Hours are on the place's own clock. */
@Serializable
enum class TimeOfDay(val label: String, val startHour: Int, val endHour: Int) {
    /** The moment you log it (today only). Its hours are worked out from the clock, not fixed. */
    NOW("Now", -1, -1),
    MORNING("Morning", 6, 12),
    AFTERNOON("Afternoon", 12, 18),
    EVENING("Evening", 18, 24),
    ALL_DAY("All day", 8, 22),
}

object WeatherCodes {
    /** Emoji and words for the World Meteorological Organization weather codes Open-Meteo uses. */
    fun describe(code: Int, night: Boolean = false): Pair<String, String> = when (code) {
        0 -> (if (night) "🌙" else "☀️") to "Clear"
        1 -> (if (night) "🌙" else "🌤️") to "Mostly clear"
        2 -> "⛅" to "Partly cloudy"
        3 -> "☁️" to "Cloudy"
        45, 48 -> "🌫️" to "Fog"
        in 51..57 -> "🌦️" to "Drizzle"
        in 61..67 -> "🌧️" to "Rain"
        in 71..77 -> "❄️" to "Snow"
        in 80..82 -> "🌦️" to "Showers"
        85, 86 -> "🌨️" to "Snow showers"
        in 95..99 -> "⛈️" to "Thunderstorms"
        else -> "🌡️" to "Mixed"
    }
}

/**
 * Words for the note's Weather column, e.g. "Partly cloudy, rain likely", "Clear, sunny" or "Clear night".
 * They're written so that reading them back gives the same rain and sun: "sunny" appears only on a
 * sunny day (a clear sky with weak winter sun isn't), and a wet-sounding sky that barely rained says "mostly dry".
 */
fun WeatherSummary.words(): String {
    val base = WeatherCodes.describe(weatherCode).second
    val wet = Regex("rain|drizzle|shower|thunder|snow", RegexOption.IGNORE_CASE).containsMatchIn(base)
    return buildString {
        append(base)
        if (dayShare < 0.5 && !sunny) append(" night")
        if (rainy && !wet) append(", rain likely")
        if (!rainy && wet) append(", mostly dry")
        if (sunny) append(", sunny")
    }
}

fun deg(c: Double) = "${c.roundToInt()}°C"

fun degRange(low: Double, high: Double) =
    if (low.roundToInt() == high.roundToInt()) deg(low) else "${low.roundToInt()}–${deg(high)}"

private fun Double.round1() = (this * 10).roundToInt() / 10.0
