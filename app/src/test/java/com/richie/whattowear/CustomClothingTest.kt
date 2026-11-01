package com.richie.whattowear

import com.richie.whattowear.model.CustomClothing
import com.richie.whattowear.model.Feeling
import com.richie.whattowear.model.Recommender
import com.richie.whattowear.model.Slot
import com.richie.whattowear.model.TimeOfDay
import com.richie.whattowear.model.Wardrobe
import com.richie.whattowear.model.WearLog
import com.richie.whattowear.model.WeatherSummary
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CustomClothingTest {
    private val today = LocalDate.of(2026, 11, 1)
    private fun dry(t: Double) = WeatherSummary(t, t, t, t, 0, 0.0, 10.0, 1.0, 0.0, 3)

    @After
    fun clear() {
        Wardrobe.custom = emptyList()
    }

    @Test
    fun givesReadableUnusedIds() {
        assertEquals("custom_navy_pea_coat", Wardrobe.newId("Navy pea-coat!"))
        Wardrobe.custom = listOf(CustomClothing("custom_navy_pea_coat", "Navy pea coat", Slot.JACKET, 4.0).toItem())
        assertEquals("custom_navy_pea_coat_2", Wardrobe.newId("Navy pea coat"))
    }

    @Test
    fun yourOwnCoatGetsSuggestedOnceYouHaveWornIt() {
        val coat = CustomClothing("custom_navy_pea_coat", "Navy pea coat", Slot.JACKET, 4.0)
        Wardrobe.custom = listOf(coat.toItem())
        // Logged in it a few times at about 6°C and was comfortable.
        val logs = (1..3L).map { i ->
            WearLog(i, today.minusDays(i).toString(), TimeOfDay.ALL_DAY, "Manchester", 0.0, 0.0,
                listOf("shirt", "custom_navy_pea_coat", "trousers"), Feeling.JUST_RIGHT, dry(6.0)).toEntry()
        }
        val ids = Recommender(logs, today).suggest(dry(5.0)).items.map { it.id }
        assertTrue(ids.toString(), "custom_navy_pea_coat" in ids)
    }

    @Test
    fun yourOwnHatIsOnlyForColdDays() {
        val hat = CustomClothing("custom_bobble_hat", "Bobble hat", Slot.EXTRA, 0.5).toItem()
        Wardrobe.custom = listOf(hat)
        val r = Recommender(emptyList(), today)
        assertTrue(r.allowedAt(hat, 3.0))
        assertFalse(r.allowedAt(hat, 15.0))
    }
}
