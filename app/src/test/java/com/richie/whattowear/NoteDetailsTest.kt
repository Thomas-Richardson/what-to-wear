package com.richie.whattowear

import com.richie.whattowear.model.Calibration
import com.richie.whattowear.model.Clothes
import com.richie.whattowear.model.Entry
import com.richie.whattowear.model.Feeling
import com.richie.whattowear.model.HourWeather
import com.richie.whattowear.model.HourlyForecast
import com.richie.whattowear.model.LogRow
import com.richie.whattowear.model.NoteTable
import com.richie.whattowear.model.TimeOfDay
import com.richie.whattowear.model.Wardrobe
import com.richie.whattowear.model.WearLog
import com.richie.whattowear.model.WeatherSummary
import com.richie.whattowear.model.quickLogClothes
import com.richie.whattowear.model.words
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** Smaller pieces: the note's table in odd shapes, reading clothes, writing rows, time windows. */
class NoteDetailsTest {
    private val table = """
        || Temperature | wind? | What I wore | How I felt | Score | Counted as | Feels like °C | Date | Place | Weather |
        || ----------- | ----- | ----------- | ---------- | ----- | ---------- | ------------- | ---- | ----- | ------- |
        || 8           | Y     | big coat    | fine       | 3     | Warm coat  | 4.0           |      |       |         |
        || 14          | N     | Shirt       | chilly     | 2     |            | 12.0          |      |       |         |""".trimMargin()

    // --- The note's table in odd shapes ---

    @Test
    fun aNoteWithoutAFinalLineBreakKeepsItThatWay() {
        val after = NoteTable.parse(table)!!.withRow(mapOf(NoteTable.TEMPERATURE to "20", NoteTable.SCORE to "3"))
        assertFalse(after.endsWith("\n"))
        assertTrue(after.lines().last().startsWith("| 20"))
    }

    @Test
    fun aNewRowGoesFirstOrLastByTemperatureWhenThereIsNoSpacerRow() {
        val t = NoteTable.parse(table)!!
        assertTrue(t.withRow(mapOf(NoteTable.TEMPERATURE to "2")).lines()[2].startsWith("| 2 "))
        assertTrue(t.withRow(mapOf(NoteTable.TEMPERATURE to "30")).lines().last().startsWith("| 30"))
    }

    @Test
    fun columnsAreReadByNameWhateverTheirOrder() {
        val shuffled = """
            || Temperature | Score | Counted as | wind? | What I wore | How I felt |
            || --- | --- | --- | --- | --- | --- |
            || 9 | 3 | Shirt, Blazer | N | tweed | fine |""".trimMargin()
        val e = NoteTable.parse(shuffled)!!.rows.single().toEntry()!!
        assertEquals(listOf("shirt", "blazer"), e.itemIds)
        assertEquals(Feeling.JUST_RIGHT, e.feeling)
    }

    @Test
    fun aShortRowReadsAsBlanksNotACrash() {
        val short = table + "\n| 11 | N | hoodie | cold |"
        val row = NoteTable.parse(short)!!.rows.last()
        assertEquals("", row.countedAs)
        assertEquals(null, row.toEntry()) // no score, so the app can't use it yet
    }

    @Test
    fun deletingARowThatIsntThereSaysSo() {
        assertThrows(IllegalStateException::class.java) { NoteTable.parse(table)!!.withoutRow("| 99 | gone |") }
    }

    @Test
    fun windowsLineEndingsAreKept() {
        val crlf = table.replace("\n", "\r\n") + "\r\n"
        val t = NoteTable.parse(crlf)!!
        assertEquals(2, t.rows.size)
        val after = t.withRow(mapOf(NoteTable.TEMPERATURE to "10", NoteTable.SCORE to "3"))
        assertTrue("every line keeps its \\r", after.removeSuffix("\n").split("\n").all { it.endsWith("\r") })
    }

    // --- Reading clothes written as text ---

