package com.richie.whattowear.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Something the AI saw or read that isn't on the clothes list, offered for Richie to add. */
data class SuggestedClothing(val name: String, val slot: Slot, val level: WarmthLevel, val keepsRainOff: Boolean)

/** What the AI made of a photo or a description. */
data class AiOutfit(
    /** Ids from the clothes list, in the list's order. */
    val itemIds: List<String>,
    val suggestions: List<SuggestedClothing>,
    /** Anything the AI wasn't sure about, in a few words. Blank if nothing. */
    val unsure: String,
)

/**
 * The instructions sent to the AI, and reading its reply. Kept free of the internet so both can be
 * tested. The reply is never trusted blindly: ids not on the list are dropped, and a garbled reply
 * becomes an error rather than a half-filled log.
 */
object OutfitReader {

    /** The AI's instructions, with the full clothes list (Richie's own clothes included) written in. */
    fun instructions(clothes: List<ClothingItem>): String = """
        You match what someone is wearing to the clothes list of a weather app, so the app can learn what they wear in different weather.

        The clothes list (id: name (group)):
        ${clothes.joinToString("; ") { "${it.id}: ${it.name} (${it.slot.label})" }}

        Reply with JSON only, in exactly this shape:
        {"items": ["id", ...], "new": [{"name": "...", "group": "Top|Layer|Jacket|Bottoms|Extras", "warmth": "barely|light|medium|warm|very warm", "keeps_rain_off": false}], "unsure": "..."}

        Rules:
        - For each piece of clothing, put the id of the closest item in "items". A tweed jacket is a Blazer, chinos are Trousers / jeans, a pea coat is a Warm coat (or a matching item the person added themselves, which is better).
        - Use "new" only for clothing with nothing close on the list, so the person can add it.
        - Leave out shoes, socks, underwear, bags and jewellery: the app doesn't track them.
        - Each piece of clothing appears once. Never invent clothing that isn't mentioned or visible.
        - For a photo, include only what the person is wearing, not clothes lying around or worn by other people.
        - Put anything you're unsure about in "unsure", in a few plain words. Otherwise leave it empty.
    """.trimIndent()

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * The request for OpenAI's chat API: the instructions, then either Richie's description or his
     * photo (as a base64 JPEG). JSON mode makes the model reply with JSON only.
     */
    fun request(model: String, clothes: List<ClothingItem>, description: String?, photoBase64: String?): String =
        buildJsonObject {
            put("model", model)
            putJsonObject("response_format") { put("type", "json_object") }
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", instructions(clothes))
                }
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        addJsonObject {
                            put("type", "text")
                            put("text", if (photoBase64 != null) "What am I wearing in this photo?" else "What I'm wearing: ${description.orEmpty().trim()}")
                        }
                        if (photoBase64 != null) addJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") { put("url", "data:image/jpeg;base64,$photoBase64") }
                        }
                    }
                }
            }
        }.toString()

    /** Reads the AI's reply. Throws [IllegalArgumentException] if it isn't the JSON asked for. */
    fun parse(reply: String, clothes: List<ClothingItem>): AiOutfit {
        // Some models wrap JSON in a code block even when asked not to.
        val text = reply.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: throw IllegalArgumentException("The AI's answer wasn't in the expected format.")
        val known = clothes.map { it.id }
        val picked = (root["items"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet()
        val suggestions = (root["new"] as? JsonArray).orEmpty().mapNotNull { element ->
            val o = element as? JsonObject ?: return@mapNotNull null
            val name = o["name"]?.jsonPrimitive?.contentOrNull?.replace(",", " ")?.replace("|", " ")?.trim()
            if (name.isNullOrEmpty()) return@mapNotNull null
            val slot = slotFor(o["group"]?.jsonPrimitive?.contentOrNull) ?: Slot.EXTRA
            SuggestedClothing(
                name = name,
                slot = slot,
                level = levelFor(o["warmth"]?.jsonPrimitive?.contentOrNull) ?: WarmthLevel.defaultFor(slot),
                keepsRainOff = o["keeps_rain_off"]?.jsonPrimitive?.booleanOrNull ?: false,
            )
        }
            // Don't offer to add something that's already on the list under the same name.
            .filter { s -> clothes.none { it.name.equals(s.name, ignoreCase = true) } }
            .distinctBy { it.name.lowercase() }
        return AiOutfit(
            itemIds = known.filter { it in picked },
            suggestions = suggestions,
            unsure = root["unsure"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty(),
        )
    }

    private fun slotFor(group: String?) = Slot.entries.firstOrNull {
        it.label.equals(group?.trim(), ignoreCase = true) || it.name.equals(group?.trim(), ignoreCase = true)
    } ?: if (group.equals("bottom", ignoreCase = true)) Slot.BOTTOM else null

    private fun levelFor(warmth: String?) = WarmthLevel.entries.firstOrNull { it.label.equals(warmth?.trim(), ignoreCase = true) }
}
