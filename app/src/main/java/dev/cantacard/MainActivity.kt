package dev.cantacard

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.cantacard.ui.CantaCardTheme
import dev.cantacard.ui.list.CardListScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
                val backup = rememberBackupActions()
                var showSync by rememberSaveable { mutableStateOf(false) }
                val update by app.updater.available.collectAsState()
                val sortOrder by app.listPrefs.sortOrder.collectAsState()
                val favoritesOrder by app.listPrefs.favoritesOrder.collectAsState()

                CardListScreen(
                    repository = repository,
                    sortOrder = sortOrder,
                    favoritesOrder = favoritesOrder,
                    onSortChange = { app.changeOrder(favorites = false, it) },
                    onFavoritesOrderChange = { app.changeOrder(favorites = true, it) },
                    onImport = backup::importFromApp,
                    onOpen = { card -> startActivity(ShowCardActivity.intent(this, card.id)) },
                    onAdd = { startActivity(StorePickerActivity.add(this)) },
                    onRestore = backup::restore,
                    onSync = { showSync = true },
                    onSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    update = update,
                    onUpdate = { release -> openUpdate(release.apkUrl) },
                )

                if (showSync) SyncSettingsDialog(backup) { showSync = false }
                RestorePrompt()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Fuori dal thread principale: legge le preferenze della sincronizzazione e, se attiva,
        // rilegge il file poco dopo l'apertura (la lista si mostra comunque subito).
        val app = app
        app.appScope.launch(Dispatchers.IO) { app.sync.onAppOpen() }
        // Una volta per avvio, in background: se c'è una versione nuova compare una riga nella lista.
        app.updater.checkOnStart()
    }

    /** Il download e l'installazione li fanno il browser e Android: l'app non installa niente. */
    private fun openUpdate(apkUrl: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.update_no_browser, Toast.LENGTH_LONG).show()
        }
    }
}
