package com.richie.whattowear

import com.richie.whattowear.data.WeatherClient
import com.richie.whattowear.model.Recommender
import com.richie.whattowear.model.TimeOfDay
import com.richie.whattowear.model.words
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Real replies from Open-Meteo, saved in src/test/resources: Manchester over 24–26 October 2026,
 * the weekend the UK clocks went back. The expected numbers were worked out separately in Python.
 */
class WeatherParsingTest {
    private fun saved(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()
    private val forecast = WeatherClient.parseForecast(saved("forecast-manchester-clock-change.json"))
    private val oct24 = LocalDate.of(2026, 10, 24)
    private val oct25 = LocalDate.of(2026, 10, 25)

    @Test
    fun readsEveryHourOnThePlacesOwnClock() {
        assertEquals(ZoneId.of("Europe/London"), forecast.zone)
        assertEquals(72, forecast.hours.size)
        // Open-Meteo uses one fixed offset for a whole reply, so it starts at 1am, not midnight.
        // That's why the app always asks for the day before and after.
        assertEquals(LocalTime.of(1, 0), forecast.hours.first().time.atZone(forecast.zone).toLocalTime())
        // The day the clocks went back is 25 hours long.
        assertEquals(25, forecast.hours.count { it.time.atZone(forecast.zone).toLocalDate() == oct25 })
    }

    @Test
    fun anAllDaySummaryMatchesTheIndependentWorkingOut() {
        val s = forecast.summarise(oct24, TimeOfDay.ALL_DAY)!!
        assertEquals(6.0, s.feelsMeanC, 0.001)
        assertEquals(4.2, s.feelsMinC, 0.001)
        assertEquals(9.2, s.feelsMaxC, 0.001)
        assertEquals(100, s.rainChanceMax)
        assertEquals(14.5, s.rainTotalMm, 0.001)
        assertEquals(27.7, s.windMaxKmh, 0.001)
        assertTrue(s.rainy)
    }

    @Test
    fun theDayTheClocksWentBack() {
        val day = forecast.summarise(oct25, TimeOfDay.ALL_DAY)!!
        assertEquals(4.7, day.feelsMeanC, 0.001)
        assertEquals(3.8, day.feelsMinC, 0.001)
        assertEquals(6.2, day.feelsMaxC, 0.001)
        assertEquals(37.1, day.windMaxKmh, 0.001)
        val evening = forecast.summarise(oct25, TimeOfDay.EVENING)!!
        assertEquals(5.3, evening.feelsMinC, 0.001)
        assertEquals(6.2, evening.feelsMaxC, 0.001)
        assertEquals(0.0, evening.dayShare, 0.001)
        assertTrue(evening.words(), evening.words().endsWith("night"))
    }

    @Test
    fun aRealWetDayGetsRainCover() {
        val s = forecast.summarise(oct24, TimeOfDay.ALL_DAY)!!
        assertTrue(Recommender(emptyList(), oct24).suggest(s).items.any { it.keepsRainOff })
    }

    @Test
    fun readsPlaceSearchResults() {
        val places = WeatherClient.parsePlaces(saved("places-manchester.json"))
        assertEquals(8, places.size)
        assertEquals("Manchester", places.first().name)
        assertEquals("England, United Kingdom", places.first().detail)
        assertEquals(53.48, places.first().latitude, 0.01)
    }

    @Test
    fun copesWithGapsInAReply() {
        // Hours with no feels-like are skipped; other missing values get safe defaults.
        val reply = """{"timezone":"Europe/London","hourly":{"time":[0,3600,7200],
            "apparent_temperature":[5.0,null,7.0],"temperature_2m":[6.0,6.5,null]}}"""
        val f = WeatherClient.parseForecast(reply)
        assertEquals(2, f.hours.size)
        assertEquals(0, f.hours[0].rainChance)
        assertEquals(7.0, f.hours[1].tempC, 0.001) // no air temperature, so it uses feels-like
    }

    @Test
    fun anUnknownTimeZoneFallsBackToThePhones() {
        val f = WeatherClient.parseForecast("""{"timezone":"Not/AZone","hourly":{"time":[0],"apparent_temperature":[5.0]}}""")
        assertEquals(ZoneId.systemDefault(), f.zone)
    }

    @Test
    fun noPlacesFoundIsAnEmptyList() {
        assertTrue(WeatherClient.parsePlaces("""{"generationtime_ms":0.5}""").isEmpty())
    }
}
