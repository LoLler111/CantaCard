package dev.cantacard

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.cantacard.ui.CantaCardTheme
import dev.cantacard.ui.list.CardListScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Finita la schermata di avvio verde (Theme.CantaCard.Launch), torna lo sfondo normale.
        setTheme(R.style.Theme_CantaCard)
        // Intestazione sempre scura (gradiente del marchio): icone di sistema chiare in alto.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val repository = app.cards
        setContent {
            CantaCardTheme {
                CardListScreen(
                    repository = repository,
                    onOpen = { card -> startActivity(ShowCardActivity.intent(this, card.id)) },
                    onAdd = { startActivity(StorePickerActivity.add(this)) },
                )
            }
        }
    }
}
