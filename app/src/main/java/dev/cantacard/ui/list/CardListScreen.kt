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
import dev.cantacard.ui.Brand
import dev.cantacard.ui.SectionLabelStyle
import dev.cantacard.ui.components.CARD_RATIO
import dev.cantacard.ui.components.CardArt
import dev.cantacard.ui.components.SearchField
import dev.cantacard.ui.headerColor

@Composable
fun CardListScreen(
    repository: CardRepository,
    onOpen: (Card) -> Unit,
    onAdd: () -> Unit,
    onExport: () -> Unit,
    onRestore: () -> Unit,
    onSync: () -> Unit,
) {
    val cards by repository.cards.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val visible = remember(cards, query) {
        cards?.let { CardQuery.filter(CardQuery.sorted(it), query) }
    }
    val all = cards

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(Modifier.fillMaxSize()) {
            Header(
                count = all?.size,
                query = query,
                onQueryChange = { query = it },
                showSearch = !all.isNullOrEmpty(),
                canExport = !all.isNullOrEmpty(),
                onExport = onExport,
                onRestore = onRestore,
                onSync = onSync,
            )
            when {
                // Caricamento: dura pochi millisecondi.
                all == null || visible == null -> Unit
                all.isEmpty() -> EmptyState(
                    onAdd = onAdd,
                    onRestore = onRestore,
                    onAddSamples = if (BuildConfig.DEBUG) {
                        { repository.addAll(SampleCards.create()) }
                    } else {
                        null
                    },
                )
                visible.isEmpty() -> NoResults(query)
                else -> CardGrid(cards = visible, grouped = query.isBlank(), onOpen = onOpen)
            }
        }

        if (!all.isNullOrEmpty()) {
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
    onExport: () -> Unit,
    onRestore: () -> Unit,
    onSync: () -> Unit,
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
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                Box {
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
                    }
                }
            }
            if (count != null && count > 0) {
                Text(
                    text = pluralStringResource(R.plurals.list_header_count, count, count),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 2.dp, start = 50.dp),
                )
            }
            if (showSearch) {
                Spacer(Modifier.height(16.dp))
                SearchField(query, onQueryChange, placeholder = R.string.search_hint)
            }
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
private fun EmptyState(onAdd: () -> Unit, onRestore: () -> Unit, onAddSamples: (() -> Unit)?) {
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
