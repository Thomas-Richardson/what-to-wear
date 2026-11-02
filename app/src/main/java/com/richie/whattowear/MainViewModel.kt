package com.richie.whattowear

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.richie.whattowear.data.CustomClothesStore
import com.richie.whattowear.data.LocationHelper
import com.richie.whattowear.data.LocationResult
import com.richie.whattowear.data.LogStore
import com.richie.whattowear.data.Logs
import com.richie.whattowear.data.NoTableException
import com.richie.whattowear.data.NoteDamagedException
import com.richie.whattowear.data.NoteStore
import com.richie.whattowear.data.Settings
import com.richie.whattowear.data.WeatherClient
import com.richie.whattowear.model.ClothingItem
import com.richie.whattowear.model.CustomClothing
import com.richie.whattowear.model.Entry
import com.richie.whattowear.model.Feeling
import com.richie.whattowear.model.HourlyForecast
import com.richie.whattowear.model.Place
import com.richie.whattowear.model.Slot
import com.richie.whattowear.model.TimeOfDay
import com.richie.whattowear.model.Wardrobe
import com.richie.whattowear.model.WarmthLevel
import com.richie.whattowear.model.WearLog
import com.richie.whattowear.model.WeatherSummary
import com.richie.whattowear.model.plural
import com.richie.whattowear.reminder.Reminders
import com.richie.whattowear.widget.WidgetUpdates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** How far ahead to dress for on the Today tab. */
enum class Ahead(val label: String) {
    NEXT_3_HOURS("Next 3 hours"),
    REST_OF_TODAY("Rest of today");

    fun range(now: Instant, zone: ZoneId): Pair<Instant, Instant> {
        val start = now.truncatedTo(ChronoUnit.HOURS)
        val threeHours = start.plus(3, ChronoUnit.HOURS)
        val end = when (this) {
            NEXT_3_HOURS -> threeHours
            // Until 10pm, matching "All day" on the log screen, but always at least 3 hours.
            REST_OF_TODAY -> maxOf(now.atZone(zone).toLocalDate().atTime(22, 0).atZone(zone).toInstant(), threeHours)
        }
        return start to end
    }
}

