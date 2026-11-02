package com.richie.whattowear.data

import android.content.Context
import android.net.Uri
import com.richie.whattowear.model.Entry
import com.richie.whattowear.model.Wardrobe

/** Every log, plus how many rows in the note couldn't be used. */
data class LoadedLogs(val entries: List<Entry>, val skippedRows: Int)

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
        val rows = NoteStore(context).table(uri).rows
        val usable = rows.mapNotNull { it.toEntry() }
        // Blank spacer rows aren't counted; rows with a temperature but no score or clothes are.
        return LoadedLogs(usable, rows.count { it.realTempC != null } - usable.size)
    }
}
