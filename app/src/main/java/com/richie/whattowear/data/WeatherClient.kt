package com.richie.whattowear.data

import com.richie.whattowear.model.HourWeather
import com.richie.whattowear.model.HourlyForecast
import com.richie.whattowear.model.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Talks to Open-Meteo, which is free and needs no API key. */
object WeatherClient {
    private val json = Json { ignoreUnknownKeys = true }

    private const val HOURLY = "temperature_2m,apparent_temperature,precipitation_probability,precipitation," +
        "wind_speed_10m,uv_index,weather_code,is_day"

    /** Today and tomorrow, hour by hour. */
    suspend fun forecast(lat: Double, lon: Double): HourlyForecast = fetch(lat, lon, "forecast_days=2")

    /** The days either side of [date] too, so evenings still work when the clocks change. Goes back about 3 months. */
    suspend fun forDate(lat: Double, lon: Double, date: LocalDate): HourlyForecast =
        fetch(lat, lon, "start_date=${date.minusDays(1)}&end_date=${date.plusDays(1)}")

    suspend fun searchPlaces(query: String): List<Place> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val body = get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=8&language=en&format=json")
        json.decodeFromString<GeoResponse>(body).results.map { r ->
            Place(r.name, r.latitude, r.longitude, listOfNotNull(r.admin1, r.country).distinct().joinToString(", "))
        }
    }

    private suspend fun fetch(lat: Double, lon: Double, range: String): HourlyForecast = withContext(Dispatchers.IO) {
        // Times come back as Unix timestamps and are converted with the place's real time zone.
        // Open-Meteo's own local times use one fixed offset for the whole response, so they go an
        // hour wrong after the clocks change.
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&hourly=$HOURLY" +
            "&timezone=auto&timeformat=unixtime&$range"
        val response = json.decodeFromString<ForecastResponse>(get(url))
        val zone = runCatching { ZoneId.of(response.timezone) }.getOrDefault(ZoneId.systemDefault())
        val h = response.hourly
        val hours = h.time.indices.mapNotNull { i ->
            val feels = h.apparentTemperature.getOrNull(i) ?: return@mapNotNull null
            HourWeather(
                time = Instant.ofEpochSecond(h.time[i]),
                tempC = h.temperature.getOrNull(i) ?: feels,
                feelsC = feels,
                rainChance = h.precipitationProbability.getOrNull(i) ?: 0,
                rainMm = h.precipitation.getOrNull(i) ?: 0.0,
                windKmh = h.windSpeed.getOrNull(i) ?: 0.0,
                uv = h.uvIndex.getOrNull(i) ?: 0.0,
                code = h.weatherCode.getOrNull(i) ?: 3,
                isDay = (h.isDay.getOrNull(i) ?: 1) == 1,
            )
        }
        HourlyForecast(zone, hours)
    }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IOException("Weather service said $code: $detail")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    @Serializable
    private data class ForecastResponse(val timezone: String = "GMT", val hourly: Hourly)

    @Serializable
    private data class Hourly(
        val time: List<Long> = emptyList(),
        @SerialName("temperature_2m") val temperature: List<Double?> = emptyList(),
        @SerialName("apparent_temperature") val apparentTemperature: List<Double?> = emptyList(),
        @SerialName("precipitation_probability") val precipitationProbability: List<Int?> = emptyList(),
        val precipitation: List<Double?> = emptyList(),
        @SerialName("wind_speed_10m") val windSpeed: List<Double?> = emptyList(),
        @SerialName("uv_index") val uvIndex: List<Double?> = emptyList(),
        @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
        @SerialName("is_day") val isDay: List<Int?> = emptyList(),
    )

    @Serializable
    private data class GeoResponse(val results: List<GeoResult> = emptyList())

    @Serializable
    private data class GeoResult(
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val admin1: String? = null,
        val country: String? = null,
    )
}
