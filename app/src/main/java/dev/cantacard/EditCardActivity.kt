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
        val catalogRepository = app.catalog
        val initialStoreId = intent.getStringExtra(EXTRA_STORE_ID)
        val initialName = intent.getStringExtra(EXTRA_NAME)
        val hasCamera = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

        setContent {
            val cards by repository.cards.collectAsState()
            val catalog by catalogRepository.catalog.collectAsState()
            val loaded = cards ?: return@setContent
            val stores = catalog ?: return@setContent
            val original = editId?.let { id -> loaded.firstOrNull { it.id == id } }
            if (editId != null && original == null) {
                // Tessera eliminata nel frattempo.
                LaunchedEffect(Unit) { finish() }
                return@setContent
            }
            CantaCardTheme {
                EditCardScreen(
                    original = original,
                    catalog = stores,
                    initialStoreId = initialStoreId,
                    initialName = initialName,
                    hasCamera = hasCamera,
                    onSave = { card ->
                        repository.save(card)
                        setResult(RESULT_OK)
                        finish()
                    },
                    onClose = ::finish,
                )
            }
        }
    }

    companion object {
        private const val EXTRA_CARD_ID = "dev.cantacard.extra.EDIT_CARD_ID"

        private const val EXTRA_STORE_ID = "dev.cantacard.extra.EDIT_STORE_ID"
        private const val EXTRA_NAME = "dev.cantacard.extra.EDIT_NAME"

        /** Nuova tessera, con il negozio scelto dal catalogo (`null` = "Altro negozio"). */
        fun add(context: Context, storeId: String? = null, name: String? = null): Intent =
            Intent(context, EditCardActivity::class.java)
                .putExtra(EXTRA_STORE_ID, storeId)
                .putExtra(EXTRA_NAME, name)

        fun edit(context: Context, cardId: String): Intent =
            Intent(context, EditCardActivity::class.java).putExtra(EXTRA_CARD_ID, cardId)
    }
}
