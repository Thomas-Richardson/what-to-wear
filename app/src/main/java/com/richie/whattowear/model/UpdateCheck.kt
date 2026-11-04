package com.richie.whattowear.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** A version of the app published on GitHub: its number, where its app file is, and what's new. */
data class Release(val version: String, val apkUrl: String, val apkBytes: Long, val notes: String)

/**
 * Works out whether there's a newer version on the app's GitHub page. Each version Richie ships is a
 * GitHub "release" with the app file attached, built and signed on his Mac.
 */
object UpdateCheck {
    const val REPO = "Thomas-Richardson/what-to-wear"
    const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"

    /** The app only ever downloads from this repository's releases. */
    private const val DOWNLOADS = "https://github.com/$REPO/releases/download/"

    private val json = Json { ignoreUnknownKeys = true }

    /** Reads GitHub's "latest release" reply. Null if it has no app file from this repository attached. */
    fun parse(reply: String): Release? {
        val release = json.parseToJsonElement(reply).jsonObject
        val tag = release["tag_name"]?.jsonPrimitive?.contentOrNull ?: return null
        val apk = release["assets"]?.jsonArray?.map { it.jsonObject }?.firstOrNull { asset ->
            asset["name"]?.jsonPrimitive?.contentOrNull.orEmpty().endsWith(".apk") &&
                asset["browser_download_url"]?.jsonPrimitive?.contentOrNull.orEmpty().startsWith(DOWNLOADS)
        } ?: return null
        return Release(
            version = tag.removePrefix("v"),
            apkUrl = apk["browser_download_url"]!!.jsonPrimitive.content,
            apkBytes = apk["size"]?.jsonPrimitive?.longOrNull ?: 0,
            notes = release["body"]?.jsonPrimitive?.contentOrNull.orEmpty().trim(),
        )
    }

    /** Compares version numbers part by part, as numbers: 0.10 is newer than 0.9, and 0.12 than 0.11. */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parts(version: String) =
        version.trim().removePrefix("v").split(".").map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
}
