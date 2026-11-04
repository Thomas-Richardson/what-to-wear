package com.richie.whattowear.data

import android.content.Context
import com.richie.whattowear.model.CustomClothing
import com.richie.whattowear.model.Place
import com.richie.whattowear.model.WearLog
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Keeps logs inside the app, only until the temperatures note is connected. Connecting moves them
 * into the note and retires this file, so there's never a second copy in use.
 */
class LogStore(context: Context) {
    private val file = File(context.filesDir, "wear_logs.json")
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun load(): List<WearLog> {
        if (!file.exists()) return emptyList()
        return try {
            json.decodeFromString(file.readText())
        } catch (e: Exception) {
            // Never throw logs away: put the unreadable file aside so it can be rescued.
            file.renameTo(File(file.parentFile, "wear_logs.unreadable-${System.currentTimeMillis()}.json"))
            emptyList()
        }
    }

    fun save(logs: List<WearLog>) {
        // Write to a spare file first, so a crash half-way through can't corrupt the real one.
        val spare = File(file.parentFile, "wear_logs.json.tmp")
        spare.writeText(toJson(logs))
        spare.renameTo(file)
    }

    fun toJson(logs: List<WearLog>): String = json.encodeToString(logs)

    /** After the logs have moved into the note: renamed, never read again, kept only in case of trouble. */
    fun retire() {
        if (file.exists()) file.renameTo(File(file.parentFile, "wear_logs.moved-to-note-${System.currentTimeMillis()}.json"))
    }
}

/** The clothes you've added yourself, in their own small JSON file. */
class CustomClothesStore(context: Context) {
    private val file = File(context.filesDir, "custom_clothes.json")
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun load(): List<CustomClothing> = try {
        if (file.exists()) json.decodeFromString(file.readText()) else emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    fun save(clothes: List<CustomClothing>) {
        val spare = File(file.parentFile, "custom_clothes.json.tmp")
        spare.writeText(json.encodeToString(clothes))
        spare.renameTo(file)
    }
}

/** Small settings: the place you last chose and whether to follow your location. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** The temperatures note the app reads and writes, and its name. Null until connected. */
    var noteUri: String?
        get() = prefs.getString("note_uri", null)
        set(value) = prefs.edit().putString("note_uri", value).apply()

    var noteName: String?
        get() = prefs.getString("note_name", null)
        set(value) = prefs.edit().putString("note_name", value).apply()

    /**
     * Richie's OpenAI key for "Fill in with AI", pasted on the History tab. It's kept only in the app's
     * private storage on the phone, so the app file itself holds no secrets and can be published.
     */
    var aiKey: String
        get() = prefs.getString("ai_key", "").orEmpty()
        set(value) = prefs.edit().putString("ai_key", value.trim()).apply()

    /** The 5pm reminder to log. On unless switched off on the History tab. */
    var reminderOn: Boolean
        get() = prefs.getBoolean("reminder_on", true)
        set(value) = prefs.edit().putBoolean("reminder_on", value).apply()

    /** Whether the app has already asked for permission to show notifications, so it asks only once. */
    var askedForNotifications: Boolean
        get() = prefs.getBoolean("asked_notifications", false)
        set(value) = prefs.edit().putBoolean("asked_notifications", value).apply()

    var usingGps: Boolean
        get() = prefs.getBoolean("using_gps", true)
        set(value) = prefs.edit().putBoolean("using_gps", value).apply()

    var place: Place?
        get() {
            val name = prefs.getString("place_name", null) ?: return null
            val lat = prefs.getString("place_lat", null)?.toDoubleOrNull() ?: return null
            val lon = prefs.getString("place_lon", null)?.toDoubleOrNull() ?: return null
            return Place(name, lat, lon)
        }
        set(value) {
            prefs.edit().apply {
                if (value == null) {
                    remove("place_name").remove("place_lat").remove("place_lon")
                } else {
                    putString("place_name", value.name)
                    putString("place_lat", value.latitude.toString())
                    putString("place_lon", value.longitude.toString())
                }
            }.apply()
        }
}