    @Test
    fun readsClothesWrittenInDifferentWays() {
        assertEquals(listOf("shirt", "blazer", "trousers"), Clothes.idsIn("Shirt, Blazer (+ Trousers / jeans)"))
        assertEquals(listOf("trousers", "tshirt"), Clothes.idsIn("jeans, T-SHIRT"))
        assertEquals(listOf("tshirt", "gilet"), Clothes.idsIn("tshirt, gilet"))
        assertEquals(listOf("shirt"), Clothes.idsIn("Shirt, shirt,, "))
        assertEquals(listOf("kilt"), Clothes.unknownIn("Shirt, kilt, , "))
    }

    // --- Writing a new row ---

    private fun summary(feels: Double = 9.5, temp: Double = 11.5, wind: Double = 20.0) =
        WeatherSummary(feels, 8.0, 11.0, temp, 10, 0.0, wind, 1.0, 0.0, 3)

    private fun row(timeOfDay: TimeOfDay = TimeOfDay.MORNING, weather: WeatherSummary = summary(), note: String = "") =
        LogRow.values(LocalDate.of(2026, 11, 2), timeOfDay, ZonedDateTime.of(2026, 11, 2, 14, 35, 0, 0, ZoneId.of("UTC")),
            "San Francisco", listOf(Wardrobe.byId("shirt")!!), Feeling.TOO_COLD, note, weather)

    @Test
    fun aNewRowIsWrittenTheWayRichieWritesHisColumns() {
        assertEquals("12", row()[NoteTable.TEMPERATURE]) // whole degrees
        assertEquals("Y", row()[NoteTable.WIND]) // 20 km/h counts as windy
        assertEquals("N", row(weather = summary(wind = 19.9))[NoteTable.WIND])
        assertEquals("2026-11-02 morning", row()[NoteTable.DATE])
        assertEquals("2026-11-02 14:35", row(TimeOfDay.NOW)[NoteTable.DATE])
        assertEquals("Too cold. hands numb", row(note = "hands numb")[NoteTable.FELT])
    }

    @Test
    fun aPipeInANoteCantBreakTheTable() {
        val after = NoteTable.parse(table)!!.withRow(row(note = "a | b"))
        val read = NoteTable.parse(after)!!.rows.first { it.place == "San Francisco" }
        assertEquals("Too cold. a / b", read.felt)
    }

    @Test
    fun aLogKeptInTheAppMovesIntoTheNoteUnchanged() {
        // This is what happens to logs saved before the note was connected.
        val log = WearLog(
            id = Instant.parse("2026-11-02T22:35:00Z").toEpochMilli(), date = "2026-11-02", timeOfDay = TimeOfDay.ALL_DAY,
            placeName = "Manchester", latitude = 53.48, longitude = -2.24, itemIds = listOf("shirt", "blazer", "trousers"),
            feeling = Feeling.WAY_TOO_COLD, weather = WeatherSummary(6.0, 4.2, 9.2, 8.0, 100, 14.5, 27.7, 0.5, 0.0, 61),
        )
        val moved = NoteTable.parse(NoteTable.parse(table)!!.withRow(log.toNoteValues()))!!
            .rows.first { it.place == "Manchester" }.toEntry()!!
        val direct = log.toEntry()
        assertEquals(direct.date, moved.date)
        assertEquals(direct.whenLabel, moved.whenLabel)
        assertEquals(direct.feeling, moved.feeling)
        assertEquals(direct.itemIds, moved.itemIds)
        assertEquals(direct.dressTempC, moved.dressTempC, 0.05)
        assertEquals(direct.rainy, moved.rainy)
        assertEquals(direct.sunny, moved.sunny)
    }

    // --- Weather words agree with what they describe ---

