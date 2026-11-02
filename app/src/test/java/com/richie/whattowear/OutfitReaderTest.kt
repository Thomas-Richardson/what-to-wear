package com.richie.whattowear

import com.richie.whattowear.model.CustomClothing
import com.richie.whattowear.model.Feeling
import com.richie.whattowear.model.LogRow
import com.richie.whattowear.model.NoteTable
import com.richie.whattowear.model.OutfitReader
import com.richie.whattowear.model.Slot
import com.richie.whattowear.model.TimeOfDay
import com.richie.whattowear.model.Wardrobe
import com.richie.whattowear.model.WarmthLevel
import com.richie.whattowear.model.WeatherSummary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Reading what the AI says about a photo or a description, and what the app sends it. */
class OutfitReaderTest {
    private val peaCoat = CustomClothing("custom_navy_pea_coat", "Navy pea coat", Slot.JACKET, 4.0).toItem()

    @Before
    fun setUp() {
        Wardrobe.custom = listOf(peaCoat)
    }

    @After
    fun tearDown() {
        Wardrobe.custom = emptyList()
    }

    private fun parse(reply: String) = OutfitReader.parse(reply, Wardrobe.items)

    @Test
    fun theInstructionsListEveryItemIncludingYourOwn() {
        val text = OutfitReader.instructions(Wardrobe.items)
        Wardrobe.items.forEach { assertTrue(it.id, text.contains("${it.id}: ${it.name}")) }
        assertTrue(text.contains("custom_navy_pea_coat: Navy pea coat (Jacket)"))
    }

    @Test
    fun readsARealReplyFromGpt61Terra() {
        // gpt-6.1-terra's actual answer to "tweed jacket, white oxford, chinos, my navy pea coat on top,
        // and a kilt for some reason. also trainers" (2 Nov 2026).
        val reply = javaClass.classLoader!!.getResource("ai-reply-word-dump.json")!!.readText()
        val outfit = parse(reply)
        assertEquals(listOf("shirt", "blazer", "trousers", "custom_navy_pea_coat"), outfit.itemIds) // in the list's order
        assertEquals(1, outfit.suggestions.size)
        val kilt = outfit.suggestions.single()
        assertEquals("Kilt", kilt.name)
        assertEquals(Slot.BOTTOM, kilt.slot)
        assertEquals(WarmthLevel.MEDIUM, kilt.level)
        assertEquals("", outfit.unsure)
    }

    @Test
    fun clothesNotOnTheListAreDroppedNotInvented() {
        val outfit = parse("""{"items":["shirt","tuxedo","shirt",7,{"x":1}],"new":[],"unsure":"the shoes"}""")
        assertEquals(listOf("shirt"), outfit.itemIds)
        assertEquals("the shoes", outfit.unsure)
    }

    @Test
    fun copesWithAnswersWrappedInACodeBlock() {
        assertEquals(listOf("gilet"), parse("```json\n{\"items\":[\"gilet\"]}\n```").itemIds)
    }

    @Test
    fun aGarbledAnswerIsAnErrorNotAHalfFilledLog() {
        assertThrows(IllegalArgumentException::class.java) { parse("Sure! You're wearing a shirt.") }
    }

    @Test
    fun newClothesGetSensibleGroupsAndWarmth() {
        val outfit = parse(
            """{"items":[],"new":[
                {"name":"Wax jacket","group":"jacket","warmth":"Warm","keeps_rain_off":true},
                {"name":"Bucket hat","group":"Hats"},
                {"name":"Navy Pea Coat","group":"Jacket"},
                {"name":"wax jacket","group":"Jacket"},
                {"name":"","group":"Top"}]}""",
        )
        val (wax, hat) = outfit.suggestions
        assertEquals(2, outfit.suggestions.size) // already on the list, a repeat, and a blank name are dropped
        assertEquals(Slot.JACKET, wax.slot)
        assertEquals(WarmthLevel.WARM, wax.level)
        assertTrue(wax.keepsRainOff)
        assertEquals(Slot.EXTRA, hat.slot) // an unknown group becomes Extras
        assertEquals(WarmthLevel.BARELY, hat.level) // and gets that group's usual warmth
        assertFalse(hat.keepsRainOff)
    }

    @Test
    fun theRequestAsksForJsonAndCarriesYourWordsOrPhoto() {
        val described = Json.parseToJsonElement(OutfitReader.request("gpt-6.1-terra", Wardrobe.items, "tweed jacket", null)).jsonObject
        assertEquals("gpt-6.1-terra", described["model"]!!.jsonPrimitive.content)
        assertEquals("json_object", described["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        val messages = described["messages"]!!.jsonArray
        assertEquals(OutfitReader.instructions(Wardrobe.items), messages[0].jsonObject["content"]!!.jsonPrimitive.content)
        val userParts = messages[1].jsonObject["content"]!!.jsonArray
        assertEquals(1, userParts.size)
        assertTrue(userParts[0].jsonObject["text"]!!.jsonPrimitive.content.endsWith("tweed jacket"))

        val photo = Json.parseToJsonElement(OutfitReader.request("gpt-6.1-terra", Wardrobe.items, null, "QUJD")).jsonObject
        val parts = photo["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonArray
        assertEquals("data:image/jpeg;base64,QUJD", parts[1].jsonObject["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content)
    }

    @Test
    fun yourOwnWordsGoInWhatIWoreAndTheAppsReadingInCountedAs() {
        val table = NoteTable.parse(
            "| Temperature | wind? | What I wore | How I felt | Score | Counted as | Feels like °C | Date | Place | Weather |\n" +
                "| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |\n",
        )!!
        val values = LogRow.values(
            LocalDate.of(2026, 11, 2), TimeOfDay.NOW, ZonedDateTime.of(2026, 11, 2, 9, 0, 0, 0, ZoneId.of("UTC")), "Manchester",
            listOf("shirt", "blazer", "custom_navy_pea_coat").map { Wardrobe.byId(it)!! }, Feeling.JUST_RIGHT, "",
            WeatherSummary(9.0, 8.0, 10.0, 11.0, 0, 0.0, 10.0, 1.0, 0.0, 3), wore = "tweed jacket, white oxford, my pea coat",
        )
        val row = NoteTable.parse(table.withRow(values))!!.rows.single()
        assertEquals("tweed jacket, white oxford, my pea coat", row.wore)
        assertEquals("Shirt, Blazer, Navy pea coat", row.countedAs)
        assertEquals(listOf("shirt", "blazer", "custom_navy_pea_coat"), row.toEntry()!!.itemIds)
    }
}
