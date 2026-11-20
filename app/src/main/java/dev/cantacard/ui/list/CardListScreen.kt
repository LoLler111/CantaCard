package dev.cantacard.ui.list

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.cantacard.BuildConfig
import dev.cantacard.R
import dev.cantacard.app
import dev.cantacard.image.rememberCardImage
import dev.cantacard.data.Card
import dev.cantacard.data.CardQuery
import dev.cantacard.data.CardRepository
import dev.cantacard.data.SampleCards
import dev.cantacard.data.SortOrder
import dev.cantacard.ui.components.CardBadge
import dev.cantacard.ui.edit.Pill
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import dev.cantacard.ui.Brand
import dev.cantacard.ui.SectionLabelStyle
import dev.cantacard.ui.components.CARD_RATIO
import dev.cantacard.ui.components.CardArt
import dev.cantacard.ui.components.SearchField
import dev.cantacard.ui.headerColor
import dev.cantacard.update.AppRelease

@Composable
fun CardListScreen(
    repository: CardRepository,
    sortOrder: SortOrder,
    onSortChange: (SortOrder) -> Unit,
    onOpen: (Card) -> Unit,
    onAdd: () -> Unit,
    onExport: () -> Unit,
    onRestore: () -> Unit,
    onSync: () -> Unit,
    onImport: () -> Unit,
    onReport: () -> Unit,
    update: AppRelease?,
    onUpdate: (AppRelease) -> Unit,
) {
    val cards by repository.cards.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    // Filtro sotto l'intestazione: un'etichetta, oppure le archiviate.
    var label by rememberSaveable { mutableStateOf<String?>(null) }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var reordering by rememberSaveable { mutableStateOf(false) }
    var sortDialog by rememberSaveable { mutableStateOf(false) }
    val active = remember(cards) { cards?.filterNot { it.archived } }
    val archivedCount = cards?.count { it.archived } ?: 0
    val labels = remember(active) { active?.let(CardQuery::labels).orEmpty() }
    // Un'etichetta non più usata (o le archiviate finite) non lascia la lista vuota.
    if (label != null && labels.none { it.equals(label, ignoreCase = true) }) label = null
    if (showArchived && archivedCount == 0) showArchived = false
    if (reordering && sortOrder != SortOrder.MANUAL) reordering = false
    val visible = remember(cards, query, label, showArchived, sortOrder) {
        cards?.let { all ->
            val base = if (showArchived) all.filter { it.archived } else all.filterNot { it.archived }
            val labelled = label?.let { CardQuery.withLabel(base, it) } ?: base
            CardQuery.filter(CardQuery.sorted(labelled, sortOrder), query)
        }
    }
    val all = cards

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(Modifier.fillMaxSize()) {
            Header(
                count = active?.size,
                query = query,
                onQueryChange = { query = it },
                showSearch = !all.isNullOrEmpty() && !reordering,
                canExport = !all.isNullOrEmpty(),
                reordering = reordering,
                canReorder = sortOrder == SortOrder.MANUAL && !active.isNullOrEmpty(),
                onDone = { reordering = false },
                onSort = { sortDialog = true },
                onReorder = {
                    query = ""
                    label = null
                    showArchived = false
                    reordering = true
                },
                onExport = onExport,
                onRestore = onRestore,
                onSync = onSync,
                onImport = onImport,
                onReport = onReport,
            )
            // Fissa sotto l'intestazione, fuori dalla griglia: arriva dopo che la lista è già
            // mostrata, e come primo elemento della griglia finiva sopra la parte visibile.
            // Nascosta durante una ricerca.
            if (all != null && query.isBlank() && !reordering) {
                UpdateRow(update, onUpdate, Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp))
                if (labels.isNotEmpty() || archivedCount > 0) {
                    FilterRow(
                        labels = labels,
                        archivedCount = archivedCount,
                        label = label,
                        showArchived = showArchived,
                        onAll = {
                            label = null
                            showArchived = false
                        },
                        onLabel = {
                            label = it
                            showArchived = false
                        },
                        onArchived = {
                            label = null
                            showArchived = true
                        },
                    )
                }
            }
            when {
                // Caricamento: dura pochi millisecondi.
                all == null || visible == null -> Unit
                all.isEmpty() -> EmptyState(
                    onAdd = onAdd,
                    onRestore = onRestore,
                    onImport = onImport,
                    onAddSamples = if (BuildConfig.DEBUG) {
                        { repository.addAll(SampleCards.create()) }
                    } else {
                        null
                    },
                )
                reordering -> {
                    val sorted = CardQuery.sorted(all.filterNot { it.archived }, SortOrder.MANUAL)
                    ReorderList(
                        favorites = sorted.filter { it.favorite },
                        others = sorted.filterNot { it.favorite },
                        onMove = { group, from, to -> repository.setRanks(CardQuery.reorder(group, from, to)) },
                    )
                }
                visible.isEmpty() && query.isNotBlank() -> NoResults(query)
                visible.isEmpty() -> NoCardsHere()
                else -> CardGrid(cards = visible, grouped = query.isBlank() && !showArchived, onOpen = onOpen)
            }
        }

        if (sortDialog) {
            SortDialog(
                current = sortOrder,
                onChoose = { order ->
                    sortDialog = false
                    onSortChange(order)
                    if (order == SortOrder.MANUAL && !active.isNullOrEmpty()) {
                        query = ""
                        label = null
                        showArchived = false
                        reordering = true
                    }
                },
                onDismiss = { sortDialog = false },
            )
        }

        if (!all.isNullOrEmpty() && !reordering) {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                shape = RoundedCornerShape(20.dp),
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text(stringResource(R.string.add_card)) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(20.dp),
            )
        }
    }
}

