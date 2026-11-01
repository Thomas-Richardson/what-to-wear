package com.richie.whattowear.model

import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Turns a new log into the cells of a row for the temperatures note. */
object LogRow {
    /** Wind this strong or more counts as "Y" in the note's wind column. */
    private const val WINDY_KMH = 20.0

    fun values(
        date: LocalDate,
        timeOfDay: TimeOfDay,
        savedAt: ZonedDateTime,
        place: String,
        items: List<ClothingItem>,
        feeling: Feeling,
        note: String,
        weather: WeatherSummary,
    ): Map<String, String> = mapOf(
        // His columns, filled the way he fills them: the real temperature in whole degrees and Y/N for wind.
        NoteTable.TEMPERATURE to weather.tempMeanC.roundToInt().toString(),
        NoteTable.WIND to if (weather.windMaxKmh >= WINDY_KMH) "Y" else "N",
        // The app's own names, so "Counted as" can stay blank.
        NoteTable.WORE to items.joinToString(", ") { it.name },
        NoteTable.FELT to feeling.label + if (note.isBlank()) "" else ". " + note.trim(),
        NoteTable.SCORE to feeling.score.toString(),
        NoteTable.COUNTED_AS to "",
        NoteTable.FEELS to String.format(Locale.US, "%.1f", weather.dressTempC),
        // A sortable date, then the time for "Now" logs or the part of the day.
        NoteTable.DATE to "$date " + if (timeOfDay == TimeOfDay.NOW) savedAt.format(DateTimeFormatter.ofPattern("HH:mm"))
        else timeOfDay.label.lowercase(),
        NoteTable.PLACE to place,
        NoteTable.WEATHER to weather.words(),
    )
}
