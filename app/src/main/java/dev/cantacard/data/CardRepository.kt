package dev.cantacard.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Tessere più quelle cancellate (id → ora), che servono alla sincronizzazione. */
data class CardSnapshot(val cards: List<Card>, val deleted: Map<String, Long> = emptyMap())

/**
 * Unica fonte delle tessere in memoria. Carica il file all'avvio in background e
 * serializza tutte le scritture. La UI osserva [cards].
 */
class CardRepository(
    private val store: CardStore,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val _cards = MutableStateFlow<List<Card>?>(null)

    /** Tessere cancellate, protette da [mutex]. */
    private var deleted: Map<String, Long> = emptyMap()

    /** `null` finché il file non è stato letto. */
    val cards: StateFlow<List<Card>?> = _cards.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            mutex.withLock { if (_cards.value == null) load() }
        }
    }

    fun find(id: String): Card? = _cards.value?.firstOrNull { it.id == id }

    /** Aggiunge la tessera o sostituisce quella con lo stesso id (segna l'ora della modifica). */
    fun save(card: Card): Job = update { snapshot ->
        val edited = card.copy(updatedAt = clock())
        val list = snapshot.cards
        val cards = if (list.any { it.id == card.id }) list.map { if (it.id == card.id) edited else it } else list + edited
        CardSnapshot(cards, snapshot.deleted - card.id)
    }

    fun addAll(cards: List<Card>): Job = update { snapshot ->
        val ids = snapshot.cards.mapTo(HashSet()) { it.id }
        val now = clock()
        val added = cards.filter { it.id !in ids }.map { if (it.updatedAt == 0L) it.copy(updatedAt = now) else it }
        CardSnapshot(snapshot.cards + added, snapshot.deleted - added.map { it.id }.toSet())
    }

    /** Elimina la tessera e ricorda la cancellazione, così non ricompare da un altro telefono. */
    fun delete(id: String): Job = update { snapshot ->
        CardSnapshot(snapshot.cards.filterNot { it.id == id }, snapshot.deleted + (id to clock()))
    }

    fun toggleFavorite(id: String): Job = modify(id) { it.copy(favorite = !it.favorite, updatedAt = clock()) }

    fun setArchived(id: String, archived: Boolean): Job = modify(id) { it.copy(archived = archived, updatedAt = clock()) }

    /** Nuove posizioni nell'ordine personalizzato (id → posizione), vedi [CardQuery.reorder]. */
    fun setRanks(ranks: Map<String, Double>): Job = update { snapshot ->
        if (ranks.isEmpty()) return@update snapshot
        val now = clock()
        snapshot.copy(
            cards = snapshot.cards.map { card -> ranks[card.id]?.let { card.copy(rank = it, updatedAt = now) } ?: card },
        )
    }

    /** Da chiamare quando la tessera viene mostrata in cassa. */
    fun markUsed(id: String): Job = modify(id) {
        it.copy(lastUsed = clock(), useCount = it.useCount + 1)
    }

    private fun modify(id: String, change: (Card) -> Card): Job = update { snapshot ->
        snapshot.copy(cards = snapshot.cards.map { if (it.id == id) change(it) else it })
    }

    /**
     * Applica [transform] allo stato attuale e aspetta che sia salvato (ripristino, sincronizzazione).
     * @return il valore calcolato da [transform] insieme al nuovo stato.
     */
    suspend fun <T> replace(transform: (CardSnapshot) -> Pair<CardSnapshot, T>): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            val (next, result) = transform(current())
            commit(next)
            result
        }
    }

    private fun update(transform: (CardSnapshot) -> CardSnapshot): Job =
        scope.launch(Dispatchers.IO) {
            mutex.withLock { commit(transform(current())) }
        }

    private fun current(): CardSnapshot {
        if (_cards.value == null) load()
        return CardSnapshot(_cards.value.orEmpty(), deleted)
    }

    private fun load() {
        val snapshot = store.read()
        deleted = snapshot.deleted
        _cards.value = snapshot.cards
    }

    private fun commit(next: CardSnapshot) {
        // Le cancellate più vecchie di un anno non servono più: ogni telefono ha avuto il tempo di saperlo.
        val horizon = clock() - DELETED_KEEP_MS
        deleted = next.deleted.filterValues { it >= horizon }
        _cards.value = next.cards
        try {
            store.write(CardSnapshot(next.cards, deleted))
        } catch (e: IOException) {
            Log.e(TAG, "Salvataggio tessere fallito", e)
        }
    }

    private companion object {
        const val TAG = "CardRepository"
        val DELETED_KEEP_MS: Long = TimeUnit.DAYS.toMillis(365)
    }
}
