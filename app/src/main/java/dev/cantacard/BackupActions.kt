package dev.cantacard

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import dev.cantacard.data.Backup
import dev.cantacard.data.RestoreMode
import dev.cantacard.data.Sync
import dev.cantacard.ui.list.RestoreDialog
import dev.cantacard.ui.list.SyncDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate

/**
 * Backup, ripristino, importazione da Catima e sincronizzazione: i comandi usati dalle impostazioni
 * e dalla lista vuota ("Hai un backup? Ripristinalo"). I file si scelgono con il SAF; le operazioni
 * girano nello scope dell'app, così finiscono anche se lo schermo ruota o si chiude la schermata.
 */
class BackupActions internal constructor(
    private val app: CantaCardApp,
    private val exportLauncher: ManagedActivityResultLauncher<String, Uri?>,
    private val restoreLauncher: ManagedActivityResultLauncher<Array<String>, Uri?>,
    private val importLauncher: ManagedActivityResultLauncher<Array<String>, Uri?>,
    private val syncCreateLauncher: ManagedActivityResultLauncher<String, Uri?>,
    private val syncOpenLauncher: ManagedActivityResultLauncher<Array<String>, Uri?>,
) {
    fun export() = exportLauncher.launch(app.getString(R.string.backup_file_name, LocalDate.now().toString()))

    fun restore() = restoreLauncher.launch(BACKUP_MIMES)

    fun importFromApp() = importLauncher.launch(IMPORT_MIMES)

    fun createSyncFile() = syncCreateLauncher.launch(app.getString(R.string.sync_file_name))

    fun useExistingSyncFile() = syncOpenLauncher.launch(BACKUP_MIMES)

    fun syncNow() {
        app.appScope.launch(Dispatchers.Main) {
            val message = when (app.sync.syncNow()) {
                null -> R.string.sync_done
                Sync.Error.INVALID_FILE -> R.string.sync_invalid
                Sync.Error.UNREACHABLE -> R.string.sync_failed
            }
            Toast.makeText(app, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun disableSync() {
        app.sync.disable()
        Toast.makeText(app, R.string.sync_disabled, Toast.LENGTH_LONG).show()
    }

    companion object {
        const val ZIP_MIME = "application/zip"

        // Alcune app di archiviazione non riconoscono lo zip e lo indicano come file generico.
        val BACKUP_MIMES = arrayOf(ZIP_MIME, "application/x-zip-compressed", "application/octet-stream")

        /** Esportazione di Catima: zip, oppure il solo CSV. */
        val IMPORT_MIMES = BACKUP_MIMES + arrayOf("text/csv", "text/comma-separated-values", "text/plain")
    }

    internal object Ops {

        fun export(app: CantaCardApp, uri: Uri) {
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

        fun readBackup(app: CantaCardApp, uri: Uri) {
            app.appScope.launch(Dispatchers.Main) {
                if (!app.backup.read(uri)) Toast.makeText(app, R.string.backup_invalid, Toast.LENGTH_LONG).show()
            }
        }

        fun readImport(app: CantaCardApp, uri: Uri) {
            app.appScope.launch(Dispatchers.Main) {
                val message = when (app.backup.readImport(uri)) {
                    Backup.ImportResult.OK -> return@launch
                    Backup.ImportResult.ENCRYPTED -> R.string.import_encrypted
                    Backup.ImportResult.NOT_SUPPORTED -> R.string.import_not_supported
                    Backup.ImportResult.UNREADABLE -> R.string.import_unreadable
                }
                Toast.makeText(app, message, Toast.LENGTH_LONG).show()
            }
        }

        fun enableSync(app: CantaCardApp, uri: Uri) {
            app.appScope.launch(Dispatchers.Main) {
                val message = when (app.sync.enable(uri)) {
                    Sync.EnableResult.OK -> R.string.sync_enabled
                    Sync.EnableResult.SYNC_FAILED -> R.string.sync_failed
                    Sync.EnableResult.INVALID_FILE -> R.string.sync_invalid
                    Sync.EnableResult.NO_PERMISSION -> R.string.sync_no_permission
                }
                Toast.makeText(app, message, Toast.LENGTH_LONG).show()
            }
        }

        fun restore(app: CantaCardApp, mode: RestoreMode, imported: Boolean) {
            app.appScope.launch(Dispatchers.Main) {
                val summary = app.backup.restore(mode) ?: return@launch
                Toast.makeText(app, summaryText(app, summary, imported), Toast.LENGTH_LONG).show()
            }
        }

        private fun summaryText(app: CantaCardApp, summary: Backup.Summary, imported: Boolean): String {
            val res = app.resources
            val done = if (imported) R.plurals.import_done else R.plurals.restore_done
            val parts = mutableListOf(
                if (summary.added == 0) res.getString(R.string.restore_none_new)
                else res.getQuantityString(done, summary.added, summary.added),
            )
            if (summary.updated > 0) {
                parts += res.getQuantityString(R.plurals.restore_updated, summary.updated, summary.updated)
            }
            if (summary.duplicates > 0) {
                parts += res.getQuantityString(R.plurals.restore_duplicates, summary.duplicates, summary.duplicates)
            }
            if (summary.skipped > 0) {
                parts += res.getQuantityString(R.plurals.restore_skipped, summary.skipped, summary.skipped)
            }
            return parts.joinToString(" · ")
        }

        const val CATIMA = "Catima"
    }
}

/** Registra i selettori di file della schermata e restituisce i comandi. */
@Composable
fun rememberBackupActions(): BackupActions {
    val app = LocalContext.current.app
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupActions.ZIP_MIME)) { uri ->
        uri?.let { BackupActions.Ops.export(app, it) }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { BackupActions.Ops.readBackup(app, it) }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { BackupActions.Ops.readImport(app, it) }
    }
    val syncCreate = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupActions.ZIP_MIME)) { uri ->
        uri?.let { BackupActions.Ops.enableSync(app, it) }
    }
    val syncOpen = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { BackupActions.Ops.enableSync(app, it) }
    }
    return BackupActions(app, export, restore, import, syncCreate, syncOpen)
}

/**
 * Dopo aver letto un backup (o un'esportazione di Catima) chiede "Aggiungi alle attuali" o
 * "Sostituisci tutte". Va messo in ogni schermata che può aprire un backup.
 */
@Composable
fun RestorePrompt() {
    val app = LocalContext.current.app
    val pending by app.backup.pending.collectAsState()
    val cards by app.cards.cards.collectAsState()
    pending?.let { backup ->
        val imported = backup.source != Backup.Source.BACKUP
        RestoreDialog(
            backupCount = backup.content.cards.size,
            currentCount = cards?.size ?: 0,
            isNewer = backup.content.isNewer,
            onChoose = { mode -> BackupActions.Ops.restore(app, mode, imported) },
            onDismiss = app.backup::cancel,
            fromApp = if (backup.source == Backup.Source.CATIMA) BackupActions.Ops.CATIMA else null,
        )
    }
}

/** Finestra della sincronizzazione (dal sottotitolo della lista e dalle impostazioni). */
@Composable
fun SyncSettingsDialog(actions: BackupActions, onDismiss: () -> Unit) {
    val state by LocalContext.current.app.sync.state.collectAsState()
    SyncDialog(
        state = state,
        onCreateFile = {
            onDismiss()
            actions.createSyncFile()
        },
        onUseExisting = {
            onDismiss()
            actions.useExistingSyncFile()
        },
        onSyncNow = actions::syncNow,
        onDisable = {
            onDismiss()
            actions.disableSync()
        },
        onDismiss = onDismiss,
    )
}
