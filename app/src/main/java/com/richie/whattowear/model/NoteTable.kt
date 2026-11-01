package com.richie.whattowear.model

import java.time.LocalDate

/**
 * The table in Richie's Obsidian note "How do I feel at different temperatures".
 *
 * The note is the only place the logs live. The app reads every row, and adds or removes a single
 * line at a time, leaving the rest of the note exactly as it was. His four columns come first, as
 * they always have, so the note-temperature skill keeps working. The app's columns follow them.
 */
class NoteTable private constructor(
    private val lines: List<String>,
    private val headerAt: Int,
    private val lastRowAt: Int,
    private val columns: List<String>,
    private val endsWithNewline: Boolean,
) {
    /** Every row with something in it, in the note's order. */
    val rows: List<NoteRow> = (headerAt + 2..lastRowAt).mapNotNull { i ->
        val cells = cellsByColumn(lines[i])
        if (cells.values.all { it.isBlank() }) null else NoteRow.from(cells, lines[i])
    }

    val hasAppColumns: Boolean get() = APP_COLUMNS.keys.all { it in columns }

    /**
     * The note with one new row. Like the note-temperature skill, it goes after the other rows at
     * the same temperature, before the first warmer row or the first blank row.
     */
    fun withRow(values: Map<String, String>): String {
        val temperature = values[TEMPERATURE]?.toDoubleOrNull()
        var insertAt = lastRowAt + 1
        for (i in headerAt + 2..lastRowAt) {
            val t = cellsByColumn(lines[i])[TEMPERATURE]?.toDoubleOrNull()
            if (t == null || (temperature != null && t > temperature)) {
                insertAt = i
                break
            }
        }
        val cells = columns.map { clean(values[it].orEmpty()) }
        return rebuild(lines.toMutableList().apply { add(insertAt, formatRow(cells)) })
    }

    /** The note without the given row. Only that one line changes. */
    fun withoutRow(line: String): String {
        val at = (headerAt + 2..lastRowAt).firstOrNull { lines[it] == line }
            ?: throw IllegalStateException("That row is no longer in the note.")
        return rebuild(lines.toMutableList().apply { removeAt(at) })
    }

    /** The note with the app's columns added after his, filled in for each row by [fill]. */
    fun withAppColumns(fill: (NoteRow?) -> Map<String, String> = { emptyMap() }): String {
        val missing = APP_COLUMNS.filterKeys { it !in columns }
        if (missing.isEmpty()) return rebuild(lines)
        val newValues = (headerAt..lastRowAt).associateWith { i ->
            when (i) {
                headerAt -> missing.values.toList()
                headerAt + 1 -> missing.keys.map { "" }
                else -> {
                    val row = rows.firstOrNull { it.line == lines[i] }
                    val filled = fill(row)
                    missing.keys.map { clean(filled[it].orEmpty()) }
                }
            }
        }
        val widths = missing.keys.indices.map { c ->
            maxOf(3, newValues.filterKeys { it != headerAt + 1 }.values.maxOf { it[c].length })
        }
        val out = lines.toMutableList()
        for ((i, values) in newValues) {
            val added = if (i == headerAt + 1) widths.joinToString(" | ") { "-".repeat(it) }
            else values.mapIndexed { c, v -> v.padEnd(widths[c]) }.joinToString(" | ")
            out[i] = lines[i].trimEnd() + " " + added + " |"
        }
        return rebuild(out)
    }

    private fun cellsByColumn(line: String): Map<String, String> {
        val cells = splitRow(line)
        return columns.withIndex().associate { (i, name) -> name to cells.getOrElse(i) { "" } }
    }

    /** Pads each cell to its column's width, so new rows line up with the rest of the table. */
    private fun formatRow(cells: List<String>): String {
        val widths = columns.indices.map { c ->
            (headerAt..lastRowAt).filter { it != headerAt + 1 }
                .maxOf { splitRow(lines[it]).getOrElse(c) { "" }.length }
                .coerceAtLeast(cells[c].length)
        }
        return "| " + cells.mapIndexed { c, v -> v.padEnd(widths[c]) }.joinToString(" | ") + " |"
    }

    private fun rebuild(out: List<String>) = out.joinToString("\n") + if (endsWithNewline) "\n" else ""

    companion object {
        const val TEMPERATURE = "temperature"
        const val WIND = "wind"
        const val WORE = "whatiwore"
        const val FELT = "howifelt"
        const val SCORE = "score"
        const val COUNTED_AS = "countedas"
        const val FEELS = "feelslikec"
        const val DATE = "date"
        const val PLACE = "place"
        const val WEATHER = "weather"

        /** The app's columns and their headings, in the order they're added after his. */
        val APP_COLUMNS = linkedMapOf(
            SCORE to "Score",
            COUNTED_AS to "Counted as",
            FEELS to "Feels like °C",
            DATE to "Date",
            PLACE to "Place",
            WEATHER to "Weather",
        )

        private val HEADER = Regex("""^\s*\|\s*Temperature\s*\|""", RegexOption.IGNORE_CASE)

        /** Reads the note's text. Null if it has no table starting with a "Temperature" column. */
        fun parse(text: String): NoteTable? {
            val endsWithNewline = text.endsWith("\n")
            val lines = (if (endsWithNewline) text.dropLast(1) else text).split("\n")
            val headerAt = lines.indexOfFirst { HEADER.containsMatchIn(it) }
            if (headerAt < 0 || headerAt + 1 >= lines.size || !lines[headerAt + 1].contains("-")) return null
            var lastRowAt = headerAt + 1
            while (lastRowAt + 1 < lines.size && lines[lastRowAt + 1].trimStart().startsWith("|")) lastRowAt++
            val columns = splitRow(lines[headerAt]).map(::columnKey)
            return NoteTable(lines, headerAt, lastRowAt, columns, endsWithNewline)
        }

        /** "Feels like °C" and "feels like" both become "feelslikec"/"feelslike": letters and digits only. */
        fun columnKey(heading: String) = heading.lowercase().filter { it.isLetterOrDigit() }.let {
            if (it == "feelslike") FEELS else it
        }

        fun splitRow(line: String): List<String> =
            line.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }

        /** Keeps a value from breaking the table: no pipes, no line breaks. */
        fun clean(value: String) = value.replace("|", "/").replace("\r", " ").replace("\n", " ").trim()
    }
}

