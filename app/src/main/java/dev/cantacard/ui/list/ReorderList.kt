package dev.cantacard.ui.list

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.cantacard.R
import dev.cantacard.app
import dev.cantacard.data.Card
import dev.cantacard.data.SortOrder
import dev.cantacard.image.rememberCardImage
import dev.cantacard.ui.SectionLabelStyle
import dev.cantacard.ui.components.CardArt
import kotlin.math.roundToInt

private val RowHeight = 72.dp
private val RowGap = 8.dp

/**
 * Riordino delle tessere trascinandole per la maniglia (senza librerie). Le preferite restano
 * sopra: si riordinano tra loro, e le altre tra loro. [onMove] riceve il gruppo e lo spostamento.
 */
@Composable
fun ReorderList(
    favorites: List<Card>,
    others: List<Card>,
    onMove: (group: List<Card>, from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(RowGap),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
    ) {
        Text(
            stringResource(R.string.reorder_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
        if (favorites.isNotEmpty()) {
            GroupTitle(R.string.section_favorites)
            ReorderGroup(favorites, onMove)
            GroupTitle(R.string.section_all)
        }
        ReorderGroup(others, onMove)
    }
}

@Composable
private fun GroupTitle(title: Int) {
    Text(
        text = stringResource(title).uppercase(),
        style = SectionLabelStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

@Composable
private fun ReorderGroup(cards: List<Card>, onMove: (List<Card>, Int, Int) -> Unit) {
    // Ordine locale: si aggiorna subito al rilascio, senza aspettare il salvataggio.
    var order by remember(cards) { mutableStateOf(cards) }
    var dragging by remember { mutableStateOf<Int?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val step = with(LocalDensity.current) { (RowHeight + RowGap).toPx() }
    val target = dragging?.let { (it + (offset / step).roundToInt()).coerceIn(order.indices) }

    fun move(from: Int, to: Int) {
        if (from == to || to !in order.indices) return
        val before = order
        order = order.toMutableList().apply { add(to, removeAt(from)) }
        onMove(before, from, to)
    }

    val moveUp = stringResource(R.string.reorder_up)
    val moveDown = stringResource(R.string.reorder_down)
    Column(verticalArrangement = Arrangement.spacedBy(RowGap)) {
        order.forEachIndexed { index, card ->
            key(card.id) {
                val from = dragging
                val shift = when {
                    from == null || target == null -> 0f
                    index == from -> offset
                    target > from && index in (from + 1)..target -> -step
                    target < from && index in target until from -> step
                    else -> 0f
                }
                ReorderRow(
                    card = card,
                    lifted = index == from,
                    modifier = Modifier
                        .zIndex(if (index == from) 1f else 0f)
                        .graphicsLayer { translationY = shift }
                        .semantics {
                            customActions = listOfNotNull(
                                CustomAccessibilityAction(moveUp) { move(index, index - 1); true }.takeIf { index > 0 },
                                CustomAccessibilityAction(moveDown) { move(index, index + 1); true }
                                    .takeIf { index < order.lastIndex },
                            )
                        },
                    handle = Modifier.pointerInput(card.id, index) {
                        detectDragGestures(
                            onDragStart = {
                                dragging = index
                                offset = 0f
                            },
                            onDragEnd = {
                                // Calcolata qui: il blocco del gesto non vede le ricomposizioni.
                                val to = (index + (offset / step).roundToInt()).coerceIn(order.indices)
                                dragging = null
                                offset = 0f
                                move(index, to)
                            },
                            onDragCancel = {
                                dragging = null
                                offset = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                offset += amount.y
                            },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ReorderRow(card: Card, lifted: Boolean, handle: Modifier, modifier: Modifier = Modifier) {
    val images = LocalContext.current.app.images
    val image = rememberCardImage(card.image?.let(images::file))
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = if (lifted) 8.dp else 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(RowHeight),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp)) {
            CardArt(
                name = "",
                initial = card.initial,
                color = Color(card.color),
                favorite = false,
                image = image,
                compact = true,
                corner = 8.dp,
                markSize = 22.dp,
                modifier = Modifier
                    .width(80.dp)
                    .height(50.dp),
            )
            Spacer(Modifier.width(14.dp))
            Text(
                card.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(contentAlignment = Alignment.Center, modifier = handle.size(56.dp)) {
                Icon(
                    painterResource(R.drawable.ic_drag),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Scelta dell'ordine della lista; "Personalizzato" porta subito al riordino. */
@Composable
fun SortDialog(current: SortOrder, onChoose: (SortOrder) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sort_title)) },
        text = {
            Column {
                listOf(
                    SortOrder.RECENT to R.string.sort_recent,
                    SortOrder.NAME to R.string.sort_name,
                    SortOrder.MANUAL to R.string.sort_manual,
                ).forEach { (order, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = order == current, role = Role.RadioButton) { onChoose(order) }
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = order == current, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Text(
                    stringResource(R.string.sort_favorites_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}
