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

    /** `null` finché il file non è stato letto. */
    val cards: StateFlow<List<Card>?> = _cards.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            mutex.withLock { if (_cards.value == null) _cards.value = store.read() }
        }
    }

    fun find(id: String): Card? = _cards.value?.firstOrNull { it.id == id }

    /** Aggiunge la tessera o sostituisce quella con lo stesso id (segna l'ora della modifica). */
    fun save(card: Card): Job = update { list ->
        val edited = card.copy(updatedAt = clock())
        if (list.any { it.id == card.id }) list.map { if (it.id == card.id) edited else it }
        else list + edited
    }

    fun addAll(cards: List<Card>): Job = update { list ->
        val ids = list.mapTo(HashSet()) { it.id }
        val now = clock()
        list + cards.filter { it.id !in ids }.map { if (it.updatedAt == 0L) it.copy(updatedAt = now) else it }
    }

    fun delete(id: String): Job = update { list -> list.filterNot { it.id == id } }

    fun toggleFavorite(id: String): Job = modify(id) { it.copy(favorite = !it.favorite, updatedAt = clock()) }

    /** Da chiamare quando la tessera viene mostrata in cassa. */
    fun markUsed(id: String): Job = modify(id) {
        it.copy(lastUsed = clock(), useCount = it.useCount + 1)
    }

    private fun modify(id: String, change: (Card) -> Card): Job = update { list ->
        list.map { if (it.id == id) change(it) else it }
    }

    /**
     * Applica [transform] all'elenco attuale e aspetta che sia salvato (ripristino da backup).
     * @return il valore calcolato da [transform] insieme al nuovo elenco.
     */
    suspend fun <T> replace(transform: (List<Card>) -> Pair<List<Card>, T>): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            val (next, result) = transform(_cards.value ?: store.read())
            commit(next)
            result
        }
    }

    private fun update(transform: (List<Card>) -> List<Card>): Job =
        scope.launch(Dispatchers.IO) {
            mutex.withLock { commit(transform(_cards.value ?: store.read())) }
        }

    private fun commit(next: List<Card>) {
        _cards.value = next
        try {
            store.write(next)
        } catch (e: IOException) {
            Log.e(TAG, "Salvataggio tessere fallito", e)
        }
    }

    private companion object {
        const val TAG = "CardRepository"
    }
}
