package com.richie.whattowear

import com.richie.whattowear.model.HourWeather
import com.richie.whattowear.model.HourlyForecast
import com.richie.whattowear.model.TimeOfDay
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class NowTest {
    @Test
    fun nowUsesTheLastHourAndThisOne() {
        // Each hour's feels-like equals its UTC hour, so the result shows which hours were used.
        val start = Instant.parse("2026-11-01T00:00:00Z")
        val hours = (0 until 48).map { i ->
            HourWeather(start.plus(i.toLong(), ChronoUnit.HOURS), 10.0, (i % 24).toDouble(), 0, 0.0, 5.0, 0.0, 3, true)
        }
        val forecast = HourlyForecast(ZoneId.of("America/Los_Angeles"), hours)
        val summary = forecast.summarise(LocalDate.of(2026, 11, 1), TimeOfDay.NOW, now = Instant.parse("2026-11-01T22:40:00Z"))!!
        // At 22:40 UTC that's the 21:00 and 22:00 hours.
        assertEquals(21.0, summary.feelsMinC, 0.001)
        assertEquals(22.0, summary.feelsMaxC, 0.001)
    }
}
