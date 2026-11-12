package dev.cantacard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.cantacard.ui.CantaCardTheme
import dev.cantacard.ui.store.StoreChoice
import dev.cantacard.ui.store.StorePickerScreen

/**
 * Scelta del negozio dal catalogo.
 * - Aggiunta ([add]): dopo la scelta apre il modulo; se la tessera viene salvata si chiude,
 *   se si torna indietro dal modulo si ritorna qui.
 * - Cambio negozio ([Pick]): restituisce la scelta a chi l'ha chiesta.
 */
class StorePickerActivity : ComponentActivity() {

    private val editLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Intestazione a gradiente scuro: icone di sistema chiare.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        val pickMode = intent.getBooleanExtra(EXTRA_PICK, false)
        val selectedId = intent.getStringExtra(EXTRA_SELECTED_ID)
        val repository = app.catalog
        repository.checkInBackground()

        setContent {
            val catalog by repository.catalog.collectAsState()
            CantaCardTheme {
                StorePickerScreen(
                    catalog = catalog,
                    selectedId = selectedId,
                    showSelection = pickMode,
                    onPick = { choice ->
                        if (pickMode) {
                            setResult(Activity.RESULT_OK, choice.toIntent())
                            finish()
                        } else {
                            editLauncher.launch(EditCardActivity.add(this, choice.storeId, choice.name))
                        }
                    },
                    onBack = ::finish,
                    onUpdate = repository::updateNow,
                )
            }
        }
    }

    /** Cambio negozio: input = negozio attuale (`null` = "Altro negozio"). */
    class Pick : ActivityResultContract<String?, StoreChoice?>() {
        override fun createIntent(context: Context, input: String?): Intent =
            Intent(context, StorePickerActivity::class.java)
                .putExtra(EXTRA_PICK, true)
                .putExtra(EXTRA_SELECTED_ID, input)

        override fun parseResult(resultCode: Int, intent: Intent?): StoreChoice? =
            if (resultCode == Activity.RESULT_OK && intent != null) {
                StoreChoice(intent.getStringExtra(EXTRA_STORE_ID), intent.getStringExtra(EXTRA_NAME))
            } else {
                null
            }
    }

    companion object {
        private const val EXTRA_PICK = "dev.cantacard.extra.PICK"
        private const val EXTRA_SELECTED_ID = "dev.cantacard.extra.SELECTED_STORE_ID"
        private const val EXTRA_STORE_ID = "dev.cantacard.extra.STORE_ID"
        private const val EXTRA_NAME = "dev.cantacard.extra.STORE_NAME"

        fun add(context: Context): Intent = Intent(context, StorePickerActivity::class.java)

        private fun StoreChoice.toIntent() = Intent()
            .putExtra(EXTRA_STORE_ID, storeId)
            .putExtra(EXTRA_NAME, name)
    }
}
