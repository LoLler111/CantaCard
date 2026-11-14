package dev.cantacard.data

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/** Ordinamento e ricerca della lista tessere (funzioni pure, testate in JVM). */
object CardQuery {

    private val collator: Collator = Collator.getInstance(Locale.ITALIAN).apply {
        strength = Collator.PRIMARY
    }
    private val diacritics = Regex("\\p{Mn}+")

    /** Preferite in cima, poi per uso più recente, poi (mai usate) per nome. */
    fun sorted(cards: List<Card>): List<Card> = cards.sortedWith(
        compareByDescending<Card> { it.favorite }
            .thenByDescending { it.lastUsed }
            .thenBy(collator) { it.name },
    )

    /** Cerca nel nome (senza distinguere maiuscole e accenti) e nel numero (ignorando gli spazi). */
    fun filter(cards: List<Card>, query: String): List<Card> {
        val q = normalize(query.trim())
        if (q.isEmpty()) return cards
        val digits = q.replace(" ", "")
        return cards.filter { card ->
            normalize(card.name).contains(q) ||
                card.number.replace(" ", "").lowercase(Locale.ROOT).contains(digits)
        }
    }

    /**
     * Una tessera già salvata con lo stesso numero (ignorando spazi e maiuscole) per lo stesso
     * negozio: stesso `storeId` del catalogo oppure, per "Altro negozio", stesso nome. [excludeId]
     * è la tessera in modifica. `null` se non ce n'è.
     */
    fun findDuplicate(
        cards: List<Card>,
        number: String,
        storeId: String?,
        name: String,
        excludeId: String? = null,
    ): Card? {
        val key = compactNumber(number)
        if (key.isEmpty()) return null
        val cardName = normalize(name.trim())
        return cards.firstOrNull { card ->
            card.id != excludeId &&
                compactNumber(card.number) == key &&
                if (storeId != null) card.storeId == storeId else card.storeId == null && normalize(card.name.trim()) == cardName
        }
    }

    private fun compactNumber(number: String): String =
        number.filterNot { it.isWhitespace() }.lowercase(Locale.ROOT)

    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(diacritics, "")
            .lowercase(Locale.ROOT)
}
