package dev.cantacard

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.cantacard.ui.CantaCardTheme
import dev.cantacard.ui.edit.EditCardScreen

/** Aggiunta di una nuova tessera o modifica di una esistente ([EXTRA_CARD_ID]). */
class EditCardActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val editId = intent.getStringExtra(EXTRA_CARD_ID)
        val repository = app.cards
        val hasCamera = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

        setContent {
            val cards by repository.cards.collectAsState()
            val loaded = cards ?: return@setContent
            val original = editId?.let { id -> loaded.firstOrNull { it.id == id } }
            if (editId != null && original == null) {
                // Tessera eliminata nel frattempo.
                LaunchedEffect(Unit) { finish() }
                return@setContent
            }
            CantaCardTheme {
                EditCardScreen(
                    original = original,
                    hasCamera = hasCamera,
                    onSave = { card ->
                        repository.save(card)
                        finish()
                    },
                    onClose = ::finish,
                )
            }
        }
    }

    companion object {
        private const val EXTRA_CARD_ID = "dev.cantacard.extra.EDIT_CARD_ID"

        fun add(context: Context): Intent = Intent(context, EditCardActivity::class.java)

        fun edit(context: Context, cardId: String): Intent =
            Intent(context, EditCardActivity::class.java).putExtra(EXTRA_CARD_ID, cardId)
    }
}
