package dev.cantacard

import android.content.Context
import dev.cantacard.data.SortOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Preferenze della lista che restano su questo telefono (non vanno nel backup né nella
 * sincronizzazione): per ora l'ordine delle tessere.
 */
class ListPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("list", Context.MODE_PRIVATE)
    private val _sortOrder = MutableStateFlow(SortOrder.fromName(prefs.getString(KEY_SORT, null)))

    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
        prefs.edit().putString(KEY_SORT, order.name).apply()
    }

    private companion object {
        const val KEY_SORT = "sort"
    }
}
