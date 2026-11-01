package com.richie.whattowear

import com.richie.whattowear.model.Feeling
import com.richie.whattowear.model.WearLog
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class FeelingTest {
    private fun logWith(feeling: String) = """{"id":1,"date":"2026-10-31","timeOfDay":"ALL_DAY","placeName":"SF",
        "latitude":0,"longitude":0,"itemIds":["tshirt"],"feeling":"$feeling","weather":{"feelsMeanC":14,
        "feelsMinC":12,"feelsMaxC":16,"tempMeanC":15,"rainChanceMax":0,"rainTotalMm":0,"windMaxKmh":10,
        "uvMax":3,"sunnyShare":0.5,"weatherCode":1}}"""

    @Test
    fun scaleRunsOneToFive() {
        assertEquals(listOf(1, 2, 3, 4, 5), Feeling.entries.map { it.score })
    }

    @Test
    fun readsLogsSavedByOlderVersions() {
        // Version 0.2 had "a bit cold" and "a bit warm"; they're now 2 and 4.
        assertEquals(Feeling.TOO_COLD, Json.decodeFromString<WearLog>(logWith("A_BIT_COLD")).feeling)
        assertEquals(Feeling.TOO_WARM, Json.decodeFromString<WearLog>(logWith("A_BIT_WARM")).feeling)
        assertEquals(Feeling.TOO_HOT, Json.decodeFromString<WearLog>(logWith("TOO_HOT")).feeling)
    }
}
