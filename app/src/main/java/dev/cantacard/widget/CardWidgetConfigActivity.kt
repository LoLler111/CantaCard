package dev.cantacard.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.cantacard.MainActivity
import dev.cantacard.R
import dev.cantacard.app
import dev.cantacard.data.Card
import dev.cantacard.data.CardQuery
import dev.cantacard.image.rememberCardImage
import dev.cantacard.ui.CantaCardTheme
import dev.cantacard.ui.components.CARD_RATIO
import dev.cantacard.ui.components.CardArt
import dev.cantacard.ui.headerColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Scelta della tessera per il widget di una sola tessera (all'aggiunta o per cambiarla). */
class CardWidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT))
        super.onCreate(savedInstanceState)
        val widgetId = intent.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Se si esce senza scegliere, il launcher non aggiunge il widget.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val repository = app.cards
        setContent {
            CantaCardTheme {
                val cards by repository.cards.collectAsState()
                CardPicker(
                    cards = cards?.let(CardQuery::sorted),
                    onPick = { card -> pick(widgetId, card) },
                    onOpenApp = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    },
                    onClose = ::finish,
                )
            }
        }
    }

    private fun pick(widgetId: Int, card: Card) {
        Widgets.setCardFor(this, widgetId, card.id)
        val app = app
        app.appScope.launch(Dispatchers.IO) {
            Widgets.updateAll(app, app.cards.cards.value.orEmpty(), app.images)
        }
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}

@Composable
private fun CardPicker(cards: List<Card>?, onPick: (Card) -> Unit, onOpenApp: () -> Unit, onClose: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .background(headerColor)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 12.dp),
        ) {
            IconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_close), tint = Color.White)
            }
            Text(
                stringResource(R.string.widget_pick_title),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )
        }
        when {
            cards == null -> Unit
            cards.isEmpty() -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
            ) {
                Text(
                    stringResource(R.string.widget_pick_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onOpenApp) { Text(stringResource(R.string.open_app)) }
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
            ) {
                items(cards, key = { it.id }) { card ->
                    val images = LocalContext.current.app.images
                    Box {
                        CardArt(
                            name = card.name,
                            initial = card.initial,
                            color = Color(card.color),
                            favorite = card.favorite,
                            image = rememberCardImage(card.image?.let(images::file)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(CARD_RATIO)
                                .clickable { onPick(card) },
                        )
                    }
                }
            }
        }
    }
}
