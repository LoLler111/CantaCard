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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import dev.cantacard.ui.CantaCardTheme
import dev.cantacard.ui.card.ShowCardScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Mostra una tessera da far leggere in cassa. Activity separata e leggera, apribile anche da
 * shortcut e widget: luminosità al massimo e schermo acceso valgono solo per questa finestra
 * e si ripristinano da soli quando si chiude.
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

        val cardId = intent.getStringExtra(EXTRA_CARD_ID)
        if (cardId == null) {
            finish()
            return
        }
        val repository = app.cards
        if (savedInstanceState == null) {
            lifecycleScope.launch {
                repository.cards.first { it != null }
                repository.markUsed(cardId)
            }
        }

        setContent {
            val cards by repository.cards.collectAsState()
            val card = cards?.firstOrNull { it.id == cardId }
            // Tessera inesistente (eliminata, o shortcut vecchia): si chiude.
            LaunchedEffect(cards != null && card == null) {
                if (cards != null && card == null && !isFinishing) {
                    Toast.makeText(this@ShowCardActivity, R.string.card_not_found, Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            CantaCardTheme {
                if (card != null) {
                    ShowCardScreen(
                        card = card,
                        onBack = ::finish,
                        onToggleFavorite = { repository.toggleFavorite(card.id) },
                        onEdit = { startActivity(EditCardActivity.edit(this@ShowCardActivity, card.id)) },
                        onDelete = {
                            repository.delete(card.id)
                            app.images.delete(card.image)
                            finish()
                        },
                        isSynced = { app.sync.state.value.enabled },
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_CARD_ID = "dev.cantacard.extra.CARD_ID"

        fun intent(context: Context, cardId: String): Intent =
            Intent(context, ShowCardActivity::class.java).putExtra(EXTRA_CARD_ID, cardId)
    }
}
