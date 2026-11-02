package com.richie.whattowear

import com.richie.whattowear.model.Entry
import com.richie.whattowear.model.Feeling
import com.richie.whattowear.model.Recommender
import com.richie.whattowear.model.Slot
import com.richie.whattowear.model.Wardrobe
import com.richie.whattowear.model.WeatherSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Rules that must hold for every suggestion, checked across a wide range of weather. */
class OutfitRulesTest {
    private val today = LocalDate.of(2026, 11, 2)
    private val temps = (-8..32 step 2).map { it.toDouble() }

    private fun weather(t: Double, rain: Boolean = false, sun: Boolean = false) = WeatherSummary(
        t, t, t, t, if (rain) 80 else 0, if (rain) 3.0 else 0.0, 10.0, if (sun) 6.0 else 1.0,
        if (sun) 1.0 else 0.0, if (rain) 61 else if (sun) 0 else 3,
    )

    // A spread of made-up logs, so the rules are checked with learning switched on as well as off.
    private val someLogs = (0 until 40).map { i ->
        val t = -4.0 + i
        Entry(
            key = "log:$i", date = today.minusDays(i.toLong()), whenLabel = "all day", place = "Testville",
            realTempC = t + 2, dressTempC = t, weather = "Cloudy",
            itemIds = if (t < 8) listOf("shirt", "gilet", "warm_coat", "trousers", "gloves") else listOf("tshirt", "trousers"),
            feeling = Feeling.entries[i % 5],
        )
    }
    private val recommenders = listOf(Recommender(emptyList(), today), Recommender(someLogs, today))

    @Test
    fun everyOutfitIsSomethingYouCouldPutOn() {
        for (r in recommenders) for (t in temps) for (rain in listOf(false, true)) {
            val items = r.suggest(weather(t, rain)).items
            val label = "$t°C rain=$rain: ${items.map { it.id }}"
            assertEquals(label, 1, items.count { it.slot == Slot.TOP })
            assertTrue(label, items.count { it.slot == Slot.LAYER } <= 1)
            assertTrue(label, items.count { it.slot == Slot.JACKET } <= 1)
            assertEquals(label, 1, items.count { it.slot == Slot.BOTTOM && !it.wornUnder })
            assertTrue(label, items.count { it.wornUnder } <= 1)
        }
    }

    @Test
    fun rainGearComesOutWhenItRainsAndNotWhenItDoesnt() {
        val r = Recommender(emptyList(), today)
        for (t in temps) {
            assertTrue("$t°C wet", r.suggest(weather(t, rain = true)).items.any { it.keepsRainOff })
            assertTrue("$t°C dry", r.suggest(weather(t)).items.none { it.keepsRainOff })
        }
    }

    @Test
    fun sunGearOnlyOnSunnyDays() {
        for (r in recommenders) for (t in temps) {
            assertTrue("$t°C cloudy", r.suggest(weather(t)).items.none { it.forSun })
        }
        assertTrue(Recommender(emptyList(), today).suggest(weather(20.0, sun = true)).items.any { it.id == "sunglasses" })
    }

    @Test
    fun hatsScarvesAndGlovesOnlyWhenItsColdEnough() {
        for (r in recommenders) for (t in temps) {
            val extras = r.suggest(weather(t)).items.filter { it.slot == Slot.EXTRA && it.warmth > 0 }
            assertTrue("$t°C: $extras", extras.all { r.allowedAt(it, t) })
        }
    }

    @Test
    fun colderNeverMeansLessWarmth() {
        for (r in recommenders) {
            val needs = (-10..35).map { r.warmthNeeded(it.toDouble()) }
            assertTrue(needs.toString(), needs.zipWithNext().all { (colder, warmer) -> colder >= warmer })
        }
    }

    @Test
    fun theSameWeatherAlwaysGetsTheSameAnswer() {
        val r = Recommender(someLogs, today)
        assertEquals(r.suggest(weather(9.0)).items, Recommender(someLogs, today).suggest(weather(9.0)).items)
    }

    @Test
    fun staysQuickWithYearsOfLogs() {
        val lots = (0 until 2000).map { someLogs[it % someLogs.size].copy(key = "log:$it", date = today.minusDays(it.toLong())) }
        val started = System.nanoTime()
        Recommender(lots, today).suggest(weather(11.0, rain = true))
        val ms = (System.nanoTime() - started) / 1_000_000
        assertTrue("took $ms ms", ms < 2000)
    }

    @Test
    fun theClothesListHasNoDuplicateNamesOrIds() {
        assertEquals(Wardrobe.items.size, Wardrobe.items.map { it.id }.toSet().size)
        assertEquals(Wardrobe.items.size, Wardrobe.items.map { it.name.lowercase() }.toSet().size)
    }
}
