package com.richie.whattowear

import com.richie.whattowear.model.Feeling
import com.richie.whattowear.model.HourWeather
import com.richie.whattowear.model.HourlyForecast
import com.richie.whattowear.model.Recommender
import com.richie.whattowear.model.TimeOfDay
import com.richie.whattowear.model.Wardrobe
import com.richie.whattowear.model.WearLog
import com.richie.whattowear.model.WeatherSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class RecommenderTest {
    private val today = LocalDate.of(2026, 10, 31)


    private fun weather(feelsC: Double, rain: Boolean = false) = WeatherSummary(
        feelsMeanC = feelsC, feelsMinC = feelsC, feelsMaxC = feelsC, tempMeanC = feelsC,
        rainChanceMax = if (rain) 80 else 0, rainTotalMm = if (rain) 3.0 else 0.0,
        windMaxKmh = 10.0, uvMax = 1.0, sunnyShare = 0.0, weatherCode = if (rain) 61 else 3,
    )

    private var nextId = 1L
    private fun log(daysAgo: Long, feelsC: Double, feeling: Feeling, vararg items: String) = WearLog(
        id = nextId++, date = today.minusDays(daysAgo).toString(), timeOfDay = TimeOfDay.ALL_DAY,
        placeName = "Testville", latitude = 0.0, longitude = 0.0,
        itemIds = items.toList(), feeling = feeling, weather = weather(feelsC),
    ).toEntry()

    private fun ids(r: Recommender, w: WeatherSummary) = r.suggest(w).items.map { it.id }

    @Test
    fun starterGuessGivesSensibleWarmthOnAMildDay() {
        val r = Recommender(emptyList(), today)
        val s = r.suggest(weather(14.0))
        val warmth = s.items.sumOf { it.warmth }
        assertEquals(r.warmthNeeded(14.0), warmth, 0.6)
        assertTrue(s.items.any { it.id == "tshirt" || it.id == "longsleeve" || it.id == "shirt" })
        assertTrue("trousers" in s.items.map { it.id })
        assertTrue(s.reason.startsWith("Starter guess"))
    }

    @Test
    fun rainAlwaysGetsSomethingToKeepItOff() {
        val r = Recommender(emptyList(), today)
        for (t in listOf(2.0, 12.0, 22.0)) {
            val items = r.suggest(weather(t, rain = true)).items
            assertTrue("no rain cover at $t°C: $items", items.any { it.keepsRainOff })
        }
    }

    @Test
    fun noRainGearOnADryDay() {
        val r = Recommender(emptyList(), today)
        for (t in listOf(2.0, 8.0, 12.0, 16.0, 22.0)) {
            val items = r.suggest(weather(t)).items
            assertTrue("rain gear on a dry $t°C day: $items", items.none { it.keepsRainOff })
        }
    }

    @Test
    fun coldDayGetsACoatAndHatOrGloves() {
        val ids = ids(Recommender(emptyList(), today), weather(1.0))
        assertTrue(ids.toString(), "warm_coat" in ids || "puffer" in ids)
        assertTrue(ids.toString(), ids.any { it in setOf("beanie", "scarf", "gloves") })
    }

    @Test
    fun hotDayGetsShortsAndTShirt() {
        val ids = ids(Recommender(emptyList(), today), weather(24.0))
        assertEquals(listOf("tshirt", "shorts"), ids)
    }

    @Test
    fun feelingTooColdMakesItSuggestMore() {
        val starter = Recommender(emptyList(), today).warmthNeeded(14.0)
        val logs = (1..4L).map { log(it, 14.0 + it * 0.1, Feeling.TOO_COLD, "tshirt", "hoodie", "trousers") }
        val learnt = Recommender(logs, today).warmthNeeded(14.0)
        assertTrue("starter $starter, learnt $learnt", learnt > starter + 0.3)
    }

    @Test
    fun wayTooColdPushesHarderThanTooCold() {
        fun learntAfter(feeling: Feeling) = Recommender(
            (1..4L).map { log(it, 14.0, feeling, "tshirt", "hoodie", "trousers") }, today,
        ).warmthNeeded(14.0)
        val one = learntAfter(Feeling.WAY_TOO_COLD)
        val two = learntAfter(Feeling.TOO_COLD)
        assertTrue("1 gave $one, 2 gave $two", one > two + 0.3)
    }

    @Test
    fun feelingTooHotMakesItSuggestLess() {
        val starter = Recommender(emptyList(), today).warmthNeeded(10.0)
        // Close to the starter outfit at 10°C (warmth 6.8), so "too hot" here means you want less than that.
        val logs = (1..4L).map { log(it, 10.0, Feeling.TOO_HOT, "tshirt", "hoodie", "light_jacket", "trousers") }
        val learnt = Recommender(logs, today).warmthNeeded(10.0)
        assertTrue("starter $starter, learnt $learnt", learnt < starter - 0.5)
    }

    @Test
    fun reusesAnOutfitThatFeltJustRightInSimilarWeather() {
        val past = log(3, 13.0, Feeling.JUST_RIGHT, "shirt", "fleece", "trousers")
        val s = Recommender(listOf(past), today).suggest(weather(14.0))
        assertEquals(past, s.fromPastDay)
        assertEquals(listOf("shirt", "fleece", "trousers"), s.items.map { it.id })
    }

    @Test
    fun reusedOutfitDropsSunglassesWhenItIsNotSunny() {
        val past = log(3, 13.0, Feeling.JUST_RIGHT, "tshirt", "hoodie", "trousers", "sunglasses")
        val s = Recommender(listOf(past), today).suggest(weather(13.0))
        assertEquals(listOf("tshirt", "hoodie", "trousers"), s.items.map { it.id })
    }

    @Test
    fun doesNotReuseAnOutfitThatFeltWrong() {
        val past = log(3, 13.0, Feeling.TOO_COLD, "shirt", "trousers")
        val s = Recommender(listOf(past), today).suggest(weather(14.0))
        assertNull(s.fromPastDay)
        assertTrue(s.notes.any { "too cold" in it })
    }

    @Test
    fun theFirstCardIsTheSameAsTheSuggestion() {
        val logs = listOf(
            log(3, 13.0, Feeling.JUST_RIGHT, "shirt", "fleece", "trousers"),
            log(1, 13.5, Feeling.JUST_RIGHT, "tshirt", "blazer", "trousers"),
        )
        for (r in listOf(Recommender(emptyList(), today), Recommender(logs, today))) for (t in listOf(2.0, 13.0, 22.0)) {
            assertEquals("$t°C", r.suggest(weather(t)), r.suggestions(weather(t)).first())
        }
    }

    @Test
    fun otherDaysThatFeltJustRightInWeatherLikeThisBecomeCards() {
        val older = log(5, 13.0, Feeling.JUST_RIGHT, "shirt", "fleece", "trousers")
        val newer = log(1, 14.5, Feeling.JUST_RIGHT, "tshirt", "blazer", "trousers")
        val cards = Recommender(listOf(older, newer), today).suggestions(weather(14.0))
        assertEquals(newer, cards[0].fromPastDay)
        assertEquals(older, cards[1].fromPastDay)
        assertEquals(listOf("shirt", "fleece", "trousers"), cards[1].items.map { it.id })
        assertEquals("On Mon 26 Oct in Testville it felt like 13°C. You wore this and it felt just right.", cards[1].reason)
    }

    @Test
    fun anOutfitThatLaterFeltWrongInTheSameWeatherIsLeftOut() {
        val fine = log(5, 13.0, Feeling.JUST_RIGHT, "shirt", "fleece", "trousers")
        val laterCold = log(3, 13.5, Feeling.TOO_COLD, "shirt", "fleece", "trousers")
        val latest = log(1, 14.0, Feeling.JUST_RIGHT, "tshirt", "blazer", "trousers")
        val cards = Recommender(listOf(fine, laterCold, latest), today).suggestions(weather(14.0))
        assertTrue(cards.map { it.fromPastDay }.toString(), cards.none { it.fromPastDay == fine })
    }

    @Test
    fun aCopiedOutfitIsListedInTheOrderYouPutItOn() {
        val past = log(2, 5.0, Feeling.JUST_RIGHT, "blazer", "gloves", "thermals", "shirt", "trousers")
        val ids = Recommender(listOf(past), today).suggest(weather(5.0)).items.map { it.id }
        assertEquals(listOf("shirt", "blazer", "trousers", "thermals", "gloves"), ids)
    }

    @Test
    fun madeUpCardsSayHowWarmTheyAreComparedWithWhatYouLike() {
        val r = Recommender(emptyList(), today)
        val cards = r.suggestions(weather(12.0))
        assertTrue("only ${cards.size} cards", cards.size >= 3)
        for (c in cards.drop(1)) {
            assertTrue(c.reason, Regex("^(About as warm as|A touch warmer than|A touch lighter than) the starter guess for feels-like 12°C\\.$").matches(c.reason))
        }
    }

    @Test
    fun learnsYouWearShortsWhenItIsCooler() {
        val shorts = Wardrobe.byId("shorts")!!
        assertFalse(Recommender(emptyList(), today).allowedAt(shorts, 15.0))
        val logs = listOf(log(2, 15.5, Feeling.JUST_RIGHT, "tshirt", "shorts"))
        assertTrue(Recommender(logs, today).allowedAt(shorts, 15.0))
    }

    @Test
    fun eveningCountsTheRightHoursWhenTheClocksGoBack() {
        // US clocks go back at 2am on Sunday 1 November 2026, so that evening is UTC-8, not UTC-7.
        val zone = ZoneId.of("America/Los_Angeles")
        val start = Instant.parse("2026-10-31T00:00:00Z")
        // Make each hour's feels-like equal its UTC hour of the day, so the result shows which hours were used.
        val hours = (0 until 96).map { i ->
            val t = start.plus(i.toLong(), ChronoUnit.HOURS)
            HourWeather(t, 10.0, (i % 24).toDouble(), 0, 0.0, 5.0, 0.0, 3, false)
        }
        val evening = HourlyForecast(zone, hours).summarise(LocalDate.of(2026, 11, 1), TimeOfDay.EVENING)
        assertNotNull(evening)
        // 6pm–midnight PST is 02:00–07:59 UTC on 2 November: feels-like 2 to 7.
        assertEquals(2.0, evening!!.feelsMinC, 0.001)
        assertEquals(7.0, evening.feelsMaxC, 0.001)
    }
}
