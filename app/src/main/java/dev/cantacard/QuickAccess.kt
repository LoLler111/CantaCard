package dev.cantacard

import android.content.Context
import dev.cantacard.data.Card
import dev.cantacard.data.CardQuery
import dev.cantacard.data.CardRepository
import dev.cantacard.image.ImageStore
import dev.cantacard.shortcuts.CardShortcuts
import dev.cantacard.widget.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Tiene allineati scorciatoie e widget con le tessere: li ridisegna solo quando cambia qualcosa che
 * si vede (nome, colore, immagine, preferita, ordine), non a ogni avvio dell'app.
 */
class QuickAccess(
    private val context: Context,
    private val repository: CardRepository,
    private val images: ImageStore,
    private val scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences("quick_access", Context.MODE_PRIVATE)

    fun start() {
        scope.launch(Dispatchers.IO) {
            repository.cards.filterNotNull().collectLatest { cards ->
                delay(DEBOUNCE_MS)
                val key = visibleKey(cards)
                if (key != prefs.getInt(KEY, 0)) {
                    CardShortcuts.update(context, cards, images)
                    Widgets.updateAll(context, cards, images)
                    prefs.edit().putInt(KEY, key).apply()
                }
            }
        }
    }

    private companion object {
        const val KEY = "visibleKey"
        const val DEBOUNCE_MS = 800L

        fun visibleKey(cards: List<Card>): Int =
            CardQuery.sorted(cards).map { listOf(it.id, it.name, it.color, it.image, it.favorite) }.hashCode()
    }
}
