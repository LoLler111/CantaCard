package dev.cantacard

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.cantacard.data.CardQuery
import dev.cantacard.data.SortOrder
import dev.cantacard.ui.CantaCardTheme
import dev.cantacard.ui.list.ReorderList
import dev.cantacard.ui.list.SortDialog
import dev.cantacard.ui.settings.SettingsHeader
import dev.cantacard.ui.settings.SettingsScreen
import dev.cantacard.ui.settings.SettingsState

/**
 * Impostazioni (dal menu ⋮ della lista): ordine delle preferite e delle altre tessere con il
 * riordino a mano, backup, sincronizzazione, importazione e informazioni sull'app.
 */
class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        val app = app
        val prefs = app.listPrefs
        val repository = app.cards

        setContent {
            CantaCardTheme {
                val backup = rememberBackupActions()
                val cards by repository.cards.collectAsState()
                val sortOrder by prefs.sortOrder.collectAsState()
                val favoritesOrder by prefs.favoritesOrder.collectAsState()
                val syncState by app.sync.state.collectAsState()
                var reordering by rememberSaveable { mutableStateOf(false) }
                var sortDialog by rememberSaveable { mutableStateOf(false) }
                var syncDialog by rememberSaveable { mutableStateOf(false) }
                val active = remember(cards) { cards?.filterNot { it.archived }.orEmpty() }
                val favoritesManual = favoritesOrder == SortOrder.MANUAL
                val othersManual = sortOrder == SortOrder.MANUAL

                if (reordering) {
                    BackHandler { reordering = false }
                    val sorted = remember(active) { CardQuery.sorted(active, SortOrder.MANUAL) }
                    val title = if (othersManual) R.string.reorder_menu else R.string.reorder_favorites_menu
                    Column(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                        SettingsHeader(stringResource(title)) { reordering = false }
                        ReorderList(
                            favorites = if (favoritesManual) sorted.filter { it.favorite } else emptyList(),
                            others = if (othersManual) sorted.filterNot { it.favorite } else emptyList(),
                            onMove = { group, from, to -> repository.setRanks(CardQuery.reorder(group, from, to)) },
                        )
                    }
                } else {
                    SettingsScreen(
                        state = SettingsState(
                            favoritesOrder = favoritesOrder,
                            sortOrder = sortOrder,
                            favoriteCount = active.count { it.favorite },
                            hasCards = active.isNotEmpty(),
                            sync = syncState,
                            versionName = BuildConfig.VERSION_NAME,
                        ),
                        onBack = ::finish,
                        onSort = { sortDialog = true },
                        onReorder = { reordering = true },
                        onSync = { syncDialog = true },
                        onExport = backup::export,
                        onRestore = backup::restore,
                        onImport = backup::importFromApp,
                        onReport = ::reportProblem,
                        onSource = { openLink(Uri.parse(REPO_URL)) },
                    )
                }

                if (sortDialog) {
                    SortDialog(
                        favoritesOrder = favoritesOrder,
                        othersOrder = sortOrder,
                        onFavoritesOrder = { app.changeOrder(favorites = true, it) },
                        onOthersOrder = { app.changeOrder(favorites = false, it) },
                        onDismiss = { sortDialog = false },
                    )
                }

                if (syncDialog) SyncSettingsDialog(backup) { syncDialog = false }

                RestorePrompt()
            }
        }
    }

    /**
     * "Segnala un problema": apre nel browser una nuova segnalazione su GitHub con versione dell'app,
     * di Android e modello del telefono già scritti (niente dati personali, li vede l'utente prima
     * di inviare). Nessun invio automatico.
     */
    private fun reportProblem() {
        val body = getString(
            R.string.report_body,
            BuildConfig.VERSION_NAME,
            Build.VERSION.RELEASE,
            Build.VERSION.SDK_INT,
            "${Build.MANUFACTURER} ${Build.MODEL}",
        )
        openLink(Uri.parse(ISSUES_URL).buildUpon().appendQueryParameter("body", body).build())
    }

    private fun openLink(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.update_no_browser, Toast.LENGTH_LONG).show()
        }
    }

    private companion object {
        const val REPO_URL = "https://github.com/LoLler111/CantaCard"
        const val ISSUES_URL = "$REPO_URL/issues/new"
    }
}
