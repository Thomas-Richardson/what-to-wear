package com.richie.whattowear

import com.richie.whattowear.model.Calibration
import com.richie.whattowear.model.NoteTable
import com.richie.whattowear.model.Recommender
import com.richie.whattowear.model.Slot
import com.richie.whattowear.model.Wardrobe
import com.richie.whattowear.model.WeatherSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * Checks the app against Richie's real note, read straight from his vault. Skipped on any other
 * computer, so the repo never needs a copy of his data.
 */
class RealNoteTest {
    private val note = File(System.getProperty("user.home"), "Documents/Main/How do I feel at different temperatures.md")

    private fun recommender(): Recommender {
        assumeTrue("Richie's vault isn't on this computer", note.exists())
        val entries = NoteTable.parse(note.readText())!!.rows.mapNotNull { it.toEntry() }
        return Recommender(entries, LocalDate.of(2026, 11, 2))
    }

    private fun dry(t: Double) = WeatherSummary(t, t, t, t, 0, 0.0, 10.0, 1.0, 0.0, 3)
    private fun ids(realC: Double, windy: Boolean = false) =
        recommender().suggest(dry(Calibration.feelsLike(realC, windy))).items.map { it.id }

    @Test
    fun everyOldRowIsReadable() {
        assumeTrue(note.exists())
        val rows = NoteTable.parse(note.readText())!!.rows
        val usable = rows.mapNotNull { it.toEntry() }
        assertTrue("only ${usable.size} usable rows", usable.size >= 43)
        assertTrue(usable.all { it.unknownClothes.isEmpty() })
    }

    @Test
    fun elevenDegreesMeansAJacketNotHoodieAndTrackies() {
        val ids = ids(11.0)
        assertTrue(ids.toString(), ids.any { Wardrobe.byId(it)!!.slot == Slot.JACKET })
    }

    @Test
    fun glovesComeOutWhenHandsGotCold() {
        assertTrue(ids(6.0).toString(), "gloves" in ids(6.0))
    }

    @Test
    fun shortsCanComeOutFromAboutFifteenDegrees() {
        assertTrue(recommender().allowedAt(Wardrobe.byId("shorts")!!, Calibration.feelsLike(15.0, windy = false)))
    }

    @Test
    fun neverSuggestsClothesTheNoteNeverMentions() {
        val neverWorn = setOf("puffer", "jumper", "fleece", "longsleeve", "beanie", "cap")
        for (real in 0..24 step 2) for (windy in listOf(false, true)) {
            val got = ids(real.toDouble(), windy)
            assertTrue("$real°C windy=$windy: $got", got.none { it in neverWorn })
        }
    }

    @Test
    fun calibrationStillMatchesTheAnalysis() {
        assertEquals(9.3, Calibration.feelsLike(11.0, windy = false), 0.1)
        assertEquals(7.5, Calibration.feelsLike(11.0, windy = true), 0.1)
    }
}
