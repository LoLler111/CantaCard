package dev.cantacard.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Contenuto del file di backup (funzioni pure, testate in JVM). Lo zip contiene:
 * - `cantacard.json` = `{"formatVersion": 1, "exportedAt": <epoch ms>, "cards": [...]}`, con lo
 *   stesso schema per tessera di `cards.json` ([CardJson]);
 * - `images/<idTessera>.webp` per le tessere con immagine (nel JSON `"image": "images/<id>.webp"`);
 * - dalla 0.2.0 `images/<idTessera>-back.webp` per la foto del retro (`"backImage"`): le versioni
 *   precedenti accettano la voce ma la ignorano.
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
        /** Tessere cancellate (solo nel file di sincronizzazione). */
        val deleted: Map<String, Long> = emptyMap(),
    ) {
        val isNewer: Boolean get() = formatVersion > FORMAT_VERSION
    }

    fun imageEntryFor(cardId: String): String = "images/$cardId.webp"

    fun backImageEntryFor(cardId: String): String = "images/$cardId-back.webp"

    /** `true` per i nomi di voce accettati come immagini (niente percorsi strani). */
    fun isImageEntry(name: String): Boolean = imageEntry.matches(name)

    /** @param hasEntry se la voce dello zip (immagine o retro) c'è davvero. */
    fun encode(
        cards: List<Card>,
        exportedAt: Long,
        deleted: Map<String, Long> = emptyMap(),
        hasEntry: (String) -> Boolean,
    ): String {
        val array = JSONArray()
        for (card in cards) {
            val json = CardJson.toJson(card)
            json.remove("image")
            json.remove("backImage")
            if (card.image != null && hasEntry(imageEntryFor(card.id))) json.put("image", imageEntryFor(card.id))
            if (card.backImage != null && hasEntry(backImageEntryFor(card.id))) {
                json.put("backImage", backImageEntryFor(card.id))
            }
            array.put(json)
        }
        val root = JSONObject()
            .put("formatVersion", FORMAT_VERSION)
            .put("exportedAt", exportedAt)
            .put("cards", array)
        CardJson.putDeleted(root, deleted)
        return root.toString()
    }

    /** @throws JSONException se il testo non è un backup (non è un oggetto JSON con le tessere). */
    fun decode(text: String): Content {
        val root = JSONObject(text)
        if (!root.has("cards")) throw JSONException("Nessuna tessera nel backup")
        val decoded = CardJson.decode(text)
        val cards = decoded.cards.map { card ->
            // Accetta solo riferimenti a immagini dentro lo zip.
            card.copy(
                image = card.image?.takeIf(::isImageEntry),
                backImage = card.backImage?.takeIf(::isImageEntry),
            )
        }
        return Content(
            formatVersion = root.optInt("formatVersion", 1),
            exportedAt = root.optLong("exportedAt", 0L),
            cards = cards,
            skipped = decoded.skipped,
            deleted = decoded.deleted,
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
        /** Tessere nuove arrivate dal backup. */
        val added: Int,
        /** Tessere già presenti sostituite dalla versione del backup, più recente. */
        val updated: Int,
        /** Tessere del backup lasciate fuori: già presenti e non più recenti, o doppioni. */
        val duplicates: Int,
    )

    /**
     * Con [RestoreMode.ADD]:
     * - stesso id: è la stessa tessera, vince la versione modificata più di recente (`updatedAt`;
     *   a parità, anche 0 per i dati vecchi, vince il telefono). Le statistiche d'uso si uniscono;
     * - id diverso ma stesso numero e formato (la stessa tessera aggiunta a mano su due telefoni):
     *   doppione, resta quella del telefono.
     */
    fun merge(current: List<Card>, incoming: List<Card>, mode: RestoreMode): Result {
        if (mode == RestoreMode.REPLACE) return Result(incoming, incoming.size, 0, 0)
        val byId = LinkedHashMap<String, Card>()
        current.forEach { byId[it.id] = it }
        val codes = current.mapTo(HashSet(), ::codeKey)
        var added = 0
        var updated = 0
        var duplicates = 0
        for (card in incoming) {
            val mine = byId[card.id]
            when {
                mine != null && card.updatedAt > mine.updatedAt -> {
                    byId[card.id] = card.copy(
                        lastUsed = maxOf(card.lastUsed, mine.lastUsed),
                        useCount = maxOf(card.useCount, mine.useCount),
                    )
                    codes += codeKey(card)
                    updated++
                }
                mine != null || codeKey(card) in codes -> duplicates++
                else -> {
                    byId[card.id] = card
                    codes += codeKey(card)
                    added++
                }
            }
        }
        return Result(byId.values.toList(), added, updated, duplicates)
    }

    private fun codeKey(card: Card): String = card.formatName + "|" + card.number.trim()
}