/** What's been filled in so far on the Log tab. */
data class LogDraft(
    val date: LocalDate = LocalDate.now(),
    val timeOfDay: TimeOfDay = TimeOfDay.NOW,
    val place: Place? = null,
    val itemIds: Set<String> = emptySet(),
    val feeling: Feeling? = null,
    val note: String = "",
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = LogStore(app)
    private val settings = Settings(app)
    private val clothesStore = CustomClothesStore(app)
    private val noteStore = NoteStore(app)

    /** Clothes you've added yourself. Kept in step with [Wardrobe.custom], which the rest of the app reads. */
    var customClothes by mutableStateOf(clothesStore.load().also { saved -> Wardrobe.custom = saved.map { it.toItem() } })
        private set

    /** Every log the app learns from: the rows of the temperatures note, once it's connected. */
    var entries by mutableStateOf<List<Entry>>(emptyList())
        private set

    /** The connected note's name, or null if the app is still keeping logs itself. */
    var noteName by mutableStateOf(settings.noteName)
        private set

    /** Set when the connected note can't be read, e.g. it was renamed or moved. Saving waits until it's fixed. */
    var noteProblem by mutableStateOf<String?>(null)
        private set

    /** True when the note's table is damaged, so reconnecting won't help: it needs fixing, then checking again. */
    var noteDamaged by mutableStateOf(false)
        private set

    /** Rows in the note that couldn't be used, e.g. a score or clothes missing. */
    var skippedRows by mutableStateOf(0)
        private set
    var place by mutableStateOf(settings.place)
        private set
    var usingGps by mutableStateOf(settings.usingGps)
        private set
    var forecast by mutableStateOf<HourlyForecast?>(null)
        private set
    private var fetchedAt: Instant? = null
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var ahead by mutableStateOf(Ahead.REST_OF_TODAY)

    var draft by mutableStateOf(LogDraft(place = settings.place))
        private set
    var saving by mutableStateOf(false)
        private set

    /** A short message to pop up at the bottom of the screen. */
    var message by mutableStateOf<String?>(null)

    /** Goes up each time a log is saved, so the quick-log window knows when to close. */
    var savedCount by mutableStateOf(0)
        private set

    var reminderOn by mutableStateOf(settings.reminderOn)
        private set

    fun setReminder(on: Boolean) {
        reminderOn = on
        settings.reminderOn = on
        Reminders.schedule(getApplication(), restart = true)
    }

    /** Starts a log from the widget or the reminder: "Now", with the score and clothes already chosen. */
    fun startQuickLog(feeling: Feeling?, itemIds: List<String>) {
        draft = LogDraft(place = place, itemIds = itemIds.toSet(), feeling = feeling)
    }

    init {
        reloadEntries()
    }

    private val noteUri: Uri? get() = settings.noteUri?.let(Uri::parse)

    /** Reads the logs again, e.g. when you come back to the app after editing the note on your Mac. */
    fun reloadEntries() {
        viewModelScope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) { Logs.load(getApplication()) }
                entries = loaded.entries
                skippedRows = loaded.skippedRows
                noteProblem = null
                noteDamaged = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                noteDamaged = e is NoteDamagedException
                noteProblem = if (e is NoteDamagedException) e.message
                else "Couldn't read \"${noteName ?: "your note"}\". If you renamed or moved it, connect it again."
            }
        }
    }

    /**
     * Connects the temperatures note: keeps permission to it, adds the app's columns if they're missing,
     * and moves any logs the app was keeping itself into the note.
     */
    fun connectNote(uri: Uri) {
        viewModelScope.launch {
            try {
                val (name, moved) = withContext(Dispatchers.IO) {
                    noteStore.keepAccess(uri)
                    noteStore.addAppColumns(uri)
                    val waiting = store.load()
                    for (log in waiting) noteStore.addRow(uri, log.toNoteValues())
                    store.retire()
                    noteStore.name(uri) to waiting.size
                }
                settings.noteUri = uri.toString()
                settings.noteName = name
                noteName = name
                message = "Connected to \"$name\"." + if (moved > 0) " Moved ${plural(moved, "log")} into it." else ""
                reloadEntries()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = e.message ?: "Couldn't connect that note."
            }
        }
    }

    fun todaySummary(now: Instant = Instant.now()): WeatherSummary? {
        val f = forecast ?: return null
        val (from, to) = ahead.range(now, f.zone)
        return f.summarise(from, to)
    }

    fun refresh() {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                if (usingGps) {
                    val found = LocationHelper.find(getApplication())
                    locationDetail = (found as? LocationResult.NoFix)?.detail
                    needsLocationPermission = found is LocationResult.NoPermission
                    // Say which step failed, so the fix is obvious (and a screenshot tells Claude the cause).
                    val why = when (found) {
                        is LocationResult.Found -> null
                        LocationResult.NoPermission -> "What to Wear isn't allowed to use your location."
                        LocationResult.LocationOff -> "Location is switched off on your phone."
                        is LocationResult.NoFix -> "Couldn't get a reading of where you are."
                    }
                    when {
                        found is LocationResult.Found -> rememberPlace(found.place)
                        place == null -> throw Problem("$why Tap Change to pick a place instead.")
                        else -> message = "$why Showing ${place?.name} instead."
                    }
                }
                val p = place ?: throw Problem("Choose a place to see the weather.")
                forecast = WeatherClient.forecast(p.latitude, p.longitude)
                fetchedAt = Instant.now()
                WidgetUpdates.refreshNow(getApplication())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = friendly(e)
            } finally {
                loading = false
            }
        }
    }

    /** Fetch again if the forecast is over an hour old, e.g. when you come back to the app later. */
    fun refreshIfStale() {
        val last = fetchedAt ?: return
        if (Duration.between(last, Instant.now()) > Duration.ofHours(1)) refresh()
    }

    fun useMyLocation() {
        usingGps = true
        settings.usingGps = true
        refresh()
    }

    fun choosePlace(p: Place) {
        usingGps = false
        settings.usingGps = false
        rememberPlace(p)
        refresh()
    }

    private fun rememberPlace(p: Place) {
        place = p
        settings.place = p
        if (draft.place == null) draft = draft.copy(place = p)
    }

    /** Set when location permission is missing, so Today can offer to allow it. */
    var needsLocationPermission by mutableStateOf(false)
        private set

    /** Set when Android won't ask for permission again, so Today offers the app's settings instead. */
    var locationBlocked by mutableStateOf(false)
        private set

    /** What was tried when no location reading came back, shown in small print under the problem. */
    var locationDetail by mutableStateOf<String?>(null)
        private set

    private var refusals = 0

    /**
     * Location permission was refused. Android asks twice at most: after the second "Don't allow" it
     * refuses on its own, so from then on the only way back is the app's page in Settings.
     */
    fun locationDenied() {
        refusals++
        needsLocationPermission = true
        locationBlocked = refusals >= 2
        message = "No location permission, so pick a place instead."
        if (usingGps && place == null) {
            error = "What to Wear isn't allowed to use your location. " +
                if (locationBlocked) "Tap Open settings, then Permissions, then Location. Or tap Change to pick a place."
                else "Tap Allow location, or tap Change to pick a place."
        }
    }

    /** Starts a log pre-filled with today's suggestion, so logging is a couple of taps. */
    fun startLogFromSuggestion(items: List<ClothingItem>) {
        draft = LogDraft(place = place, itemIds = items.map { it.id }.toSet())
    }

    fun editDraft(change: (LogDraft) -> LogDraft) {
        draft = change(draft)
    }

    fun useMyLocationForDraft() {
        viewModelScope.launch {
            val here = LocationHelper.here(getApplication())
            if (here != null) editDraft { it.copy(place = here) } else message = "Couldn't find your location."
        }
    }

    fun saveDraft() {
        val d = draft
        val p = d.place ?: run { message = "Choose where you were."; return }
        if (d.itemIds.isEmpty()) { message = "Tick what you wore."; return }
        val feeling = d.feeling ?: run { message = "Say how it felt."; return }
        viewModelScope.launch {
            saving = true
            try {
                // "Now" is always today, even if the app was left open past midnight.
                val date = if (d.timeOfDay == TimeOfDay.NOW) LocalDate.now() else d.date
                val weather = WeatherClient.forDate(p.latitude, p.longitude, date).summarise(date, d.timeOfDay)
                    ?: throw Problem("Couldn't get the weather for that time.")
                val log = WearLog(
                    id = System.currentTimeMillis(),
                    date = date.toString(),
                    timeOfDay = d.timeOfDay,
                    placeName = p.name,
                    latitude = p.latitude,
                    longitude = p.longitude,
                    // Keep the wardrobe's order, so logs read top to bottom.
                    itemIds = Wardrobe.items.map { it.id }.filter { it in d.itemIds },
                    feeling = feeling,
                    weather = weather,
                    note = d.note.trim(),
                )
                val uri = noteUri
                withContext(Dispatchers.IO) {
                    if (uri != null) {
                        // Once the note is connected it's the only store. If it can't be read, don't save elsewhere.
                        if (noteProblem != null) throw Problem(noteProblem!!)
                        noteStore.addRow(uri, log.toNoteValues())
                    } else {
                        store.save(store.load() + log)
                    }
                }
                draft = LogDraft(place = p)
                reloadEntries()
                // The widget now shows what you're wearing.
                WidgetUpdates.refreshNow(getApplication())
                message = if (uri != null) "Saved to \"$noteName\"." else "Saved."
                savedCount++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = friendly(e)
            } finally {
                saving = false
            }
        }
    }

    /** Adds your own piece of clothing to its group, and ticks it on the log you're filling in. */
    fun addCustomClothing(rawName: String, slot: Slot, level: WarmthLevel, keepsRainOff: Boolean) {
        // Names are written into the note's table as a comma-separated list, so commas and pipes would break it.
        val name = rawName.replace(",", " ").replace("|", " ").replace(Regex("\\s+"), " ").trim()
        val added = CustomClothing(Wardrobe.newId(name), name, slot, level.warmth, keepsRainOff)
        saveCustomClothes(customClothes + added)
        editDraft { it.copy(itemIds = it.itemIds + added.id) }
        message = "Added $name."
    }

    /** Logs that included it keep its ID but no longer count it. */
    fun removeCustomClothing(item: CustomClothing) {
        saveCustomClothes(customClothes - item)
        editDraft { it.copy(itemIds = it.itemIds - item.id) }
    }

    private fun saveCustomClothes(clothes: List<CustomClothing>) {
        // Update the shared list first, so screens that redraw because of the state change see it.
        Wardrobe.custom = clothes.map { it.toItem() }
        customClothes = clothes
        clothesStore.save(clothes)
    }

    fun deleteEntry(entry: Entry) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val uri = noteUri
                    if (uri != null) noteStore.removeRow(uri, entry.key)
                    else store.save(store.load().filterNot { "log:${it.id}" == entry.key })
                }
                reloadEntries()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = e.message ?: "Couldn't delete that log."
            }
        }
    }

    /** Only used before the note is connected; after that, the note is the export. */
    fun exportJson(): String = store.toJson(store.load())

    suspend fun searchPlaces(query: String): List<Place> = WeatherClient.searchPlaces(query)

    private fun friendly(e: Exception) = when (e) {
        is Problem, is NoTableException -> e.message.orEmpty()
        is IOException -> "Couldn't reach the weather service. Check your internet and try again."
        else -> "Something went wrong: ${e.message}"
    }

    private class Problem(message: String) : Exception(message)
}
