package dev.cantacard.data

import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID

/** Una tessera fedeltà. Immutabile: le modifiche passano da [CardRepository]. */
data class Card(
    val id: String,
    val name: String,
    /** Contenuto del codice, esattamente come va codificato. */
    val number: String,
    /**
     * Nome del formato (vedi [CodeFormat]). Resta una stringa per conservare anche formati
     * sconosciuti a questa versione dell'app (es. da un backup più recente).
     */
    val formatName: String,
    /** Colore ARGB della tessera, usato per lo sfondo dell'iniziale. */
    val color: Int,
    /** Id del negozio nel catalogo, se scelto da lì. */
    val storeId: String? = null,
    /** Nome del file immagine in `files/images/`, se presente. */
    val image: String? = null,
    val favorite: Boolean = false,
    /** Ultima apertura (epoch millis), 0 se mai aperta. */
    val lastUsed: Long = 0L,
    val useCount: Int = 0,
    val createdAt: Long = 0L,
    val note: String? = null,
    /**
     * Ultima modifica fatta dall'utente (epoch millis), 0 se sconosciuta (dati di versioni
     * precedenti). Non cambia quando la tessera viene solo mostrata in cassa. Decide quale versione
     * vince tra backup e telefono, e tra telefoni sincronizzati.
     */
    val updatedAt: Long = 0L,
    /** Foto del retro della tessera in `files/images/`, se presente (dalla 0.2.0). */
    val backImage: String? = null,
    /** Scadenza della tessera o del buono, data ISO `yyyy-MM-dd` (dalla 0.2.0). */
    val expiry: String? = null,
    /** Etichette scelte dall'utente, senza doppioni (dalla 0.2.0). */
    val labels: List<String> = emptyList(),
    /** Archiviata: non compare nella lista principale, in scorciatoie e widget (dalla 0.2.0). */
    val archived: Boolean = false,
    /**
     * Posizione nell'ordine personalizzato (più piccola = prima), `null` se mai spostata. Numero
     * con la virgola: spostare una tessera tra due altre cambia solo lei (dalla 0.2.0).
     */
    val rank: Double? = null,
) {
    val format: CodeFormat? get() = CodeFormat.fromName(formatName)

    /** [expiry] come data, `null` se manca o non è valida. */
    val expiryDate: LocalDate? get() = parseDate(expiry)

    /** Lettera o cifra mostrata quando non c'è un'immagine. */
    val initial: String get() = initialOf(name)

    companion object {
        fun newId(): String = UUID.randomUUID().toString()

        fun initialOf(name: String): String =
            name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"

        fun parseDate(text: String?): LocalDate? = try {
            text?.let(LocalDate::parse)
        } catch (e: DateTimeParseException) {
            null
        }

        /** Etichette pulite: spazi tolti, vuote e doppioni (senza distinguere maiuscole) scartati. */
        fun cleanLabels(labels: List<String>): List<String> {
            val seen = HashSet<String>()
            return labels.map { it.trim().replace(spaces, " ").take(MAX_LABEL_LENGTH) }
                .filter { it.isNotEmpty() && seen.add(it.lowercase()) }
        }

        const val MAX_LABEL_LENGTH = 30
        private val spaces = Regex("\\s+")
    }
}
