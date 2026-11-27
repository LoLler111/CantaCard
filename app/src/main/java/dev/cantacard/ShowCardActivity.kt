package dev.cantacard

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.cantacard.data.Card
import dev.cantacard.data.CardQuery
import dev.cantacard.data.SortOrder
import dev.cantacard.shortcuts.CardShortcuts
import dev.cantacard.ui.CantaCardTheme
import dev.cantacard.ui.card.ShowCardScreen

/**
 * Mostra una tessera da far leggere in cassa. Activity separata e leggera, apribile anche da
 * shortcut, widget e riquadro delle impostazioni rapide: luminosità al massimo e schermo acceso
 * valgono solo per questa finestra e si ripristinano da soli quando si chiude.
 *
 * Scorrendo a destra e a sinistra si passa alle altre tessere, nello stesso ordine della lista
 * (fissato all'apertura: le aperture non rimescolano le pagine). Da una tessera archiviata si
 * scorrono le archiviate.
 */
class ShowCardActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Sfondo sempre bianco anche col tema scuro: icone di sistema scure.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        }

        val requestedId = intent.getStringExtra(EXTRA_CARD_ID)
        if (requestedId == null && !intent.getBooleanExtra(EXTRA_FIRST, false)) {
            finish()
            return
        }
        val repository = app.cards
        val sortOrder = app.listPrefs.sortOrder.value
        val favoritesOrder = app.listPrefs.favoritesOrder.value
        val canPin = CardShortcuts.canPin(this)

        setContent {
            val cards by repository.cards.collectAsState()
            // Ordine delle pagine, deciso una volta sola (resiste alla rotazione).
            var pages by rememberSaveable { mutableStateOf<ArrayList<String>?>(null) }
            // Tessere già contate come usate in questa apertura.
            var used by rememberSaveable { mutableStateOf(ArrayList<String>()) }
            val loaded = cards
            if (loaded != null && pages == null) {
                pages = pagesFor(loaded, requestedId, sortOrder, favoritesOrder)
            }
            val ids = pages ?: return@setContent
            if (ids.isEmpty()) {
                // Riquadro rapido senza tessere: si apre la lista per aggiungerne una.
                LaunchedEffect(Unit) {
                    startActivity(Intent(this@ShowCardActivity, MainActivity::class.java))
                    finish()
                }
                return@setContent
            }
            val start = requestedId?.let(ids::indexOf)?.coerceAtLeast(0) ?: 0
            val pager = rememberPagerState(initialPage = start) { ids.size }

            LaunchedEffect(pager.settledPage) {
                val id = ids[pager.settledPage]
                if (id !in used) {
                    used = ArrayList(used + id)
                    repository.markUsed(id)
                    CardShortcuts.reportUsed(this@ShowCardActivity, id)
                }
            }
            val current = loaded?.firstOrNull { it.id == ids[pager.currentPage] }
            // Tessera inesistente (eliminata, o shortcut vecchia): si chiude.
            LaunchedEffect(loaded != null && current == null) {
                if (loaded != null && current == null && !isFinishing) {
                    Toast.makeText(this@ShowCardActivity, R.string.card_not_found, Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            CantaCardTheme {
                HorizontalPager(state = pager, beyondViewportPageCount = 0, key = { ids[it] }) { page ->
                    val card = loaded?.firstOrNull { it.id == ids[page] } ?: return@HorizontalPager
                    ShowCardScreen(
                        card = card,
                        isCurrent = page == pager.currentPage,
                        onBack = ::finish,
                        onToggleFavorite = { repository.toggleFavorite(card.id) },
                        onEdit = { startActivity(EditCardActivity.edit(this@ShowCardActivity, card.id)) },
                        onDelete = {
                            repository.delete(card.id)
                            app.images.delete(card.image)
                            app.images.delete(card.backImage)
                            finish()
                        },
                        onArchive = { archived ->
                            repository.setArchived(card.id, archived)
                            Toast.makeText(
                                this@ShowCardActivity,
                                if (archived) R.string.archived_done else R.string.unarchived_done,
                                Toast.LENGTH_SHORT,
                            ).show()
                            if (archived) finish()
                        },
                        isSynced = { app.sync.state.value.enabled },
                        onPin = if (canPin) {
                            { CardShortcuts.requestPin(this@ShowCardActivity, card, app.images) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_CARD_ID = "dev.cantacard.extra.CARD_ID"
        private const val EXTRA_FIRST = "dev.cantacard.extra.FIRST_CARD"

        fun intent(context: Context, cardId: String): Intent =
            Intent(context, ShowCardActivity::class.java).putExtra(EXTRA_CARD_ID, cardId)

        /** La prima tessera della lista (preferite, poi le altre), per il riquadro rapido. */
        fun first(context: Context): Intent =
            Intent(context, ShowCardActivity::class.java).putExtra(EXTRA_FIRST, true)

        /**
         * Id delle pagine: le tessere attive (o le archiviate, se si apre un'archiviata) nell'ordine
         * della lista. Una tessera richiesta che non c'è dà un elenco con solo lei (si chiuderà).
         */
        internal fun pagesFor(
            cards: List<Card>,
            requestedId: String?,
            order: SortOrder,
            favoritesOrder: SortOrder = order,
        ): ArrayList<String> {
            val requested = requestedId?.let { id -> cards.firstOrNull { it.id == id } }
            if (requestedId != null && requested == null) return arrayListOf(requestedId)
            val archived = requested?.archived == true
            val pool = CardQuery.sorted(cards.filter { it.archived == archived }, order, favoritesOrder)
            return pool.mapTo(ArrayList()) { it.id }
        }
    }
}
