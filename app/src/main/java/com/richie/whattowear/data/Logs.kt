package com.richie.whattowear.data

import android.content.Context
import android.net.Uri
import com.richie.whattowear.model.Entry
import com.richie.whattowear.model.Wardrobe

/**
 * Every log, plus how many rows in the note couldn't be used, and a short description of any row with
 * more cells than the note has headings (those are left out until they're fixed in Obsidian).
 */
data class LoadedLogs(val entries: List<Entry>, val skippedRows: Int, val rowsWithExtraCells: List<String> = emptyList())

/**
 * Loads the logs the same way for the app, the widget and the reminder: from the temperatures note
 * once it's connected, otherwise from the app's own file. Throws if the note can't be read.
 */
object Logs {
    fun load(context: Context): LoadedLogs {
        // The widget and the reminder run without the app open, so make sure your own clothes are known.
        Wardrobe.custom = CustomClothesStore(context).load().map { it.toItem() }
        val uri = Settings(context).noteUri?.let(Uri::parse)
            ?: return LoadedLogs(LogStore(context).load().map { it.toEntry() }, 0)
        val table = NoteStore(context).table(uri)
        val rows = table.rows
        val usable = rows.mapNotNull { it.toEntry() }
        val odd = table.rowsWithExtraCells
        // Blank spacer rows aren't counted; rows with a temperature but no score or clothes are.
        return LoadedLogs(
            usable,
            rows.count { it.realTempC != null && !it.extraCells } - usable.size,
            odd.map { r -> listOfNotNull(r.realTempC?.let { "%.0f°C".format(it) }, r.wore.ifBlank { null }).joinToString(", ") },
        )
    }
}