/**
 * One row of the note. His words are kept as they are; the app's columns say how to read them.
 * [line] is the row exactly as it appears in the note, which is how the app finds it again.
 */
data class NoteRow(
    val realTempC: Double?,
    val windy: Boolean?,
    val wore: String,
    val felt: String,
    val score: Int?,
    val countedAs: String,
    val feelsC: Double?,
    val date: LocalDate?,
    val whenLabel: String,
    val place: String,
    val weather: String,
    val line: String,
) {
    val feeling: Feeling? get() = Feeling.entries.firstOrNull { it.score == score }

    /** "Counted as" if it's filled in, otherwise "What I wore" (rows the app writes use the app's names there). */
    private val clothesText: String get() = countedAs.ifBlank { wore }

    val itemIds: List<String> get() = Clothes.idsIn(clothesText)
    val unknownClothes: List<String> get() = Clothes.unknownIn(clothesText)

    /** The feels-like temperature, or one worked out from the real temperature and wind if it's blank. */
    val dressTempC: Double? get() = feelsC ?: realTempC?.let { Calibration.feelsLike(it, windy == true) }

    /** A log the app can learn from, or null if the row is missing a score, clothes or temperature. */
    fun toEntry(): Entry? {
        val feeling = feeling ?: return null
        val t = dressTempC ?: return null
        if (itemIds.isEmpty()) return null
        return Entry(
            key = line, date = date, whenLabel = whenLabel, place = place, realTempC = realTempC,
            dressTempC = t, weather = weather, itemIds = itemIds, feeling = feeling,
            wore = wore, felt = felt, countedAs = countedAs, unknownClothes = unknownClothes,
        )
    }

    companion object {
        fun from(cells: Map<String, String>, line: String): NoteRow {
            val dateCell = cells[NoteTable.DATE].orEmpty()
            return NoteRow(
                realTempC = cells[NoteTable.TEMPERATURE]?.toDoubleOrNull(),
                windy = when (cells[NoteTable.WIND].orEmpty().lowercase()) {
                    "y", "yes", "very" -> true
                    "n", "no", "na" -> false
                    else -> null
                },
                wore = cells[NoteTable.WORE].orEmpty(),
                felt = cells[NoteTable.FELT].orEmpty(),
                score = cells[NoteTable.SCORE]?.toIntOrNull()?.takeIf { it in 1..5 },
                countedAs = cells[NoteTable.COUNTED_AS].orEmpty(),
                feelsC = cells[NoteTable.FEELS]?.toDoubleOrNull(),
                date = runCatching { LocalDate.parse(dateCell.take(10)) }.getOrNull(),
                whenLabel = dateCell.drop(10).trim(),
                place = cells[NoteTable.PLACE].orEmpty(),
                weather = cells[NoteTable.WEATHER].orEmpty(),
                line = line,
            )
        }
    }
}

/** Turns a list of clothes written as text ("Shirt, Blazer (+ Trousers / jeans)") into the app's items. */
object Clothes {
    private fun names(text: String) = text.replace("(+", ",").replace(")", "").replace("+", ",")
        .split(",").map { it.trim() }.filter { it.isNotEmpty() }

    private fun match(name: String): ClothingItem? {
        val n = name.lowercase()
        return Wardrobe.items.firstOrNull { it.name.lowercase() == n || it.id == n }
            // "Trousers / jeans" also answers to "trousers" and "jeans".
            ?: Wardrobe.items.firstOrNull { item -> item.name.lowercase().split(" / ").any { it == n } }
    }

    fun idsIn(text: String): List<String> = names(text).mapNotNull { match(it)?.id }.distinct()

    fun unknownIn(text: String): List<String> = names(text).filter { match(it) == null }
}

/** Real temperature to feels-like, measured in analysis/calibrate_note_temperatures.py. */
object Calibration {
    /**
     * From a year of Manchester daytime weather: feels-like sits below the real temperature by
     * (a + b × temperature), more so when it's windy.
     */
    fun feelsLike(realTempC: Double, windy: Boolean): Double {
        val gap = if (windy) 5.268 - 0.1593 * realTempC else 3.486 - 0.1617 * realTempC
        return realTempC - gap
    }
}
