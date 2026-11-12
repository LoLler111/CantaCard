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

    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(diacritics, "")
            .lowercase(Locale.ROOT)
}