@Composable
private fun Header(
    count: Int?,
    query: String,
    onQueryChange: (String) -> Unit,
    showSearch: Boolean,
    canExport: Boolean,
    reordering: Boolean,
    canReorder: Boolean,
    onDone: () -> Unit,
    onSort: () -> Unit,
    onReorder: () -> Unit,
    onExport: () -> Unit,
    onRestore: () -> Unit,
    onSync: () -> Unit,
    onImport: () -> Unit,
    onReport: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(headerColor),
    ) {
        Column(
            Modifier
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_logo),
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(if (reordering) R.string.reorder_title else R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                if (reordering) {
                    TextButton(onClick = onDone) {
                        Text(stringResource(R.string.reorder_done), color = Color.White, style = MaterialTheme.typography.titleMedium)
                    }
                } else Box {
                    var menu by remember { mutableStateOf(false) }
                    IconButton(onClick = { menu = true }) {
                        Icon(
                            painterResource(R.drawable.ic_more_vert),
                            contentDescription = stringResource(R.string.more_options),
                            tint = Color.White,
                        )
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sort_menu)) },
                            enabled = canExport,
                            onClick = {
                                menu = false
                                onSort()
                            },
                        )
                        if (canReorder) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.reorder_menu)) },
                                onClick = {
                                    menu = false
                                    onReorder()
                                },
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.backup_export)) },
                            enabled = canExport,
                            onClick = {
                                menu = false
                                onExport()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.backup_restore)) },
                            onClick = {
                                menu = false
                                onRestore()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sync_menu)) },
                            onClick = {
                                menu = false
                                onSync()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.import_menu)) },
                            onClick = {
                                menu = false
                                onImport()
                            },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.report_menu)) },
                            onClick = {
                                menu = false
                                onReport()
                            },
                        )
                    }
                }
            }
            if (count != null && count > 0 && !reordering) {
                HeaderSubtitle(count, onSync, Modifier.padding(start = 50.dp))
            }
            if (showSearch) {
                Spacer(Modifier.height(16.dp))
                SearchField(query, onQueryChange, placeholder = R.string.search_hint)
            }
        }
    }
}

/**
 * Sotto il nome: numero di tessere e, con la sincronizzazione attiva, quando sono state salvate
 * l'ultima volta nel file; altrimenti l'invito "Fai il backup delle tessere". Tutti e due aprono la
 * sincronizzazione.
 */
@Composable
private fun HeaderSubtitle(count: Int, onSync: () -> Unit, modifier: Modifier = Modifier) {
    // Letto qui e non in MainActivity: il sottotitolo compare a tessere già caricate, quando la
    // sincronizzazione è già stata creata in background da onStart: niente lettura delle
    // preferenze sul thread principale al primo frame.
    val sync by LocalContext.current.app.sync.state.collectAsState()
    val dim = Color.White.copy(alpha = 0.8f)
    val style = MaterialTheme.typography.bodyMedium
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("${pluralStringResource(R.plurals.list_header_count, count, count)} · ", style = style, color = dim)
        Text(
            text = if (sync.enabled) savedText(sync.lastSync) else stringResource(R.string.header_backup_cta),
            style = style,
            color = if (sync.enabled) dim else Color.White,
            fontWeight = if (sync.enabled) null else FontWeight.SemiBold,
            textDecoration = if (sync.enabled) null else TextDecoration.Underline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onSync)
                .padding(horizontal = 2.dp, vertical = 4.dp),
        )
    }
}

/** "salvate oggi alle 14:32", "salvate ieri alle 9:05", "salvate il 17 nov alle 18:40". */
@Composable
private fun savedText(time: Long): String {
    if (time <= 0L) return stringResource(R.string.header_saved_never)
    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    val saved = Instant.ofEpochMilli(time).atZone(zone)
    val today = LocalDate.now(zone)
    val hour = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(saved)
    return when (saved.toLocalDate()) {
        today -> stringResource(R.string.header_saved_today, hour)
        today.minusDays(1) -> stringResource(R.string.header_saved_yesterday, hour)
        else -> {
            val pattern = stringResource(
                if (saved.year == today.year) R.string.header_saved_date_pattern else R.string.header_saved_date_year_pattern,
            )
            stringResource(R.string.header_saved_date, DateTimeFormatter.ofPattern(pattern, locale).format(saved), hour)
        }
    }
}

