package com.richie.whattowear.model

import kotlinx.serialization.Serializable

/** The groups on the log screen, in the order you'd put them on. [emoji] is used for clothes you add yourself. */
@Serializable
enum class Slot(val label: String, val emoji: String) {
    TOP("Top", "👕"),
    LAYER("Layer", "🧶"),
    JACKET("Jacket", "🧥"),
    BOTTOM("Bottoms", "👖"),
    EXTRA("Extras", "🔹"),
}

/** The warmth choices when you add your own clothes, each with an example to compare against. */
enum class WarmthLevel(val label: String, val example: String, val warmth: Double) {
    BARELY("Barely", "like gloves or a scarf", 0.5),
    LIGHT("Light", "like a T-shirt or trousers", 1.0),
    MEDIUM("Medium", "like a hoodie or a blazer", 2.0),
    WARM("Warm", "like a thick jumper", 3.0),
    VERY_WARM("Very warm", "like a winter coat", 4.0);

    companion object {
        fun defaultFor(slot: Slot) = when (slot) {
            Slot.TOP, Slot.BOTTOM -> LIGHT
            Slot.LAYER, Slot.JACKET -> MEDIUM
            Slot.EXTRA -> BARELY
        }

        fun nearest(warmth: Double) = entries.minBy { kotlin.math.abs(it.warmth - warmth) }
    }
}

/** Something you've added yourself, as saved on the phone. */
@Serializable
data class CustomClothing(
    val id: String,
    val name: String,
    val slot: Slot,
    val warmth: Double,
    val keepsRainOff: Boolean = false,
) {
    fun toItem() = ClothingItem(
        id, name, slot.emoji, slot, warmth, keepsRainOff = keepsRainOff,
        // Sensible starting limits, like the built-in clothes: hats and gloves only when it's cold,
        // winter coats only when it's cold, shorts-like bottoms only when it's warm. Logs widen them.
        defaultMinC = if (slot == Slot.BOTTOM && warmth < 1.0) 19.0 else -60.0,
        defaultMaxC = when {
            slot == Slot.EXTRA && warmth > 0 -> 6.0
            warmth >= 4.0 -> 10.0
            else -> 60.0
        },
    )
}

/**
 * One thing you can wear.
 *
 * [warmth] is a rough score: a T-shirt is 1, a warm coat is 4. An outfit's warmth is the sum.
 * [defaultMinC] and [defaultMaxC] are the feels-like temperatures where the app will suggest it
 * before it has learnt anything. Your own "just right" logs can widen that range.
 * [wornUnder] items (thermals) go on as well as another item in the same group, not instead of it.
 */
data class ClothingItem(
    val id: String,
    val name: String,
    val emoji: String,
    val slot: Slot,
    val warmth: Double,
    val keepsRainOff: Boolean = false,
    val forSun: Boolean = false,
    val wornUnder: Boolean = false,
    val defaultMinC: Double = -60.0,
    val defaultMaxC: Double = 60.0,
)

object Wardrobe {
    // Within each group, earlier items win ties when the app has nothing else to go on.
    // Gilet, waistcoat, blazer and the rest come from what Richie wears in his temperatures note.
    private val builtIn = listOf(
        ClothingItem("tshirt", "T-shirt", "👕", Slot.TOP, 1.0),
        ClothingItem("shirt", "Shirt", "👔", Slot.TOP, 1.5),
        ClothingItem("ss_shirt", "Short-sleeve shirt", "👕", Slot.TOP, 1.0),
        ClothingItem("rugby", "Rugby top", "🏉", Slot.TOP, 2.0),
        ClothingItem("turtleneck", "Turtleneck", "👕", Slot.TOP, 2.0),
        ClothingItem("longsleeve", "Long-sleeve top", "👕", Slot.TOP, 1.5),
        ClothingItem("gilet", "Gilet", "🦺", Slot.LAYER, 1.5),
        ClothingItem("waistcoat", "Knitted waistcoat", "🦺", Slot.LAYER, 1.0),
        ClothingItem("hoodie", "Hoodie", "🧥", Slot.LAYER, 2.0),
        ClothingItem("cardigan", "Cardigan", "🧶", Slot.LAYER, 2.0),
        ClothingItem("jumper", "Jumper", "🧶", Slot.LAYER, 2.5),
        ClothingItem("fleece", "Fleece", "🧶", Slot.LAYER, 2.5),
        ClothingItem("light_jacket", "Light jacket", "🧥", Slot.JACKET, 2.0),
        ClothingItem("blazer", "Blazer", "🧥", Slot.JACKET, 2.0),
        ClothingItem("denim_jacket", "Denim jacket", "🧥", Slot.JACKET, 2.0),
        ClothingItem("rain_jacket", "Rain jacket", "🧥", Slot.JACKET, 1.5, keepsRainOff = true),
        ClothingItem("warm_coat", "Warm coat", "🧥", Slot.JACKET, 4.0, defaultMaxC = 10.0),
        ClothingItem("puffer", "Puffer jacket", "🧥", Slot.JACKET, 4.5, defaultMaxC = 7.0),
        ClothingItem("trousers", "Trousers / jeans", "👖", Slot.BOTTOM, 1.0),
        ClothingItem("trackies", "Trackies", "👖", Slot.BOTTOM, 1.0),
        ClothingItem("shorts", "Shorts", "🩳", Slot.BOTTOM, 0.0, defaultMinC = 19.0),
        // The ID stays "thermals" so logs saved before the rename still read correctly.
        ClothingItem("thermals", "Leggings underneath", "🧦", Slot.BOTTOM, 1.0, wornUnder = true, defaultMaxC = 3.0),
        ClothingItem("beanie", "Beanie", "🧢", Slot.EXTRA, 0.5, defaultMaxC = 6.0),
        ClothingItem("scarf", "Scarf", "🧣", Slot.EXTRA, 0.5, defaultMaxC = 6.0),
        ClothingItem("gloves", "Gloves", "🧤", Slot.EXTRA, 0.5, defaultMaxC = 4.0),
        ClothingItem("umbrella", "Umbrella", "☂️", Slot.EXTRA, 0.0, keepsRainOff = true),
        ClothingItem("sunglasses", "Sunglasses", "🕶️", Slot.EXTRA, 0.0, forSun = true),
        ClothingItem("cap", "Cap", "🧢", Slot.EXTRA, 0.0, forSun = true),
    )

    /** Clothes you've added yourself. The app sets this at start-up and whenever you add or remove one. */
    @Volatile
    var custom: List<ClothingItem> = emptyList()

    val items: List<ClothingItem> get() = builtIn + custom

    fun byId(id: String): ClothingItem? = items.firstOrNull { it.id == id }

    /** A readable, unused ID for a new item, e.g. "custom_navy_pea_coat". */
    fun newId(name: String): String {
        val base = "custom_" + name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "item" }
        var id = base
        var n = 2
        while (byId(id) != null) id = "${base}_${n++}"
        return id
    }

    fun warmthOf(ids: Collection<String>): Double = ids.sumOf { byId(it)?.warmth ?: 0.0 }
}
