package dev.cantacard

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.cantacard.data.Backup
import dev.cantacard.data.RestoreMode
import dev.cantacard.ui.CantaCardTheme
import dev.cantacard.ui.list.CardListScreen
import dev.cantacard.ui.list.RestoreDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate

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
                val exportLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument(ZIP_MIME),
                ) { uri -> uri?.let(::export) }
                val restoreLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument(),
                ) { uri -> uri?.let(::readBackup) }

                CardListScreen(
                    repository = repository,
                    onOpen = { card -> startActivity(ShowCardActivity.intent(this, card.id)) },
                    onAdd = { startActivity(StorePickerActivity.add(this)) },
                    onExport = {
                        exportLauncher.launch(getString(R.string.backup_file_name, LocalDate.now().toString()))
                    },
                    onRestore = { restoreLauncher.launch(BACKUP_MIMES) },
                )

                val pending by app.backup.pending.collectAsState()
                val cards by repository.cards.collectAsState()
                pending?.let { backup ->
                    RestoreDialog(
                        backupCount = backup.content.cards.size,
                        currentCount = cards?.size ?: 0,
                        isNewer = backup.content.isNewer,
                        onChoose = ::restore,
                        onDismiss = app.backup::cancel,
                    )
                }
            }
        }
    }

    // Le operazioni vanno nello scope dell'app: finiscono anche se lo schermo ruota.
    private fun export(uri: Uri) {
        val app = app
        app.appScope.launch(Dispatchers.Main) {
            val message = try {
                val count = app.backup.export(uri)
                app.resources.getQuantityString(R.plurals.backup_exported, count, count)
            } catch (e: IOException) {
                app.getString(R.string.backup_export_failed)
            } catch (e: SecurityException) {
                app.getString(R.string.backup_export_failed)
            }
            Toast.makeText(app, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun readBackup(uri: Uri) {
        val app = app
        app.appScope.launch(Dispatchers.Main) {
            if (!app.backup.read(uri)) Toast.makeText(app, R.string.backup_invalid, Toast.LENGTH_LONG).show()
        }
    }

    private fun restore(mode: RestoreMode) {
        val app = app
        app.appScope.launch(Dispatchers.Main) {
            val summary = app.backup.restore(mode) ?: return@launch
            Toast.makeText(app, summaryText(summary), Toast.LENGTH_LONG).show()
        }
    }

    private fun summaryText(summary: Backup.Summary): String {
        val res = app.resources
        val parts = mutableListOf(res.getQuantityString(R.plurals.restore_done, summary.added, summary.added))
        if (summary.duplicates > 0) {
            parts += res.getQuantityString(R.plurals.restore_duplicates, summary.duplicates, summary.duplicates)
        }
        if (summary.skipped > 0) {
            parts += res.getQuantityString(R.plurals.restore_skipped, summary.skipped, summary.skipped)
        }
        return parts.joinToString(" · ")
    }

    private companion object {
        const val ZIP_MIME = "application/zip"

        // Alcune app di archiviazione non riconoscono lo zip e lo indicano come file generico.
        val BACKUP_MIMES = arrayOf(ZIP_MIME, "application/x-zip-compressed", "application/octet-stream")
    }
}