@Composable
private fun CardGrid(cards: List<Card>, grouped: Boolean, onOpen: (Card) -> Unit) {
    val favorites = if (grouped) cards.filter { it.favorite } else emptyList()
    val others = if (grouped && favorites.isNotEmpty()) cards.filterNot { it.favorite } else cards
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        if (favorites.isNotEmpty()) {
            section(R.string.section_favorites)
            cardItems(favorites, onOpen)
            section(R.string.section_all)
        }
        cardItems(others, onOpen)
    }
}

private fun LazyGridScope.section(title: Int) {
    item(key = "section-$title", span = { GridItemSpan(maxLineSpan) }) {
        Text(
            text = stringResource(title).uppercase(),
            style = SectionLabelStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .animateItem()
                .padding(start = 4.dp, top = 12.dp),
        )
    }
}

private fun LazyGridScope.cardItems(cards: List<Card>, onOpen: (Card) -> Unit) {
    items(cards, key = { it.id }) { card ->
        CardTile(card = card, onClick = { onOpen(card) }, modifier = Modifier.animateItem())
    }
}

/** Avviso di scadenza per la tessera: scaduta, o entro 30 giorni. */
@Composable
private fun expiryBadge(card: Card): CardBadge? {
    val today = remember { LocalDate.now() }
    val days = CardQuery.daysToExpiry(card, today) ?: return null
    return when {
        days < 0 -> CardBadge(stringResource(R.string.expiry_badge_expired), alert = true)
        days == 0L -> CardBadge(stringResource(R.string.expiry_badge_today), alert = true)
        days <= EXPIRY_WARNING_DAYS -> CardBadge(
            pluralStringResource(R.plurals.expiry_badge_days, days.toInt(), days.toInt()),
            alert = false,
        )
        else -> null
    }
}

private const val EXPIRY_WARNING_DAYS = 30

/** Filtri sotto l'intestazione: tutte, ogni etichetta, le archiviate. */
@Composable
private fun FilterRow(
    labels: List<String>,
    archivedCount: Int,
    label: String?,
    showArchived: Boolean,
    onAll: () -> Unit,
    onLabel: (String) -> Unit,
    onArchived: () -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
    ) {
        item(key = "all") {
            Pill(stringResource(R.string.filter_all), onAll, selected = label == null && !showArchived)
        }
        items(labels, key = { "label-$it" }) { name ->
            Pill(name, { onLabel(name) }, icon = R.drawable.ic_label, selected = name.equals(label, ignoreCase = true))
        }
        if (archivedCount > 0) {
            item(key = "archived") {
                Pill(
                    stringResource(R.string.filter_archived, archivedCount),
                    onArchived,
                    icon = R.drawable.ic_archive,
                    selected = showArchived,
                )
            }
        }
    }
}

@Composable
private fun NoCardsHere() {
    Text(
        text = stringResource(R.string.list_filter_empty),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp, start = 32.dp, end = 32.dp),
    )
}

@Composable
private fun CardTile(card: Card, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "press")
    val color = Color(card.color)
    val images = LocalContext.current.app.images
    val image = rememberCardImage(card.image?.let(images::file))
    val favoriteLabel = stringResource(R.string.card_favorite)
    CardArt(
        name = card.name,
        initial = card.initial,
        color = color,
        favorite = card.favorite,
        image = image,
        badge = expiryBadge(card),
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .fillMaxWidth()
            .aspectRatio(CARD_RATIO)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics {
                contentDescription = if (card.favorite) "${card.name}, $favoriteLabel" else card.name
            },
    )
}

@Composable
private fun EmptyState(onAdd: () -> Unit, onRestore: () -> Unit, onImport: () -> Unit, onAddSamples: (() -> Unit)?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(32.dp),
    ) {
        CardStackIllustration()
        Spacer(Modifier.height(36.dp))
        Text(
            text = stringResource(R.string.list_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.list_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onAdd,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
            ),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 16.dp),
        ) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.list_empty_cta))
        }
        TextButton(onClick = onRestore, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.list_empty_restore))
        }
        TextButton(onClick = onImport) {
            Text(stringResource(R.string.list_empty_import))
        }
        if (onAddSamples != null) {
            TextButton(onClick = onAddSamples, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.debug_add_samples))
            }
        }
    }
}

/** Tre tessere a ventaglio, disegnate con lo stesso componente della lista. */
@Composable
private fun CardStackIllustration() {
    val width = 170.dp
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(width = 240.dp, height = 150.dp)) {
        listOf(
            Triple(Brand.Coral, -14f, (-34).dp),
            Triple(Brand.Sun, 10f, 34.dp),
            Triple(Brand.Teal700, -2f, 0.dp),
        ).forEach { (color, angle, dx) ->
            CardArt(
                name = "",
                initial = "",
                color = color,
                favorite = false,
                modifier = Modifier
                    .offset(x = dx)
                    .graphicsLayer { rotationZ = angle }
                    .shadow(3.dp, RoundedCornerShape(18.dp))
                    .width(width)
                    .aspectRatio(CARD_RATIO),
            )
        }
    }
}

@Composable
private fun NoResults(query: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 64.dp, start = 32.dp, end = 32.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.list_no_results, query.trim()),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
    }
}
