package dev.cantacard.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Contenuto del file di backup (funzioni pure, testate in JVM). Lo zip contiene:
 * - `cantacard.json` = `{"formatVersion": 1, "exportedAt": <epoch ms>, "cards": [...]}`, con lo
 *   stesso schema per tessera di `cards.json` ([CardJson]);
 * - `images/<idTessera>.webp` per le tessere con immagine (nel JSON `"image": "images/<id>.webp"`).
 *
 * La lettura tollera versioni future: campi sconosciuti ignorati, tessere illeggibili saltate una
 * per una, [Content.isNewer] per avvisare l'utente.
 */
object BackupFormat {

    const val FORMAT_VERSION = 1
    const val JSON_ENTRY = "cantacard.json"

    private val imageEntry = Regex("images/([A-Za-z0-9-]{1,64})\\.webp")

    class Content(
        val formatVersion: Int,
        val exportedAt: Long,
        /** Tessere lette; `image` è il nome della voce nello zip (o `null`). */
        val cards: List<Card>,
        /** Tessere scartate perché illeggibili o con id ripetuto. */
        val skipped: Int,
    ) {
        val isNewer: Boolean get() = formatVersion > FORMAT_VERSION
    }

    fun imageEntryFor(cardId: String): String = "images/$cardId.webp"

    /** `true` per i nomi di voce accettati come immagini (niente percorsi strani). */
    fun isImageEntry(name: String): Boolean = imageEntry.matches(name)

    /** @param hasImage se la tessera ha davvero un file immagine da mettere nello zip. */
    fun encode(cards: List<Card>, exportedAt: Long, hasImage: (Card) -> Boolean): String {
        val array = JSONArray()
        for (card in cards) {
            val json = CardJson.toJson(card)
            json.remove("image")
            if (card.image != null && hasImage(card)) json.put("image", imageEntryFor(card.id))
            array.put(json)
        }
        return JSONObject()
            .put("formatVersion", FORMAT_VERSION)
            .put("exportedAt", exportedAt)
            .put("cards", array)
            .toString()
    }

    /** @throws JSONException se il testo non è un backup (non è un oggetto JSON con le tessere). */
    fun decode(text: String): Content {
        val root = JSONObject(text)
        if (!root.has("cards")) throw JSONException("Nessuna tessera nel backup")
        val decoded = CardJson.decode(text)
        val cards = decoded.cards.map { card ->
            // Accetta solo riferimenti a immagini dentro lo zip.
            if (card.image != null && !isImageEntry(card.image)) card.copy(image = null) else card
        }
        return Content(
            formatVersion = root.optInt("formatVersion", 1),
            exportedAt = root.optLong("exportedAt", 0L),
            cards = cards,
            skipped = decoded.skipped,
        )
    }
}

enum class RestoreMode {
    /** Aggiunge le tessere del backup senza doppioni; quelle del telefono restano com'erano. */
    ADD,

    /** Le tessere del telefono vengono sostituite da quelle del backup. */
    REPLACE,
}

object BackupMerge {

    class Result(
        /** Elenco finale delle tessere. */
        val cards: List<Card>,
        /** Tessere del backup entrate nell'elenco. */
        val added: List<Card>,
        /** Tessere del backup lasciate fuori perché già presenti. */
        val duplicates: Int,
    )

    /**
     * Con [RestoreMode.ADD] una tessera del backup è un doppione se nel telefono c'è già lo stesso
     * id oppure lo stesso numero con lo stesso formato: in quel caso vince la versione del telefono.
     */
    fun merge(current: List<Card>, incoming: List<Card>, mode: RestoreMode): Result {
        if (mode == RestoreMode.REPLACE) return Result(incoming, incoming, 0)
        val ids = current.mapTo(HashSet()) { it.id }
        val codes = current.mapTo(HashSet(), ::codeKey)
        val added = ArrayList<Card>()
        var duplicates = 0
        for (card in incoming) {
            if (card.id in ids || codeKey(card) in codes) {
                duplicates++
            } else {
                added += card
                ids += card.id
                codes += codeKey(card)
            }
        }
        return Result(current + added, added, duplicates)
    }

    private fun codeKey(card: Card): String = card.formatName + "|" + card.number.trim()
}
