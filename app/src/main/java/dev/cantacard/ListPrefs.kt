package dev.cantacard

import android.content.Context
import dev.cantacard.data.CardQuery
import dev.cantacard.data.SortOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Preferenze della lista che restano su questo telefono (non vanno nel backup né nella
 * sincronizzazione): l'ordine delle preferite e quello delle altre tessere.
 */
class ListPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("list", Context.MODE_PRIVATE)
    private val _sortOrder = MutableStateFlow(SortOrder.fromName(prefs.getString(KEY_SORT, null)))

    // Prima della 0.3 c'era un solo ordine per tutte: le preferite partono da quello.
    private val _favoritesOrder = MutableStateFlow(
        SortOrder.fromName(prefs.getString(KEY_SORT_FAVORITES, null) ?: prefs.getString(KEY_SORT, null)),
    )

    /** Ordine delle tessere non preferite. */
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    /** Ordine delle preferite: nella lista, nelle scorciatoie, nei widget e nel riquadro rapido. */
    val favoritesOrder: StateFlow<SortOrder> = _favoritesOrder.asStateFlow()

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
        prefs.edit().putString(KEY_SORT, order.name).apply()
    }

    fun setFavoritesOrder(order: SortOrder) {
        _favoritesOrder.value = order
        prefs.edit().putString(KEY_SORT_FAVORITES, order.name).apply()
    }

    private companion object {
        const val KEY_SORT = "sort"
        const val KEY_SORT_FAVORITES = "sortFavorites"
    }
}

/**
 * Cambia l'ordine delle preferite ([favorites]) o delle altre tessere. Passando a "Personalizzato"
 * un gruppo mai ordinato a mano resta nell'ordine in cui si vede ora, invece di rimescolarsi.
 */
fun CantaCardApp.changeOrder(favorites: Boolean, order: SortOrder) {
    val current = if (favorites) listPrefs.favoritesOrder.value else listPrefs.sortOrder.value
    if (order == SortOrder.MANUAL && current != SortOrder.MANUAL) {
        val group = cards.cards.value.orEmpty().filter { !it.archived && it.favorite == favorites }
        cards.setRanks(CardQuery.freezeOrder(CardQuery.sorted(group, current, current)))
    }
    if (favorites) listPrefs.setFavoritesOrder(order) else listPrefs.setSortOrder(order)
}
