package dev.cantacard.ui.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cantacard.BuildConfig
import dev.cantacard.R
import dev.cantacard.data.Card
import dev.cantacard.data.CardQuery
import dev.cantacard.data.CardRepository
import dev.cantacard.data.SampleCards

/** Rapporto larghezza/altezza di una carta di credito (ISO/IEC 7810 ID-1). */
private const val CARD_RATIO = 1.586f

@Composable
fun CardListScreen(
    repository: CardRepository,
    onOpen: (Card) -> Unit,
) {
    val cards by repository.cards.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val visible = remember(cards, query) {
        cards?.let { CardQuery.filter(CardQuery.sorted(it), query) }
    }

    Scaffold(
        topBar = {
            SearchField(
                query = query,
                onQueryChange = { query = it },
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        },
    ) { padding ->
        val all = cards
        when {
            // Caricamento: dura pochi millisecondi, lasciamo lo sfondo vuoto.
            all == null || visible == null -> Unit
            all.isEmpty() -> EmptyState(
                modifier = Modifier.padding(padding),
                onAddSamples = if (BuildConfig.DEBUG) {
                    { repository.addAll(SampleCards.create()) }
                } else {
                    null
                },
            )
            visible.isEmpty() -> CenteredText(
                text = stringResource(R.string.list_no_results),
                modifier = Modifier.padding(padding),
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(visible, key = { it.id }) { card ->
                    CardTile(
                        card = card,
                        onClick = { onOpen(card) },
                        // Provvisorio finché non c'è il menu della tessera.
                        onLongClick = { repository.toggleFavorite(card.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.search_clear),
                    )
                }
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CardTile(
    card: Card,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val background = Color(card.color)
    val content = if (background.luminance() > 0.5f) Color.Black else Color.White
    val favoriteLabel = stringResource(R.string.card_favorite)

    Column {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(CARD_RATIO)
                .clip(RoundedCornerShape(12.dp))
                .background(background)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        ) {
            Text(
                text = card.initial,
                color = content,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
            )
            if (card.favorite) {
                Icon(
                    painter = painterResource(R.drawable.ic_star),
                    contentDescription = favoriteLabel,
                    tint = content,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(18.dp),
                )
            }
        }
        Text(
            text = card.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 6.dp, start = 2.dp, end = 2.dp)
                .semantics { if (card.favorite) contentDescription = "${card.name}, $favoriteLabel" },
        )
    }
}

@Composable
private fun EmptyState(onAddSamples: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
    ) {
        Text(
            text = stringResource(R.string.list_empty_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.list_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        if (onAddSamples != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onAddSamples) { Text(stringResource(R.string.debug_add_samples)) }
        }
    }
}

@Composable
private fun CenteredText(text: String, modifier: Modifier = Modifier) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxSize()) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
