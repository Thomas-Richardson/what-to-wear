package com.richie.whattowear.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.richie.whattowear.model.NoteTable
import java.io.IOException

/**
 * Reads and writes Richie's temperatures note in his Obsidian vault on the phone. Obsidian Sync
 * carries the changes to his Mac. The app keeps no copy of its own: the note is the only store.
 */
class NoteStore(context: Context) {
    private val resolver = context.contentResolver

    /** Keeps the app's permission to the note across restarts. */
    fun keepAccess(uri: Uri) = resolver.takePersistableUriPermission(
        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
    )

    fun name(uri: Uri): String = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { if (it.moveToFirst()) it.getString(0) else null }?.removeSuffix(".md") ?: "your note"

    fun read(uri: Uri): String = resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        ?: throw IOException("Couldn't open the note.")

    fun table(uri: Uri): NoteTable = checked(NoteTable.parse(read(uri)))

    private fun checked(table: NoteTable?): NoteTable {
        table ?: throw NoTableException()
        table.damage?.let { throw NoteDamagedException(it) }
        return table
    }

    fun addRow(uri: Uri, values: Map<String, String>) = change(uri) { it.withRow(values) }

    fun removeRow(uri: Uri, line: String) = change(uri) { it.withoutRow(line) }

    /** Adds the app's columns if the note doesn't have them yet. */
    fun addAppColumns(uri: Uri) = change(uri) { if (it.hasAppColumns) null else it.withAppColumns() }

    /**
     * Reads the note fresh (it may have changed on the Mac), makes one change, and writes it back.
     * If the write doesn't come back exactly right, it puts the old text back.
     */
    private fun change(uri: Uri, edit: (NoteTable) -> String?) {
        val before = read(uri)
        // Never edit a damaged table: it would only make the damage worse.
        val after = edit(checked(NoteTable.parse(before))) ?: return
        write(uri, after)
        if (read(uri) != after) {
            write(uri, before)
            throw IOException("The note didn't save properly, so it was put back as it was.")
        }
    }

    // "wt" empties the file before writing, so no old text is left on the end.
    private fun write(uri: Uri, text: String) = resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
        ?: throw IOException("Couldn't save to the note.")
}

class NoteDamagedException(damage: String) : Exception(
    "The table in your note looks damaged: $damage. This happens when your phone and Mac change the note " +
        "at the same moment. The app won't save to it until it's fixed in Obsidian (or ask Claude to fix it).",
)

class NoTableException : Exception(
    "That note has no table starting with a Temperature column. Pick \"How do I feel at different temperatures\".",
)
