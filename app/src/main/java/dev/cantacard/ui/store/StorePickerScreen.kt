package dev.cantacard.ui.store

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cantacard.R
import dev.cantacard.data.CardColors
import dev.cantacard.data.Catalog
import dev.cantacard.data.CatalogRepository.UpdateResult
import dev.cantacard.data.Store
import dev.cantacard.data.StoreQuery
import dev.cantacard.ui.Brand
import dev.cantacard.ui.SectionLabelStyle
import dev.cantacard.ui.components.SearchField
import dev.cantacard.ui.contentColorOn
import dev.cantacard.ui.headerColor
import kotlinx.coroutines.launch

/** Scelta fatta: un negozio del catalogo, oppure "Altro negozio" ([storeId] `null`). */
data class StoreChoice(val storeId: String?, val name: String?)

/**
 * Elenco dei negozi con ricerca (stile Google Wallet). "Altro negozio" è sempre in cima,
 * anche durante la ricerca: se il negozio non c'è, il testo cercato diventa il nome.
 */
@Composable
fun StorePickerScreen(
    catalog: Catalog?,
    /** Negozio attuale, evidenziato solo se [showSelection] (cambio negozio di una tessera). */
    selectedId: String?,
    showSelection: Boolean,
    onPick: (StoreChoice) -> Unit,
    onBack: () -> Unit,
    onUpdate: suspend () -> UpdateResult,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val stores = remember(catalog, query) { catalog?.let { StoreQuery.search(it.stores, query) } }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val resources = LocalContext.current.resources
    val trimmed = query.trim()

    fun update() {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            launch { snackbar.showSnackbar(resources.getString(R.string.catalog_updating)) }
            val result = onUpdate()
            snackbar.currentSnackbarData?.dismiss()
            val message = when (result) {
                UpdateResult.UPDATED -> resources.getString(R.string.catalog_updated)
                UpdateResult.UP_TO_DATE -> resources.getString(R.string.catalog_up_to_date)
                UpdateResult.FAILED -> resources.getString(R.string.catalog_update_failed)
            }
            snackbar.showSnackbar(message)
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            Header(
                query = query,
                onQueryChange = { query = it },
                onBack = onBack,
                onUpdate = ::update,
            )
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                item(key = "other") {
                    OtherStoreRow(
                        query = trimmed,
                        selected = showSelection && selectedId == null,
                        onClick = {
                            focusManager.clearFocus()
                            onPick(StoreChoice(storeId = null, name = trimmed.ifEmpty { null }))
                        },
                    )
                }
                if (stores != null) {
                    if (stores.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                text = stringResource(R.string.store_no_results, trimmed),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
                            )
                        }
                    } else {
                        item(key = "section") {
                            Text(
                                text = stringResource(R.string.store_section).uppercase(),
                                style = SectionLabelStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
                            )
                        }
                        items(stores, key = { it.id }) { store ->
                            StoreRow(
                                store = store,
                                selected = showSelection && store.id == selectedId,
                                onClick = {
                                    focusManager.clearFocus()
                                    onPick(StoreChoice(storeId = store.id, name = store.name))
                                },
                            )
                        }
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding(),
        )
    }
}


@Composable
private fun Header(
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onUpdate: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(headerColor),
    ) {
        Column(
            Modifier
                .statusBarsPadding()
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back), tint = Color.White)
                }
                Text(
                    text = stringResource(R.string.store_picker_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                )
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.more_options), tint = Color.White)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.catalog_update)) },
                            onClick = {
                                menu = false
                                onUpdate()
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            SearchField(
                query = query,
                onQueryChange = onQueryChange,
                placeholder = R.string.store_search_hint,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}

@Composable
private fun OtherStoreRow(query: String, selected: Boolean, onClick: () -> Unit) {
    PickerRow(onClick = onClick, selected = selected) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Brand.Coral),
        ) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = Color.White)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.store_other), style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (query.isEmpty()) stringResource(R.string.store_other_hint)
                else stringResource(R.string.store_other_named, query),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StoreRow(store: Store, selected: Boolean, onClick: () -> Unit) {
    PickerRow(onClick = onClick, selected = selected) {
        StoreBadge(store, 44.dp)
        Spacer(Modifier.width(14.dp))
        Text(
            text = store.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PickerRow(onClick: () -> Unit, selected: Boolean, content: @Composable RowScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) colors.primaryContainer else colors.surfaceContainerLowest,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) { content() }
            if (selected) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = stringResource(R.string.store_selected),
                    tint = colors.primary,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

/** Tondo pieno col colore del negozio e la sua iniziale (nessun logo incluso nell'APK). */
@Composable
fun StoreBadge(store: Store, size: Dp, modifier: Modifier = Modifier) {
    val background = Color(store.color ?: CardColors.forName(store.name))
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background),
    ) {
        Text(
            text = store.initial,
            color = contentColorOn(background),
            fontWeight = FontWeight.Black,
            fontSize = (size.value * 0.42f).sp,
        )
    }
}
