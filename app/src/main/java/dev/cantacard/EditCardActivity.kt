package dev.cantacard

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.cantacard.barcode.ScannedCode
import dev.cantacard.data.CodeFormat
import dev.cantacard.image.ImageLoader
import dev.cantacard.ui.CantaCardTheme
import dev.cantacard.ui.edit.EditCardScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException

/** Aggiunta di una nuova tessera o modifica di una esistente ([EXTRA_CARD_ID]). */
class EditCardActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val editId = intent.getStringExtra(EXTRA_CARD_ID)
        val repository = app.cards
        val catalogRepository = app.catalog
        val images = app.images
        val initialStoreId = intent.getStringExtra(EXTRA_STORE_ID)
        val initialName = intent.getStringExtra(EXTRA_NAME)
        val initialCrop = intent.getStringExtra(EXTRA_CROP)
        val initialCode = intent.getStringExtra(EXTRA_CODE_TEXT)?.let { text ->
            CodeFormat.fromName(intent.getStringExtra(EXTRA_CODE_FORMAT))?.let { ScannedCode(text, it) }
        }
        val hasCamera = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        // Nuovo modulo: via i file provvisori lasciati da modifiche annullate (non quello condiviso).
        if (savedInstanceState == null) images.clearTemp(except = initialCrop)

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
                    initialCrop = initialCrop,
                    initialCode = initialCode,
                    images = images,
                    hasCamera = hasCamera,
                    onSave = { card, newImage, removeImage ->
                        // L'immagine si sposta fuori dal thread principale; la lista si aggiorna da sola.
                        app.appScope.launch(Dispatchers.IO) {
                            var saved = card
                            try {
                                if (newImage != null) {
                                    saved = card.copy(image = images.commit(card.id, newImage))
                                } else if (removeImage) {
                                    saved = card.copy(image = null)
                                }
                            } catch (e: IOException) {
                                Log.e("EditCardActivity", "Immagine non salvata", e)
                            }
                            if (saved.image != original?.image) {
                                original?.image?.let { old ->
                                    ImageLoader.evict(images.file(old))
                                    images.delete(old)
                                }
                            }
                            repository.save(saved)
                        }
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
        private const val EXTRA_CROP = "dev.cantacard.extra.EDIT_CROP"
        private const val EXTRA_CODE_TEXT = "dev.cantacard.extra.EDIT_CODE_TEXT"
        private const val EXTRA_CODE_FORMAT = "dev.cantacard.extra.EDIT_CODE_FORMAT"

        /** Nuova tessera, con il negozio scelto dal catalogo (`null` = "Altro negozio"). */
        fun add(context: Context, storeId: String? = null, name: String? = null): Intent =
            Intent(context, EditCardActivity::class.java)
                .putExtra(EXTRA_STORE_ID, storeId)
                .putExtra(EXTRA_NAME, name)

        /** Nuova tessera da un'immagine condivisa: da ritagliare, con l'eventuale codice letto. */
        fun addFromImage(context: Context, cropPath: String, code: ScannedCode?): Intent =
            Intent(context, EditCardActivity::class.java)
                .putExtra(EXTRA_CROP, cropPath)
                .putExtra(EXTRA_CODE_TEXT, code?.text)
                .putExtra(EXTRA_CODE_FORMAT, code?.format?.name)

        fun edit(context: Context, cardId: String): Intent =
            Intent(context, EditCardActivity::class.java).putExtra(EXTRA_CARD_ID, cardId)
    }
}
