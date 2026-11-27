package dev.cantacard.data

import java.text.Collator
import java.text.Normalizer
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Ordine della lista scelto dall'utente, separato per le preferite e per le altre tessere;
 * le preferite restano sempre in cima.
 */
enum class SortOrder {
    /** Usate più di recente prima (predefinito). */
    RECENT,

    /** In ordine alfabetico. */
    NAME,

    /** Nell'ordine deciso dall'utente trascinando le tessere ([Card.rank]). */
    MANUAL;

    companion object {
        fun fromName(name: String?): SortOrder = entries.firstOrNull { it.name == name } ?: RECENT
    }
}

/** Ordinamento e ricerca della lista tessere (funzioni pure, testate in JVM). */
object CardQuery {

    private val collator: Collator = Collator.getInstance(Locale.ITALIAN).apply {
        strength = Collator.PRIMARY
    }
    private val diacritics = Regex("\\p{Mn}+")

    /**
     * Preferite in cima, ordinate secondo [favoritesOrder], poi le altre secondo [order]: per uso più
     * recente (le mai usate per nome), per nome, o nell'ordine personalizzato (le mai spostate in
     * fondo, dalla più vecchia).
     */
    fun sorted(cards: List<Card>, order: SortOrder = SortOrder.RECENT, favoritesOrder: SortOrder = order): List<Card> {
        val (favorites, others) = cards.partition { it.favorite }
        return favorites.sortedWith(comparator(favoritesOrder)) + others.sortedWith(comparator(order))
    }

    private fun comparator(order: SortOrder): Comparator<Card> = when (order) {
        SortOrder.RECENT -> compareByDescending<Card> { it.lastUsed }.thenBy(collator) { it.name }
        SortOrder.NAME -> compareBy<Card, String>(collator) { it.name }.thenBy { it.createdAt }
        SortOrder.MANUAL -> compareBy<Card> { it.rank == null }
            .thenBy { it.rank ?: 0.0 }
            .thenBy { it.createdAt }
            .thenBy(collator) { it.name }
    }

    /**
     * Tessere per scorciatoie e widget: le archiviate no. Preferite nel loro ordine
     * ([favoritesOrder]), poi le altre per uso recente.
     */
    fun quickAccess(cards: List<Card>, favoritesOrder: SortOrder = SortOrder.RECENT): List<Card> =
        sorted(cards.filterNot { it.archived }, SortOrder.RECENT, favoritesOrder)

    /**
     * Posizioni da dare a un gruppo di tessere (preferite o altre) quando passa all'ordine
     * personalizzato: se nessuna ne ha ancora una, restano nell'ordine in cui si vedono ora
     * ([group]); altrimenti niente (quelle senza posizione vanno in fondo). Id → posizione.
     */
    fun freezeOrder(group: List<Card>): Map<String, Double> =
        if (group.any { it.rank != null }) emptyMap()
        else group.mapIndexed { index, card -> card.id to (index + 1).toDouble() }.toMap()

    /**
     * Cerca nel nome (senza distinguere maiuscole e accenti), nelle etichette e nel numero
     * (ignorando gli spazi).
     */
    fun filter(cards: List<Card>, query: String): List<Card> {
        val q = normalize(query.trim())
        if (q.isEmpty()) return cards
        val digits = q.replace(" ", "")
        return cards.filter { card ->
            normalize(card.name).contains(q) ||
                card.labels.any { normalize(it).contains(q) } ||
                card.number.replace(" ", "").lowercase(Locale.ROOT).contains(digits)
        }
    }

    /** Tutte le etichette usate, in ordine alfabetico, senza doppioni (maiuscole ignorate). */
    fun labels(cards: List<Card>): List<String> =
        Card.cleanLabels(cards.flatMap { it.labels }).sortedWith(collator)

    /** Le tessere con l'etichetta [label] (maiuscole ignorate). */
    fun withLabel(cards: List<Card>, label: String): List<Card> =
        cards.filter { card -> card.labels.any { it.equals(label, ignoreCase = true) } }

    /**
     * Nuove posizioni dopo aver spostato la tessera da [from] a [to] in [ordered] (l'ordine che si
     * vede). Di solito cambia solo la tessera spostata (posizione a metà tra le vicine); se le
     * vicine non hanno ancora una posizione, o non c'è più spazio tra i numeri, si rinumera tutto
     * [ordered]. Restituisce solo le posizioni cambiate: id → nuova posizione.
     */
    fun reorder(ordered: List<Card>, from: Int, to: Int): Map<String, Double> {
        if (from == to || from !in ordered.indices || to !in ordered.indices) return emptyMap()
        val list = ordered.toMutableList()
        val moved = list.removeAt(from)
        list.add(to, moved)
        val before = list.getOrNull(to - 1)
        val after = list.getOrNull(to + 1)
        val ranked = list.all { it.rank != null }
        if (ranked) {
            val low = before?.rank
            val high = after?.rank
            val rank = when {
                low != null && high != null -> (low + high) / 2
                low != null -> low + 1.0
                high != null -> high - 1.0
                else -> 0.0
            }
            val roomy = (low == null || rank > low) && (high == null || rank < high)
            if (roomy) return mapOf(moved.id to rank)
        }
        return list.mapIndexedNotNull { index, card ->
            val rank = (index + 1).toDouble()
            if (card.rank != rank) card.id to rank else null
        }.toMap()
    }

    /** Giorni che mancano alla scadenza (0 = scade oggi, negativo = scaduta), `null` se non ha scadenza. */
    fun daysToExpiry(card: Card, today: LocalDate): Long? =
        card.expiryDate?.let { ChronoUnit.DAYS.between(today, it) }

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
