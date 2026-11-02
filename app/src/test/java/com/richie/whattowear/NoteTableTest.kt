package com.richie.whattowear

import com.richie.whattowear.model.Feeling
import com.richie.whattowear.model.LogRow
import com.richie.whattowear.model.NoteTable
import com.richie.whattowear.model.TimeOfDay
import com.richie.whattowear.model.Wardrobe
import com.richie.whattowear.model.WeatherSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Made-up rows in the same shape as the real note, so these tests don't depend on Richie's vault. */
class NoteTableTest {
    private val before = """
        |---
        |tags:
        |  - self_experiment
        |---
        |Some thoughts above the table.
        |
        || Temperature | wind? | What I wore      | How I felt    |
        || ----------- | ----- | ---------------- | ------------- |
        || 8           | Y     | big coat, gloves | fine          |
        || 12          | N     | Jeans gillet     | a bit chilly  |
        ||             |       |                  |               |
        |
        |Notes below the table.
        |""".trimMargin()

    private val withColumns = NoteTable.parse(before)!!.withAppColumns { row ->
        when (row?.realTempC) {
            8.0 -> mapOf(NoteTable.SCORE to "3", NoteTable.COUNTED_AS to "Warm coat, Gloves (+ Shirt, Trousers / jeans)", NoteTable.FEELS to "4.0")
            12.0 -> mapOf(NoteTable.SCORE to "2", NoteTable.COUNTED_AS to "Trousers / jeans, Gilet (+ T-shirt)")
            else -> emptyMap()
        }
    }

    private fun outsideTable(text: String) = text.lines().filterNot { it.startsWith("|") }

    @Test
    fun addingColumnsKeepsEverythingElse() {
        assertEquals(outsideTable(before), outsideTable(withColumns))
        val oldRows = before.lines().filter { it.startsWith("|") }
        val newRows = withColumns.lines().filter { it.startsWith("|") }
        assertEquals(oldRows.size, newRows.size)
        oldRows.zip(newRows).forEach { (o, n) -> assertTrue(n, n.startsWith(o.trimEnd())) }
        assertTrue(NoteTable.parse(withColumns)!!.hasAppColumns)
    }

    @Test
    fun readsRowsTheWayTheAppNeeds() {
        val rows = NoteTable.parse(withColumns)!!.rows
        assertEquals(2, rows.size) // the blank spacer row isn't a row
        val coat = rows[0].toEntry()!!
        assertEquals(Feeling.JUST_RIGHT, coat.feeling)
        assertEquals(setOf("warm_coat", "gloves", "shirt", "trousers"), coat.itemIds.toSet())
        assertEquals(4.0, coat.dressTempC, 0.001)
        assertNull(coat.date)
        // No feels-like written down: worked out from 12°C and calm (the Manchester calibration).
        assertEquals(10.5, rows[1].toEntry()!!.dressTempC, 0.1)
    }

    @Test
    fun rowsWithoutAScoreAreNotUsed() {
        val noScore = NoteTable.parse(before)!!.rows
        assertTrue(noScore.all { it.toEntry() == null })
    }

    @Test
    fun aNewLogGoesInByTemperatureAndReadsBackTheSame() {
        val table = NoteTable.parse(withColumns)!!
        val weather = WeatherSummary(9.5, 8.0, 11.0, 11.2, 70, 2.0, 25.0, 1.0, 0.0, 61)
        val items = listOf("shirt", "blazer", "trousers").map { Wardrobe.byId(it)!! }
        val savedAt = ZonedDateTime.of(2026, 11, 2, 14, 35, 0, 0, ZoneId.of("America/Los_Angeles"))
        val values = LogRow.values(LocalDate.of(2026, 11, 2), TimeOfDay.NOW, savedAt, "San Francisco", items,
            Feeling.TOO_COLD, "hands cold", weather)
        val after = table.withRow(values)

        val lines = after.lines()
        val newLine = lines.single { it.contains("San Francisco") }
        // 11°C goes after the 8°C row and before the 12°C row.
        assertTrue(lines.indexOf(newLine) > lines.indexOfFirst { it.contains("big coat") })
        assertTrue(lines.indexOf(newLine) < lines.indexOfFirst { it.contains("Jeans gillet") })
        assertEquals(outsideTable(withColumns), outsideTable(after))

        val entry = NoteTable.parse(after)!!.rows.single { it.place == "San Francisco" }.toEntry()!!
        assertEquals(Feeling.TOO_COLD, entry.feeling)
        assertEquals(listOf("shirt", "blazer", "trousers"), entry.itemIds)
        assertEquals(LocalDate.of(2026, 11, 2), entry.date)
        assertEquals("14:35", entry.whenLabel)
        assertTrue(entry.rainy)
        assertEquals("Too cold. hands cold", entry.felt)
    }

    @Test
    fun deletingRemovesOnlyThatRow() {
        val table = NoteTable.parse(withColumns)!!
        val target = table.rows.first { it.realTempC == 12.0 }
        val after = table.withoutRow(target.line)
        assertEquals(withColumns.lines().size - 1, after.lines().size)
        assertFalse(after.contains("Jeans gillet"))
        assertTrue(after.contains("big coat"))
    }

    @Test
    fun spotsATableThatSyncDoubledUp() {
        assertNull(NoteTable.parse(withColumns)!!.damage)
        // What Obsidian Sync left behind on 1 Nov 2026: the phone's copy, then a second divider and the Mac's copy.
        val lines = withColumns.lines()
        val divider = lines.first { it.startsWith("| ---") }
        val rows = lines.filter { it.startsWith("|") && !it.startsWith("| Temperature") && !it.startsWith("| ---") }
        val doubled = withColumns.replace(rows.last(), rows.last() + "\n" + divider + "\n" + rows.joinToString("\n"))
        assertEquals("it has two heading rows", NoteTable.parse(doubled)!!.damage)
    }

    @Test
    fun pipesInANoteCannotBreakTheTable() {
        assertEquals("a / b", NoteTable.clean("a | b"))
        assertNotNull(NoteTable.parse(withColumns))
        assertNull(NoteTable.parse("No table here."))
    }
}
