package com.richie.whattowear

import com.richie.whattowear.model.Entry
import com.richie.whattowear.model.Feeling
import com.richie.whattowear.model.quickLogClothes
import com.richie.whattowear.reminder.Reminders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class QuickLogTest {
    private val today = LocalDate.of(2026, 11, 2)
    private fun entry(date: LocalDate?, time: String, vararg items: String) = Entry(
        key = "$date $time", date = date, whenLabel = time, place = "", realTempC = 12.0, dressTempC = 10.0,
        weather = "", itemIds = items.toList(), feeling = Feeling.JUST_RIGHT,
    )

    @Test
    fun startsFromTheSuggestionWhenNothingIsLoggedToday() {
        val q = quickLogClothes(listOf(entry(today.minusDays(1), "09:00", "shirt")), today, listOf("tshirt", "gilet"))
        assertEquals(listOf("tshirt", "gilet"), q.itemIds)
        assertFalse(q.fromTodaysLog)
    }

    @Test
    fun startsFromTodaysLatestLogBecauseYoureProbablyStillWearingIt() {
        val logs = listOf(entry(today, "08:15", "shirt", "blazer"), entry(today, "13:40", "shirt", "gilet"), entry(null, "", "hoodie"))
        val q = quickLogClothes(logs, today, listOf("tshirt"))
        assertEquals(listOf("shirt", "gilet"), q.itemIds)
        assertTrue(q.fromTodaysLog)
    }

    @Test
    fun reminderWaitsUntilFivePm() {
        val zone = ZoneId.of("America/Los_Angeles")
        fun at(y: Int, mo: Int, d: Int, h: Int) = ZonedDateTime.of(y, mo, d, h, 0, 0, 0, zone)
        assertEquals(Duration.ofHours(8), Reminders.delayUntil(at(2026, 11, 2, 9), 17))
        // After 5pm it waits for tomorrow's 5pm.
        assertEquals(Duration.ofHours(23), Reminders.delayUntil(at(2026, 11, 2, 18), 17))
        assertEquals(Duration.ofHours(24), Reminders.delayUntil(at(2026, 11, 2, 17), 17))
    }

    @Test
    fun reminderStillLandsOn5pmWhenTheClocksChange() {
        val zone = ZoneId.of("America/Los_Angeles")
        // Clocks went back at 2am on 1 Nov 2026: 6pm the evening before to 5pm next day is 24 real hours, not 23.
        val back = ZonedDateTime.of(2026, 10, 31, 18, 0, 0, 0, zone)
        assertEquals(Duration.ofHours(24), Reminders.delayUntil(back, 17))
        assertEquals(17, back.plus(Reminders.delayUntil(back, 17)).hour)
        // Clocks go forward on 14 Mar 2027: only 22 real hours.
        val forward = ZonedDateTime.of(2027, 3, 13, 18, 0, 0, 0, zone)
        assertEquals(Duration.ofHours(22), Reminders.delayUntil(forward, 17))
        assertEquals(17, forward.plus(Reminders.delayUntil(forward, 17)).hour)
    }

    @Test
    fun aReminderThatDriftedAnHourIsSpotted() {
        val zone = ZoneId.of("America/Los_Angeles")
        fun millis(h: Int) = ZonedDateTime.of(2026, 11, 3, h, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertTrue(Reminders.onTime(millis(17), zone))
        assertFalse(Reminders.onTime(millis(16), zone)) // a 24-hour repeat after the clocks went back
    }
}
