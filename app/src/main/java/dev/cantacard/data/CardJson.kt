package dev.cantacard.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Lettura/scrittura delle tessere in JSON con `org.json` del framework (0 KB di dipendenze).
 *
 * Formato: `{"version": 1, "cards": [ {...}, ... ]}`.
 * La lettura è tollerante: campi sconosciuti ignorati, campi facoltativi mancanti con default,
 * un record non valido viene scartato senza bloccare gli altri. Lo stesso schema per tessera è
 * usato anche nei backup.
 */
object CardJson {

    const val VERSION = 1

    class Decoded(
        val cards: List<Card>,
        /** Record scartati perché illeggibili o duplicati. */
        val skipped: Int,
        /** Versione dichiarata dal documento (può essere più recente di [VERSION]). */
        val version: Int,
        /** Tessere cancellate: id → ora della cancellazione (per la sincronizzazione). */
        val deleted: Map<String, Long> = emptyMap(),
    )

    fun encode(cards: List<Card>, deleted: Map<String, Long> = emptyMap()): String {
        val array = JSONArray()
        cards.forEach { array.put(toJson(it)) }
        val root = JSONObject()
            .put("version", VERSION)
            .put("cards", array)
        putDeleted(root, deleted)
        return root.toString()
    }

    /** `"deleted": [{"id": ..., "at": <epoch ms>}]`, omesso se vuoto. */
    fun putDeleted(root: JSONObject, deleted: Map<String, Long>) {
        if (deleted.isEmpty()) return
        val array = JSONArray()
        deleted.forEach { (id, at) -> array.put(JSONObject().put("id", id).put("at", at)) }
        root.put("deleted", array)
    }

    /** Legge `"deleted"` tollerando voci malformate (saltate). */
    fun readDeleted(root: JSONObject): Map<String, Long> {
        val array = root.optJSONArray("deleted") ?: return emptyMap()
        val result = HashMap<String, Long>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val id = o.optString("id").takeIf { it.isNotEmpty() && !o.isNull("id") } ?: continue
            val at = o.optLong("at", 0L).takeIf { it > 0 } ?: continue
            result[id] = maxOf(at, result[id] ?: 0L)
        }
        return result
    }

    /** @throws JSONException se il testo non è un oggetto JSON. */
    fun decode(text: String): Decoded {
        val root = JSONObject(text)
        val array = root.optJSONArray("cards") ?: JSONArray()
        val seen = HashSet<String>()
        val cards = ArrayList<Card>(array.length())
        var skipped = 0
        for (i in 0 until array.length()) {
            val card = array.optJSONObject(i)?.let(::fromJsonOrNull)
            if (card != null && seen.add(card.id)) cards += card else skipped++
        }
        return Decoded(cards, skipped, root.optInt("version", 1), readDeleted(root))
    }

    fun toJson(card: Card): JSONObject = JSONObject().apply {
        put("id", card.id)
        put("name", card.name)
        put("number", card.number)
        put("format", card.formatName)
        put("color", CardColors.toHex(card.color))
        card.storeId?.let { put("storeId", it) }
        card.image?.let { put("image", it) }
        put("favorite", card.favorite)
        put("lastUsed", card.lastUsed)
        put("useCount", card.useCount)
        put("createdAt", card.createdAt)
        card.note?.let { put("note", it) }
        if (card.updatedAt > 0) put("updatedAt", card.updatedAt)
        // Campi della 0.2.0: scritti solo se diversi dal default, le versioni precedenti li ignorano.
        card.backImage?.let { put("backImage", it) }
        card.expiry?.let { put("expiry", it) }
        if (card.labels.isNotEmpty()) put("labels", JSONArray(card.labels))
        if (card.archived) put("archived", true)
        card.rank?.let { put("rank", it) }
    }

    /** `null` se mancano i campi indispensabili (id, numero, formato) o il record è malformato. */
    fun fromJsonOrNull(o: JSONObject): Card? = try {
        val id = o.string("id")
        val number = o.string("number")
        val format = o.string("format")
        if (id == null || number == null || format == null) {
            null
        } else {
            val name = o.string("name").orEmpty()
            Card(
                id = id,
                name = name,
                number = number,
                formatName = format,
                color = CardColors.parse(o.string("color")) ?: CardColors.forName(name),
                storeId = o.string("storeId"),
                image = o.string("image"),
                favorite = o.optBoolean("favorite", false),
                lastUsed = o.optLong("lastUsed", 0L).coerceAtLeast(0L),
                useCount = o.optInt("useCount", 0).coerceAtLeast(0),
                createdAt = o.optLong("createdAt", 0L).coerceAtLeast(0L),
                note = o.string("note"),
                updatedAt = o.optLong("updatedAt", 0L).coerceAtLeast(0L),
                backImage = o.string("backImage"),
                // Una data non valida (es. da una versione futura con un altro formato) si scarta.
                expiry = o.string("expiry")?.takeIf { Card.parseDate(it) != null },
                labels = readLabels(o),
                archived = o.optBoolean("archived", false),
                rank = o.optDouble("rank", Double.NaN).takeIf { it.isFinite() },
            )
        }
    } catch (e: JSONException) {
        null
    }

    private fun readLabels(o: JSONObject): List<String> {
        val array = o.optJSONArray("labels") ?: return emptyList()
        val labels = ArrayList<String>(array.length())
        for (i in 0 until array.length()) {
            if (!array.isNull(i)) array.optString(i).takeIf { it.isNotEmpty() }?.let(labels::add)
        }
        return Card.cleanLabels(labels).take(MAX_LABELS)
    }

    private const val MAX_LABELS = 50

    /** Stringa non vuota o `null` (evita il `"null"` che `optString` restituisce per JSON null). */
    private fun JSONObject.string(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
}
