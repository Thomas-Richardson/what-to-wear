package com.richie.whattowear.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.richie.whattowear.BuildConfig
import com.richie.whattowear.model.AiOutfit
import com.richie.whattowear.model.OutfitReader
import com.richie.whattowear.model.Wardrobe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/**
 * Asks OpenAI's gpt-6.1-terra what Richie is wearing, from a photo or his own words. Only called
 * when he taps a button, and nothing is kept afterwards. The key is one he pastes on the History tab:
 * it lives only on his phone (see Settings.aiKey), never in the app file or the repository.
 */
object AiClient {
    private val json = Json { ignoreUnknownKeys = true }

    /** Set from Settings when the app starts, and again when he saves or removes a key. */
    @Volatile var key: String = ""

    val isSetUp: Boolean get() = key.isNotBlank()

    suspend fun fromDescription(words: String): AiOutfit = ask(words, null)

    suspend fun fromPhoto(jpeg: ByteArray): AiOutfit = ask(null, Base64.getEncoder().encodeToString(jpeg))

    private suspend fun ask(words: String?, photoBase64: String?): AiOutfit = withContext(Dispatchers.IO) {
        if (!isSetUp) throw IOException("The AI isn't set up yet. Add your OpenAI key on the History tab, then try again.")
        val clothes = Wardrobe.items
        val reply = post(OutfitReader.request(BuildConfig.AI_MODEL, clothes, words, photoBase64))
        val content = json.parseToJsonElement(reply).jsonObject["choices"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
            ?: throw IOException("The AI sent back an empty answer.")
        OutfitReader.parse(content, clothes)
    }

    private fun post(body: String): String {
        val connection = URL("https://api.openai.com/v1/chat/completions").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000
        connection.setRequestProperty("Authorization", "Bearer $key")
        connection.setRequestProperty("Content-Type", "application/json")
        try {
            connection.outputStream.use { it.write(body.toByteArray()) }
            val code = connection.responseCode
            if (code == 401) throw IOException("OpenAI refused the app's key. It may have been switched off.")
            if (code == 429) throw IOException("OpenAI says too many requests, or the account is out of credit.")
            if (code !in 200..299) throw IOException("OpenAI said $code.")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

/** Shrinks a photo to a sensible size for the AI, the right way up. */
object Photos {
    private const val LONGEST_SIDE = 1024

    fun forAi(context: Context, uri: Uri): ByteArray {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= LONGEST_SIDE) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: throw IOException("Couldn't open that photo.")
        // Phone cameras often save photos sideways with a note saying which way is up.
        val turn = resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val scale = minOf(1f, LONGEST_SIDE.toFloat() / maxOf(decoded.width, decoded.height))
        val matrix = Matrix().apply { postScale(scale, scale); postRotate(turn) }
        val ready = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        return ByteArrayOutputStream().also { ready.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
    }
}
