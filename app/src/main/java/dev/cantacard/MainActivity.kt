package dev.cantacard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.cantacard.ui.CantaCardTheme
import dev.cantacard.ui.list.CardListScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val repository = app.cards
        setContent {
            CantaCardTheme {
                CardListScreen(
                    repository = repository,
                    onOpen = { card -> startActivity(ShowCardActivity.intent(this, card.id)) },
                    onAdd = { startActivity(EditCardActivity.add(this)) },
                )
            }
        }
    }
}