    @Test
    fun weatherWordsReadBackAsTheSameRainAndSun() {
        val zone = ZoneId.of("Europe/London")
        val start = Instant.parse("2026-11-02T08:00:00Z")
        for (code in listOf(0, 1, 2, 3, 45, 51, 61, 71, 80, 95)) for (rainMm in listOf(0.0, 0.3)) for (uv in listOf(1.0, 5.0))
            for (daylight in listOf(0, 3, 6)) {
                val hours = (0 until 6).map { i ->
                    HourWeather(start.plus(i.toLong(), ChronoUnit.HOURS), 10.0, 9.0, 20, rainMm, 10.0, uv, code, i < daylight)
                }
                val s = HourlyForecast(zone, hours).summarise(hours.first().time, hours.last().time.plusSeconds(3600))!!
                val e = Entry("x", null, "", "", null, 9.0, s.words(), listOf("tshirt"), Feeling.JUST_RIGHT)
                val label = "code $code, rain $rainMm mm, UV $uv, $daylight daylight hours: \"${s.words()}\""
                assertEquals(label, s.rainy, e.rainy)
                assertEquals(label, s.sunny, e.sunny)
            }
    }

    // --- Time windows and ordering ---

    @Test
    fun restOfTodayRunsUntil10pmButAlwaysAtLeast3Hours() {
        val zone = ZoneId.of("America/Los_Angeles")
        fun at(h: Int, m: Int) = ZonedDateTime.of(2026, 11, 2, h, m, 0, 0, zone).toInstant()
        fun local(i: Instant) = i.atZone(zone).toLocalDateTime().toString()
        val (from, to) = Ahead.REST_OF_TODAY.range(at(9, 40), zone)
        assertEquals("2026-11-02T09:00", local(from))
        assertEquals("2026-11-02T22:00", local(to))
        assertEquals("2026-11-03T00:00", local(Ahead.REST_OF_TODAY.range(at(21, 30), zone).second))
        assertEquals("2026-11-02T12:00", local(Ahead.NEXT_3_HOURS.range(at(9, 40), zone).second))
    }

    @Test
    fun theLatestLogTodayIsFoundWhateverHowItsTimeIsWritten() {
        val day = LocalDate.of(2026, 11, 2)
        fun e(time: String, item: String) = Entry(time, day, time, "", null, 9.0, "", listOf(item), Feeling.JUST_RIGHT)
        // A "Now" log at 14:35 came after a morning log.
        assertEquals(listOf("gilet"), quickLogClothes(listOf(e("morning", "hoodie"), e("14:35", "gilet")), day, emptyList()).itemIds)
        // An "all day" log (until 10pm) came after a 2pm one, but before one at 11:10pm.
        assertEquals(listOf("blazer"), quickLogClothes(listOf(e("14:00", "gilet"), e("all day", "blazer")), day, emptyList()).itemIds)
        assertEquals(listOf("coat"), quickLogClothes(listOf(e("all day", "blazer"), e("23:10", "coat")), day, emptyList()).itemIds)
    }

    // --- Small things ---

    @Test
    fun windAlwaysMakesItFeelColderAndWarmerAlwaysFeelsWarmer() {
        val range = (-5..30).map { it.toDouble() }
        assertTrue(range.all { Calibration.feelsLike(it, windy = true) < Calibration.feelsLike(it, windy = false) })
        assertTrue(range.zipWithNext().all { (a, b) -> Calibration.feelsLike(a, false) < Calibration.feelsLike(b, false) })
    }

    @Test
    fun everyFeelingSurvivesBeingSaved() {
        for (f in Feeling.entries) assertEquals(f, Json.decodeFromString<Feeling>(Json.encodeToString(f)))
    }

    @Test
    fun addingTheSameNameManyTimesNeverReusesAnId() {
        val made = mutableListOf<String>()
        try {
            repeat(30) {
                val id = Wardrobe.newId("Navy pea coat")
                made += id
                Wardrobe.custom = Wardrobe.custom + com.richie.whattowear.model.CustomClothing(id, "Navy pea coat", com.richie.whattowear.model.Slot.JACKET, 4.0).toItem()
            }
            assertEquals(30, made.toSet().size)
            assertNotNull(Wardrobe.byId("custom_navy_pea_coat_30"))
        } finally {
            Wardrobe.custom = emptyList()
        }
    }
}
