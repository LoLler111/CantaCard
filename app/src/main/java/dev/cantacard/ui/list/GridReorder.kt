package dev.cantacard.ui.list

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.cantacard.data.Card
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Riordino direttamente nella griglia della lista (senza librerie): tenendo premuta una tessera la
 * si trascina tra le altre dello stesso gruppo (preferite o altre tessere), solo se quel gruppo ha
 * l'ordine personalizzato. Le altre tessere si spostano con l'animazione della griglia; al rilascio
 * si salva la nuova posizione con [onMove] (gruppo com'era, da, a), come nel riordino a elenco.
 */
@Stable
internal class GridReorderState(
    val gridState: LazyGridState,
    private val scope: CoroutineScope,
) {
    /** Gruppi come si vedono ora (durante il trascinamento cambiano prima del salvataggio). */
    var favorites by mutableStateOf(emptyList<Card>())
        private set
    var others by mutableStateOf(emptyList<Card>())
        private set

    var draggingId by mutableStateOf<String?>(null)
        private set

    /** Spostamento della tessera trascinata rispetto al suo posto nella griglia. */
    var offset by mutableStateOf(Offset.Zero)
        private set

    var dragFavorites = false
    var dragOthers = false
    var onMove: (List<Card>, Int, Int) -> Unit = { _, _, _ -> }

    private var draggingFavorite = false
    private var startGroup = emptyList<Card>()
    private var startIndex = -1
    private var overscroll = 0f
    private var autoScroll: Job? = null

    /** Nuove tessere dalla lista (anche dopo il salvataggio): un trascinamento in corso si annulla. */
    fun reset(favorites: List<Card>, others: List<Card>) {
        this.favorites = favorites
        this.others = others
        stop()
    }

    /** Inizio dopo la pressione prolungata in [position]; `false` se lì non c'è una tessera spostabile. */
    fun start(position: Offset): Boolean {
        val item = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.contains(position) } ?: return false
        val id = item.key as? String ?: return false
        val favorite = favorites.any { it.id == id }
        val group = when {
            favorite && dragFavorites -> favorites
            !favorite && dragOthers && others.any { it.id == id } -> others
            else -> return false
        }
        draggingFavorite = favorite
        startGroup = group
        startIndex = group.indexOfFirst { it.id == id }
        offset = Offset.Zero
        draggingId = id
        return true
    }

    fun drag(delta: Offset) {
        val id = draggingId ?: return
        offset += delta
        val info = gridState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.key == id } ?: return
        val itemOffset = Offset(item.offset.x.toFloat(), item.offset.y.toFloat())
        val center = itemOffset + offset + Offset(item.size.width / 2f, item.size.height / 2f)
        val group = if (draggingFavorite) favorites else others
        val target = info.visibleItemsInfo.firstOrNull { other ->
            other.key != id && other.contains(center) && group.any { it.id == other.key }
        }
        if (target != null) {
            val from = group.indexOfFirst { it.id == id }
            val to = group.indexOfFirst { it.id == target.key }
            val moved = group.toMutableList().apply { add(to, removeAt(from)) }
            if (draggingFavorite) favorites = moved else others = moved
            // La tessera prende il posto dell'altra: resta sotto il dito.
            offset += itemOffset - Offset(target.offset.x.toFloat(), target.offset.y.toFloat())
        }
        // Vicino ai bordi la griglia scorre da sola.
        val top = item.offset.y + offset.y
        val bottom = top + item.size.height
        overscroll = when {
            bottom > info.viewportSize.height -> bottom - info.viewportSize.height
            top < 0 -> top
            else -> 0f
        }
        if (overscroll != 0f && autoScroll?.isActive != true) {
            autoScroll = scope.launch {
                while (draggingId != null && overscroll != 0f) {
                    val scrolled = gridState.scrollBy((overscroll * 0.2f).coerceIn(-40f, 40f))
                    if (scrolled == 0f) break
                    withFrameNanos { }
                    offset += Offset(0f, scrolled)
                    drag(Offset.Zero)
                }
            }
        }
    }

    /** Rilascio: salva la posizione se è cambiata. */
    fun end() {
        val id = draggingId ?: return
        val to = (if (draggingFavorite) favorites else others).indexOfFirst { it.id == id }
        val from = startIndex
        val group = startGroup
        stop()
        if (from >= 0 && to >= 0 && from != to) onMove(group, from, to)
    }

    fun stop() {
        draggingId = null
        offset = Offset.Zero
        overscroll = 0f
        autoScroll?.cancel()
        autoScroll = null
    }

    private fun LazyGridItemInfo.contains(point: Offset): Boolean =
        point.x >= offset.x && point.x < offset.x + size.width &&
            point.y >= offset.y && point.y < offset.y + size.height
}

@Composable
internal fun rememberGridReorder(
    gridState: LazyGridState,
    favorites: List<Card>,
    others: List<Card>,
    dragFavorites: Boolean,
    dragOthers: Boolean,
    onMove: (List<Card>, Int, Int) -> Unit,
): GridReorderState {
    val scope = rememberCoroutineScope()
    val state = remember(gridState) { GridReorderState(gridState, scope) }
    // Solo quando le tessere cambiano davvero (Card è una data class): dopo il salvataggio la
    // lista arriva già nell'ordine nuovo, senza tornare per un attimo a quello vecchio.
    remember(favorites, others) { state.reset(favorites, others) }
    state.dragFavorites = dragFavorites
    state.dragOthers = dragOthers
    state.onMove = onMove
    return state
}

/**
 * Gesto sulla griglia: pressione prolungata senza muoversi, poi trascinamento. Una volta preso,
 * il gesto si consuma nel primo passaggio: la griglia non scorre e la tessera non si apre al
 * rilascio. Una pressione breve o uno scorrimento restano come prima.
 */
internal fun Modifier.gridReorder(state: GridReorderState, haptic: HapticFeedback): Modifier =
    pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            // null = è scaduto il tempo senza alzare né muovere il dito: pressione prolungata.
            val notLongPress = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes
                        .firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed || change.isConsumed) break
                    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                }
                true
            }
            if (notLongPress != null || !state.start(down.position)) return@awaitEachGesture
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            try {
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes
                        .firstOrNull { it.id == down.id } ?: break
                    val delta = change.positionChange()
                    change.consume()
                    if (!change.pressed) break
                    state.drag(delta)
                }
                state.end()
            } finally {
                state.stop()
            }
        }
    }

/** Tessera trascinata: sopra le altre, un po' più grande, segue il dito. */
internal fun Modifier.dragged(state: GridReorderState): Modifier = this
    .zIndex(1f)
    .graphicsLayer {
        translationX = state.offset.x
        translationY = state.offset.y
        scaleX = 1.06f
        scaleY = 1.06f
        shadowElevation = 8.dp.toPx()
        shape = RoundedCornerShape(18.dp)
    }
