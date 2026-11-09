package com.richie.whattowear.model

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow

/**
 * Works out what to wear, learning from your logs in four ways:
 *
 * 1. If you've logged a day with very similar weather and it felt just right, it suggests that
 *    outfit again.
 * 2. Otherwise it uses a straight line for "how much warmth you like at each temperature". It starts
 *    from a sensible guess, and every log nudges the line: "too cold" says you needed more than you
 *    wore, "too hot" says less.
 * 3. It learns when you're happy in particular items, e.g. shorts from 15°C if you've logged that
 *    and felt fine.
 * 4. It prefers the items you actually log, so it suggests your hoodie, not a fleece you never wear.
 *
 * The logs are the rows of Richie's temperatures note, including the ones he wrote before the app
 * existed. Those have no date, so they count for half a log and never fade.
 */
class Recommender(
    private val entries: List<Entry>,
    private val today: LocalDate = LocalDate.now(),
) {
    data class Suggestion(
        val items: List<ClothingItem>,
        val reason: String,
        val notes: List<String>,
        /** The past day this outfit was copied from, if any. */
        val fromPastDay: Entry?,
    )

    /** One outfit at one feels-like temperature, how it felt, and how much it counts. */
    private class Evidence(
        val dressTempC: Double,
        val itemIds: List<String>,
        val feeling: Feeling,
        val weight: Double,
        val dry: Boolean,
    )

    private val evidence: List<Evidence> = entries.map { e ->
        // Recent logs count for more, because what feels comfy shifts with the seasons.
        val weight = e.date?.let { 0.5.pow(ChronoUnit.DAYS.between(it, today).coerceAtLeast(0) / HALF_LIFE_DAYS) }
            ?: UNDATED_WEIGHT
        Evidence(e.dressTempC, e.itemIds, e.feeling, weight, !e.rainy)
    }

    /** How many logs fed the warmth line (very light outfits that felt fine are left out; see below). */
    var logsUsed = 0
        private set
    private var intercept = START_INTERCEPT
    private var slope = START_SLOPE

    /** Share of logs and note rows each item appears in. Empty until there are enough to mean anything. */
    private val usage: Map<String, Double> =
        if (evidence.size < MIN_LOGS_FOR_HABITS) emptyMap()
        else Wardrobe.items.associate { item -> item.id to evidence.count { item.id in it.itemIds }.toDouble() / evidence.size }

    /** Whether you seem to own this: true if you've worn it, or if there's too little to go on yet. */
    private fun worn(item: ClothingItem) = usage.isEmpty() || (usage[item.id] ?: 0.0) > 0.0

    /** Feels-like temperatures (°C) at which you've worn each item and felt just right. */
    private val fineAt: Map<String, List<Double>> = Wardrobe.items.associate { item ->
        item.id to evidence.filter { item.id in it.itemIds && it.feeling == Feeling.JUST_RIGHT }.map { it.dressTempC }
    }

    init {
        fitWarmthLine()
    }

    /** How much warmth (T-shirt = 1) you'd want at this feels-like temperature. */
    fun warmthNeeded(dressTempC: Double): Double =
        (intercept + slope * (PIVOT_C - dressTempC)).coerceIn(MIN_WARMTH, MAX_WARMTH)

    fun suggest(weather: WeatherSummary): Suggestion {
        val t = weather.dressTempC
        val notes = weatherNotes(weather).toMutableList()

        // Only dated logs: "last time" needs a day to point to.
        val lastSimilar = entries
            .filter { it.date != null && abs(it.dressTempC - t) <= SIMILAR_WITHIN_C && it.rainy == weather.rainy }
            .maxWithOrNull(compareBy<Entry>({ it.date }, { it.endsAtMinute }))

        if (lastSimilar != null && lastSimilar.feeling == Feeling.JUST_RIGHT) {
            // Sun gear depends on today's sky, not the old day's (no sunglasses because last time was sunny).
            val clothes = inWearingOrder(lastSimilar.items.filterNot { it.forSun })
            return Suggestion(
                items = withRainCover(clothes, weather) + sunItems(weather),
                reason = "Last time it felt like ${deg(lastSimilar.dressTempC)} " +
                    "(${dayLabel(lastSimilar)}) you wore this and it felt just right.",
                notes = notes,
                fromPastDay = lastSimilar,
            )
        }
        if (lastSimilar != null) {
            notes += "On ${dayLabel(lastSimilar)} you were ${lastSimilar.feeling.label.lowercase()} " +
                "in weather like this, so this allows for that."
        }

        val reason = if (logsUsed == 0) {
            "Starter guess for feels-like ${deg(t)}. Log what you wear and how it felt, and it will learn what suits you."
        } else {
            "Learnt from your ${plural(logsUsed, "log")}: about what you like to wear when it feels like ${deg(t)}."
        }
        return Suggestion(outfitFor(weather, warmthNeeded(t)), reason, notes, null)
    }

    /**
     * Up to [MAX_OUTFITS] different outfits that would all suit this weather, for the cards on the Today tab.
     *
     * The first is always [suggest]'s answer, so the first card and the widget agree. Then come other
     * outfits that felt just right in weather like this (your newest logs first, then rows from before the
     * app), then other combinations about as warm as you like. Each card differs from every card before it
     * in at least one of top, layer, jacket, bottoms or leggings. Hats, gloves and the like don't count as a
     * difference, because they come and go with the cold, not with the outfit. Made-up combinations try to
     * differ in two of those first, so the cards aren't five copies of one outfit with a different top.
     */
    fun suggestions(weather: WeatherSummary): List<Suggestion> {
        val first = suggest(weather)
        val cards = mutableListOf(first)
        fun isNew(items: List<ClothingItem>, minChanges: Int) = cards.none { changes(it.items, items) < minChanges }

        for (day in pastOutfitsThatFeltRight(weather)) {
            if (cards.size >= MAX_OUTFITS) break
            val items = withRainCover(inWearingOrder(day.items.filterNot { it.forSun }), weather) + sunItems(weather)
            if (isNew(items, 1)) cards += Suggestion(items, pastDayReason(day), first.notes, day)
        }

        val t = weather.dressTempC
        val target = warmthNeeded(t)
        // Sorting keeps tied outfits in the order they were tried, so the best one is the same as outfitFor's.
        val ranked = allOutfits(weather, target).sortedBy { it.second }
        val (best, bestScore) = ranked.firstOrNull() ?: return cards
        // On a bitter day even the warmest outfit can fall short, so allow as big a gap as the best one has.
        val allowedGap = maxOf(abs(best.sumOf { it.warmth } - target), ALTERNATIVE_WARMTH_GAP)
        val close = ranked.filter { (outfit, score) ->
            score <= bestScore + ALTERNATIVE_SLACK &&
                abs(outfit.sumOf { it.warmth } - target) <= allowedGap + 1e-9 &&
                // Never bring in clothes you've never logged (a puffer, say), unless the best outfit needs them too.
                outfit.all { worn(it) || it in best }
        }
        for (minChanges in listOf(2, 1)) for ((outfit, _) in close) {
            if (cards.size >= MAX_OUTFITS) break
            val items = outfit + sunItems(weather)
            if (isNew(items, minChanges)) cards += Suggestion(items, warmthReason(outfit, target, t), first.notes, null)
        }
        return cards
    }

    /**
     * Logs and note rows in weather like this (within 2°C, same rain or not) that felt just right: dated ones
     * newest first, then the rows from before the app, closest temperature first. An outfit is left out if you
     * wore it again later in weather like this and it didn't feel right that time.
     */
    private fun pastOutfitsThatFeltRight(weather: WeatherSummary): List<Entry> {
        val similar = entries.filter {
            abs(it.dressTempC - weather.dressTempC) <= SIMILAR_WITHIN_C && it.rainy == weather.rainy &&
                it.items.any { item -> item.slot != Slot.EXTRA }
        }
        val outfitOf = similar.associateWith { groups(it.items) }
        fun isLater(a: Entry, b: Entry): Boolean = when {
            a.date == null -> false
            b.date == null -> true
            else -> a.date > b.date || (a.date == b.date && a.endsAtMinute > b.endsAtMinute)
        }
        fun wentWrongLater(e: Entry) = similar.any { other ->
            other.feeling != Feeling.JUST_RIGHT && isLater(other, e) && outfitOf[other] == outfitOf[e]
        }
        return similar
            .filter { it.feeling == Feeling.JUST_RIGHT && !wentWrongLater(it) }
            .sortedWith(
                compareBy<Entry> { it.date == null }
                    .thenByDescending { it.date }
                    .thenByDescending { it.endsAtMinute }
                    .thenBy { abs(it.dressTempC - weather.dressTempC) },
            )
    }

    private fun pastDayReason(e: Entry): String = when {
        e.date != null ->
            "On ${dayLabel(e)} it felt like ${deg(e.dressTempC)}. You wore this and it felt just right."
        e.realTempC != null ->
            "Your note says this felt just right at ${deg(e.realTempC)} (feels like about ${deg(e.dressTempC)})."
        else -> "Your note says this felt just right when it felt like ${deg(e.dressTempC)}."
    }

    private fun warmthReason(outfit: List<ClothingItem>, target: Double, t: Double): String {
        val gap = outfit.sumOf { it.warmth } - target
        val basis = if (logsUsed == 0) "the starter guess for" else "what you like at"
        return when {
            gap > SAME_WARMTH -> "A touch warmer than $basis feels-like ${deg(t)}."
            gap < -SAME_WARMTH -> "A touch lighter than $basis feels-like ${deg(t)}."
            else -> "About as warm as $basis feels-like ${deg(t)}."
        }
    }

    /** What the warmth line alone suggests on a plain dry day. Shown on the History tab. */
    fun typicalOutfit(feelsC: Double): List<ClothingItem> =
        outfitFor(WeatherSummary.steady(feelsC), warmthNeeded(feelsC))

    /** How much warmer (plus) or cooler (minus) than the starter guess you like to be on a mild day. */
    fun differenceFromStarter(): Double = warmthNeeded(12.0) - (START_INTERCEPT + START_SLOPE * (PIVOT_C - 12.0))

    /** Whether the app will suggest this item at this feels-like temperature. */
    internal fun allowedAt(item: ClothingItem, t: Double): Boolean {
        val okTemps = fineAt[item.id].orEmpty()
        val min = minOf(item.defaultMinC, (okTemps.minOrNull() ?: Double.POSITIVE_INFINITY) - ITEM_RANGE_MARGIN_C)
        val max = maxOf(item.defaultMaxC, (okTemps.maxOrNull() ?: Double.NEGATIVE_INFINITY) + ITEM_RANGE_MARGIN_C)
        return t in min..max
    }

    /**
     * Fits "warmth = intercept + slope × (18 − feels-like)" through your logs and note rows.
     * Each one says how much warmth you'd have been comfortable in at that temperature.
     */
    private fun fitWarmthLine() {
        // (x = degrees below 18°C, y = comfortable warmth, weight)
        val points = mutableListOf<Triple<Double, Double, Double>>()

        // The starter guess counts as three made-up logs, so a handful of real logs nudge it rather than fling it about.
        for (t in listOf(2.0, 10.0, 18.0)) {
            points += Triple(PIVOT_C - t, START_INTERCEPT + START_SLOPE * (PIVOT_C - t), PRIOR_WEIGHT)
        }

        for (e in evidence) {
            val worn = Wardrobe.warmthOf(e.itemIds)
            // In a T-shirt and shorts you can't wear less, so "just right" or "too hot" there tells it nothing.
            if (!e.feeling.wantedMore && worn <= 1.5) continue
            val comfy = maxOf(worn + e.feeling.step, MIN_WARMTH)
            // "Too cold" only says you needed more, not exactly how much, so it counts for a bit less.
            points += Triple(PIVOT_C - e.dressTempC, comfy, e.weight * e.feeling.certainty)
            logsUsed++
        }

        // Weighted least-squares straight line.
        val s = points.sumOf { it.third }
        val sx = points.sumOf { it.third * it.first }
        val sy = points.sumOf { it.third * it.second }
        val sxx = points.sumOf { it.third * it.first * it.first }
        val sxy = points.sumOf { it.third * it.first * it.second }
        val denominator = s * sxx - sx * sx
        val fitted = if (denominator > 1e-9) (s * sxy - sx * sy) / denominator else START_SLOPE
        // Keep the slope sensible: colder should always mean at least a bit more clothing.
        slope = fitted.coerceIn(MIN_SLOPE, MAX_SLOPE)
        intercept = (sy - slope * sx) / s
    }

    /** Tries every sensible combination and picks the one closest to the warmth you want. */
    private fun outfitFor(weather: WeatherSummary, target: Double): List<ClothingItem> =
        // minBy keeps the first of any tie, so earlier items in the clothes list win, as before.
        allOutfits(weather, target).minByOrNull { it.second }?.first.orEmpty() + sunItems(weather)

    /**
     * Every sensible combination (without sun gear), each with its score: how far its warmth is from
     * [target], plus small costs that favour fewer items and clothes you actually wear. Lower is better.
     */
    private fun allOutfits(weather: WeatherSummary, target: Double): List<Pair<List<ClothingItem>, Double>> {
        val t = weather.dressTempC
        val ok = Wardrobe.items.filter { allowedAt(it, t) }
        val tops = ok.filter { it.slot == Slot.TOP }
        val layers = listOf<ClothingItem?>(null) + ok.filter { it.slot == Slot.LAYER }
        val jackets = listOf<ClothingItem?>(null) + ok.filter { it.slot == Slot.JACKET }
        val bottoms = ok.filter { it.slot == Slot.BOTTOM && !it.wornUnder }
        val under = listOf<ClothingItem?>(null) + ok.filter { it.slot == Slot.BOTTOM && it.wornUnder }
        // Hats, scarves and gloves go on whenever it's cold enough for them (and you wear them),
        // not to make up a warmth score. Richie's note is full of "hands cold" at 6–11°C.
        val coldExtras = ok.filter { it.slot == Slot.EXTRA && it.warmth > 0 && worn(it) }
        val umbrella = Wardrobe.byId("umbrella")!!
        val umbrellaChoices = if (weather.rainy) listOf(false, true) else listOf(false)
        val costOf = (ok + umbrella).associateWith { cost(it, weather) }

        val outfits = mutableListOf<Pair<List<ClothingItem>, Double>>()
        for (top in tops) for (layer in layers) for (jacket in jackets) for (bottom in bottoms)
            for (thermal in under) for (withUmbrella in umbrellaChoices) {
                if (weather.rainy && jacket?.keepsRainOff != true && !withUmbrella) continue
                // A rain jacket and an umbrella together is never the answer (it only ever scored worse anyway).
                if (withUmbrella && jacket?.keepsRainOff == true) continue
                val outfit = listOfNotNull(top, layer, jacket, bottom, thermal) + coldExtras +
                    (if (withUmbrella) listOf(umbrella) else emptyList())
                val score = abs(outfit.sumOf { it.warmth } - target) + outfit.sumOf { costOf.getValue(it) }
                outfits += outfit to score
            }
        return outfits
    }

    /**
     * A small cost per item, so it prefers fewer items and items you actually wear (and strongly avoids ones you never do).
     * Rain gear costs extra on dry days (a rain jacket on a sunny day looks odd), unless you often wear it then anyway.
     */
    private fun cost(item: ClothingItem, weather: WeatherSummary): Double {
        val tieBreak = TIE_BREAK * Wardrobe.items.filter { it.slot == item.slot }.indexOf(item)
        val unfamiliar = when {
            usage.isEmpty() -> 0.0
            !worn(item) -> NEVER_WORN_COST
            else -> UNFAMILIAR_COST * (1 - usage.getValue(item.id))
        }
        val rainGearWhenDry =
            if (item.keepsRainOff && !weather.rainy && !wearsOnDryDays(item)) DRY_RAIN_GEAR_COST else 0.0
        return ITEM_COST + tieBreak + unfamiliar + rainGearWhenDry
    }

    private fun wearsOnDryDays(item: ClothingItem): Boolean {
        val dry = evidence.filter { it.dry }
        return dry.size >= MIN_LOGS_FOR_HABITS && dry.count { item.id in it.itemIds } >= 0.3 * dry.size
    }

    private fun sunItems(weather: WeatherSummary): List<ClothingItem> {
        if (!weather.sunny) return emptyList()
        val sunnyLogs = entries.filter { it.sunny }
        return Wardrobe.items.filter { it.forSun }.filter { item ->
            // Until it has seen a few sunny days: sunglasses yes, cap no.
            if (sunnyLogs.size < MIN_LOGS_FOR_HABITS) item.id == "sunglasses"
            else sunnyLogs.count { item.id in it.itemIds } >= 0.3 * sunnyLogs.size
        }
    }

    private fun withRainCover(items: List<ClothingItem>, weather: WeatherSummary): List<ClothingItem> =
        if (weather.rainy && items.none { it.keepsRainOff }) items + Wardrobe.byId("umbrella")!! else items

    private fun weatherNotes(w: WeatherSummary): List<String> = buildList {
        if (w.feelsMaxC - w.feelsMinC >= 6) {
            add("Big swing: feels like ${degRange(w.feelsMinC, w.feelsMaxC)}. Wear layers you can take off.")
        }
        if (w.rainy) add("Rain likely (up to ${w.rainChanceMax}% chance).")
        if (w.windMaxKmh >= 30) add("Windy: up to ${w.windMaxKmh.toInt()} km/h.")
        if (w.sunny) add("Sunny, UV up to ${w.uvMax.toInt()}.")
    }

    private fun dayLabel(e: Entry) = e.date!!.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)) +
        if (e.place.isNotBlank()) " in ${e.place}" else ""

    companion object {
        const val PIVOT_C = 18.0
        // The starter guess: at 18°C a T-shirt and trousers (warmth 2), and 0.6 more for every degree colder.
        const val START_INTERCEPT = 2.0
        const val START_SLOPE = 0.6
        const val PRIOR_WEIGHT = 1.0
        const val MIN_SLOPE = 0.15
        const val MAX_SLOPE = 1.2
        const val MIN_WARMTH = 1.0
        const val MAX_WARMTH = 14.0
        /** Rows from before the app have no date: each counts as half a log and never fades, so new logs take over. */
        const val UNDATED_WEIGHT = 0.5
        const val SIMILAR_WITHIN_C = 2.0
        const val HALF_LIFE_DAYS = 180.0
        const val ITEM_RANGE_MARGIN_C = 1.0
        const val MIN_LOGS_FOR_HABITS = 3
        const val ITEM_COST = 0.05
        const val TIE_BREAK = 0.02
        const val UNFAMILIAR_COST = 0.4
        const val NEVER_WORN_COST = 0.8
        const val DRY_RAIN_GEAR_COST = 0.5
        /** The most cards the Today tab shows. */
        const val MAX_OUTFITS = 5
        /** A made-up alternative must score within this of the best one (warmth gap plus the small costs). */
        const val ALTERNATIVE_SLACK = 0.6
        /** …and be within this much warmth of what you like (or as close as the best outfit gets). A "too cold" means about 1 point short. */
        const val ALTERNATIVE_WARMTH_GAP = 0.6
        /** Within this much warmth, a card says "about as warm"; beyond it, "a touch warmer" or "lighter". */
        const val SAME_WARMTH = 0.25

        /**
         * A logged outfit in the order you'd put it on (top, layer, jacket, bottoms, leggings, extras), because
         * rows in the note list clothes in whatever order they were written, e.g. "Blazer, Gloves, Shirt".
         */
        internal fun inWearingOrder(items: List<ClothingItem>) = items.sortedWith(compareBy({ it.slot.ordinal }, { it.wornUnder }))

        /** Each group of an outfit (top, layer, jacket, bottoms, leggings) and what's in it. Extras are left out. */
        private fun groups(items: List<ClothingItem>): Map<Pair<Slot, Boolean>, Set<String>> =
            items.filter { it.slot != Slot.EXTRA }.groupBy({ it.slot to it.wornUnder }, { it.id }).mapValues { it.value.toSet() }

        /** How many groups differ between two outfits: a different top is 1, a different top and jacket is 2. */
        fun changes(a: List<ClothingItem>, b: List<ClothingItem>): Int {
            val ga = groups(a)
            val gb = groups(b)
            return (ga.keys + gb.keys).count { ga[it] != gb[it] }
        }
    }
}

fun plural(n: Int, word: String) = "$n $word${if (n == 1) "" else "s"}"
