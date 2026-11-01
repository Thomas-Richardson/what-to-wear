package com.richie.whattowear.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * How an outfit felt, on Richie's 1–5 scale.
 * [step] is how much more (plus) or less (minus) warmth you'd have liked: a jumper's worth is about 2.
 * [certainty] is how much the app trusts it: "just right" pins the answer down, "too cold" only says "more".
 */
@Serializable(with = FeelingSerializer::class)
enum class Feeling(val score: Int, val label: String, val emoji: String, val step: Double, val certainty: Double) {
    WAY_TOO_COLD(1, "Way too cold", "🥶", 2.5, 0.7),
    TOO_COLD(2, "Too cold", "😬", 1.0, 0.85),
    JUST_RIGHT(3, "Just right", "😊", 0.0, 1.0),
    TOO_WARM(4, "Too warm", "😅", -1.0, 0.85),
    TOO_HOT(5, "Too hot", "🥵", -2.5, 0.7);

    val wantedMore: Boolean get() = step > 0
}

/**
 * Saves a feeling by name, and still reads logs saved by version 0.2, whose
 * "a bit cold" and "a bit warm" are now 2 (too cold) and 4 (too warm).
 */
object FeelingSerializer : KSerializer<Feeling> {
    private val oldNames = mapOf("A_BIT_COLD" to Feeling.TOO_COLD, "A_BIT_WARM" to Feeling.TOO_WARM)

    override val descriptor = PrimitiveSerialDescriptor("Feeling", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Feeling) = encoder.encodeString(value.name)

    override fun deserialize(decoder: Decoder): Feeling {
        val name = decoder.decodeString()
        return oldNames[name] ?: Feeling.valueOf(name)
    }
}

/** One day's entry: what you wore, where, when, the weather then, and how it felt. */
@Serializable
data class WearLog(
    /** When the log was saved, in milliseconds. Also used as its ID. */
    val id: Long,
    /** The day you wore it, as yyyy-mm-dd. */
    val date: String,
    val timeOfDay: TimeOfDay,
    val placeName: String,
    val latitude: Double,
    val longitude: Double,
    val itemIds: List<String>,
    val feeling: Feeling,
    val weather: WeatherSummary,
    val note: String = "",
) {
    val localDate: LocalDate get() = LocalDate.parse(date)
    val warmth: Double get() = Wardrobe.warmthOf(itemIds)
    val items: List<ClothingItem> get() = itemIds.mapNotNull(Wardrobe::byId)

    private val savedAt get() = Instant.ofEpochMilli(id).atZone(ZoneId.systemDefault())

    fun toEntry() = Entry(
        key = "log:$id", date = localDate,
        whenLabel = if (timeOfDay == TimeOfDay.NOW) savedAt.format(DateTimeFormatter.ofPattern("HH:mm")) else timeOfDay.label.lowercase(),
        place = placeName, realTempC = weather.tempMeanC, dressTempC = weather.dressTempC,
        weather = weather.words(), itemIds = itemIds, feeling = feeling, felt = note,
    )

    /** The same log as a row for the temperatures note. */
    fun toNoteValues() = LogRow.values(localDate, timeOfDay, savedAt, placeName, items, feeling, note, weather)
}

data class Place(val name: String, val latitude: Double, val longitude: Double, val detail: String? = null)
