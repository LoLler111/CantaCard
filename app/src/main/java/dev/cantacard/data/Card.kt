package dev.cantacard.data

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
) {
    val format: CodeFormat? get() = CodeFormat.fromName(formatName)

    /** Lettera o cifra mostrata quando non c'è un'immagine. */
    val initial: String get() = initialOf(name)

    companion object {
        fun newId(): String = UUID.randomUUID().toString()

        fun initialOf(name: String): String =
            name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"
    }
}
