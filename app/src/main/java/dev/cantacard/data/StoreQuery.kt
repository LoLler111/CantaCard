package dev.cantacard.data

import java.text.Collator
import java.util.Locale

/** Ordinamento e ricerca nel catalogo (funzioni pure, testate in JVM). */
object StoreQuery {

    private val collator: Collator = Collator.getInstance(Locale.ITALIAN).apply {
        strength = Collator.PRIMARY
    }

    fun sorted(stores: List<Store>): List<Store> = stores.sortedWith(compareBy(collator) { it.name })

    /**
     * Cerca in nome e alias senza distinguere maiuscole, accenti, spazi e punteggiatura
     * ("acqua e sapone" trova "Acqua & Sapone"). Prima chi inizia con il testo cercato,
     * poi chi lo contiene; a parità in ordine alfabetico.
     */
    fun search(stores: List<Store>, query: String): List<Store> {
        val q = key(query)
        if (q.isEmpty()) return sorted(stores)
        return stores.mapNotNull { store ->
            val keys = listOf(store.name) + store.aliases
            val rank = keys.minOf { name ->
                val k = key(name)
                when {
                    k.startsWith(q) -> 0
                    k.contains(q) -> 1
                    else -> 2
                }
            }
            if (rank < 2) rank to store else null
        }.sortedWith(compareBy<Pair<Int, Store>> { it.first }.thenBy(collator) { it.second.name })
            .map { it.second }
    }

    /** Solo lettere e cifre, senza accenti, minuscole; "&" vale come "e". */
    fun key(text: String): String =
        CardQuery.normalize(text.replace("&", "e")).filter { it.isLetterOrDigit() }
}
