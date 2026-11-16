package dev.cantacard.data

import java.time.Instant
import java.time.ZoneId

/**
 * Lettura dell'esportazione di Catima (app libera di tessere fedeltà), funzioni pure testate in JVM.
 *
 * Il file `catima.csv` (dentro lo zip esportato da Catima) ha più sezioni separate da righe vuote:
 * la versione (`2`), i gruppi (`_id`), le tessere (`_id,store,note,...,cardid,barcodeid,barcodetype,
 * headercolor,starstatus,lastused,archive`) e i legami tessera-gruppo (`cardId,groupId`). Le colonne
 * si riconoscono dal nome, non dalla posizione, così vanno bene anche le versioni vecchie (senza
 * gruppi) e quelle future con colonne in più.
 */
object CatimaCsv {

    /** Segno di inizio file UTF-8 che alcuni programmi mettono davanti al CSV. */
    private const val BOM = "\uFEFF"

    /** Una tessera letta, con l'id di Catima per ritrovare le sue immagini nello zip. */
    class Entry(val catimaId: String, val card: Card)

    /** `null` se il testo non contiene la sezione delle tessere di Catima. */
    fun parse(text: String, newId: () -> String = Card::newId, zone: ZoneId = ZoneId.systemDefault()): List<Entry>? {
        val records = records(text.removePrefix(BOM))
        val headerIndex = records.indexOfFirst { "store" in it && "cardid" in it && "_id" in it }
        if (headerIndex < 0) return null
        val header = records[headerIndex]
        val groups = groupsByCard(records)
        val entries = ArrayList<Entry>()
        for (record in records.drop(headerIndex + 1)) {
            if (record.isBlankRecord()) break
            val row = header.indices.associate { header[it] to record.getOrElse(it) { "" } }
            entryOf(row, groups, newId, zone)?.let(entries::add)
        }
        return entries
    }

    private fun entryOf(row: Map<String, String>, groups: Map<String, List<String>>, newId: () -> String, zone: ZoneId): Entry? {
        val catimaId = row["_id"].orEmpty().trim()
        val shown = row["cardid"].orEmpty().trim()
        val encoded = row["barcodeid"].orEmpty().trim()
        // In Catima il contenuto del codice può essere diverso dal numero mostrato.
        val number = encoded.ifEmpty { shown }
        if (catimaId.isEmpty() || number.isEmpty()) return null
        val name = row["store"].orEmpty().trim().ifEmpty { shown }
        val format = row["barcodetype"].orEmpty().trim().takeIf { CodeFormat.fromName(it) != null }
            ?: CardInput.guessFormat(number).name
        val note = listOfNotNull(
            row["note"]?.trim()?.takeIf { it.isNotEmpty() },
            shown.takeIf { encoded.isNotEmpty() && it.isNotEmpty() && it != encoded }?.let { "N. $it" },
        ).joinToString("\n").ifEmpty { null }
        val card = Card(
            id = newId(),
            name = name,
            number = number,
            formatName = format,
            color = row["headercolor"]?.trim()?.toLongOrNull()?.toInt() ?: CardColors.forName(name),
            favorite = row["starstatus"]?.trim() == "1",
            lastUsed = millis(row["lastused"]) ?: 0L,
            note = note,
            expiry = millis(row["expiry"])?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }?.toString(),
            labels = Card.cleanLabels(groups[catimaId].orEmpty()),
            archived = row["archive"]?.trim() == "1",
        )
        return Entry(catimaId, card)
    }

    /** Catima salva alcune date in secondi e altre in millisecondi: sotto 10^11 sono secondi. */
    private fun millis(value: String?): Long? {
        val n = value?.trim()?.toLongOrNull()?.takeIf { it > 0 } ?: return null
        return if (n < 100_000_000_000L) n * 1000 else n
    }

    /** Sezione `cardId,groupId`: id tessera → nomi dei gruppi (in Catima l'id del gruppo è il nome). */
    private fun groupsByCard(records: List<List<String>>): Map<String, List<String>> {
        val start = records.indexOfFirst { it.size >= 2 && it[0] == "cardId" && it[1] == "groupId" }
        if (start < 0) return emptyMap()
        val result = LinkedHashMap<String, MutableList<String>>()
        for (record in records.drop(start + 1)) {
            if (record.isBlankRecord()) break
            val card = record.getOrNull(0)?.trim().orEmpty()
            val group = record.getOrNull(1)?.trim().orEmpty()
            if (card.isNotEmpty() && group.isNotEmpty()) result.getOrPut(card) { ArrayList() } += group
        }
        return result
    }

    private fun List<String>.isBlankRecord(): Boolean = all { it.isBlank() }

    /** CSV secondo RFC 4180: virgolette, virgole e a capo dentro i campi tra virgolette. */
    internal fun records(text: String): List<List<String>> {
        val records = ArrayList<List<String>>()
        var record = ArrayList<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                when {
                    c == '"' && text.getOrNull(i + 1) == '"' -> {
                        field.append('"')
                        i++
                    }
                    c == '"' -> quoted = false
                    else -> field.append(c)
                }
            } else {
                when (c) {
                    '"' -> quoted = true
                    ',' -> {
                        record += field.toString()
                        field.clear()
                    }
                    '\r' -> Unit
                    '\n' -> {
                        record += field.toString()
                        field.clear()
                        records += record
                        record = ArrayList()
                    }
                    else -> field.append(c)
                }
            }
            i++
        }
        if (field.isNotEmpty() || record.isNotEmpty()) {
            record += field.toString()
            records += record
        }
        return records
    }

    /** Nome dell'immagine di una tessera nello zip di Catima: `card_<id>_front.png` e simili. */
    fun imageName(catimaId: String, side: String): String = "card_${catimaId}_$side.png"
}
