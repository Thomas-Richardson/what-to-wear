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
import java.time.LocalDateTime

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
        assertEquals(Duration.ofHours(8), Reminders.delayUntil(LocalDateTime.of(2026, 11, 2, 9, 0), 17))
        // After 5pm it waits for tomorrow's 5pm.
        assertEquals(Duration.ofHours(23), Reminders.delayUntil(LocalDateTime.of(2026, 11, 2, 18, 0), 17))
        assertEquals(Duration.ofHours(24), Reminders.delayUntil(LocalDateTime.of(2026, 11, 2, 17, 0), 17))
    }
}
