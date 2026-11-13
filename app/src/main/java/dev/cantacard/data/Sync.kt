package dev.cantacard.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import dev.cantacard.image.ImageStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.io.File
import java.io.IOException

/**
 * Sincronizzazione tra telefoni tramite un file scelto dall'utente con lo Storage Access Framework
 * (es. su Google Drive), senza account né server: ogni telefono tiene tutte le sue tessere, il file
 * serve solo a scambiarle. Si sincronizza all'apertura dell'app e poco dopo ogni modifica, sempre in
 * background: mostrare una tessera non aspetta mai la rete. Regole di unione in [SyncMerge].
 */
class Sync(
    private val context: Context,
    private val repository: CardRepository,
    private val images: ImageStore,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    enum class Error { UNREACHABLE, INVALID_FILE }

    data class State(
        val enabled: Boolean,
        val fileName: String?,
        val lastSync: Long,
        val running: Boolean = false,
        val error: Error? = null,
    )

    enum class EnableResult { OK, SYNC_FAILED, INVALID_FILE, NO_PERMISSION }

    private val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(
        State(
            enabled = prefs.getString(KEY_URI, null) != null,
            fileName = prefs.getString(KEY_NAME, null),
            lastSync = prefs.getLong(KEY_LAST, 0L),
        ),
    )
    val state: StateFlow<State> = _state.asStateFlow()

    private val mutex = Mutex()
    private val workDir = File(context.cacheDir, "sync")
    private var scheduled: Job? = null
    private var lastAttempt = 0L

    /** Contenuto dell'ultima sincronizzazione (senza statistiche d'uso), per riconoscere le modifiche. */
    @Volatile private var syncedKey: List<Card>? = null

    init {
        scope.launch {
            repository.cards.filterNotNull().collect { cards ->
                if (_state.value.enabled && (syncedKey == null || triggerKey(cards) != syncedKey)) schedule(CHANGE_DELAY_MS)
            }
        }
    }

    /** All'apertura dell'app: rilegge il file (non più di una volta ogni 30 secondi). */
    fun onAppOpen() {
        if (_state.value.enabled && clock() - lastAttempt > REOPEN_INTERVAL_MS) schedule(OPEN_DELAY_MS)
    }

    /**
     * Attiva la sincronizzazione con [uri]: un file nuovo (vuoto) o uno già usato da un altro
     * telefono. Un file che non è di CantaCard non viene mai sovrascritto.
     */
    suspend fun enable(uri: Uri): EnableResult = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.takePersistableUriPermission(uri, RW)
        } catch (e: SecurityException) {
            return@withContext EnableResult.NO_PERMISSION
        }
        prefs.edit()
            .putString(KEY_URI, uri.toString())
            .putString(KEY_NAME, displayName(uri))
            .putLong(KEY_LAST, 0L)
            .apply()
        _state.value = State(enabled = true, fileName = prefs.getString(KEY_NAME, null), lastSync = 0L)
        when (syncNow()) {
            null -> EnableResult.OK
            Error.INVALID_FILE -> {
                disable()
                EnableResult.INVALID_FILE
            }
            Error.UNREACHABLE -> EnableResult.SYNC_FAILED
        }
    }

    /** Disattiva: le tessere restano sul telefono, il file resta dov'è. */
    fun disable() {
        scheduled?.cancel()
        prefs.getString(KEY_URI, null)?.let { uri ->
            try {
                context.contentResolver.releasePersistableUriPermission(Uri.parse(uri), RW)
            } catch (e: SecurityException) {
                // Permesso già perso.
            }
        }
        prefs.edit().clear().apply()
        syncedKey = null
        _state.value = State(enabled = false, fileName = null, lastSync = 0L)
    }

    /** Sincronizza subito; `null` se è andata bene, altrimenti il motivo. */
    suspend fun syncNow(): Error? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val uri = prefs.getString(KEY_URI, null)?.let(Uri::parse) ?: return@withLock null
            lastAttempt = clock()
            _state.update { it.copy(running = true) }
            val error = try {
                run(uri)
                null
            } catch (e: InvalidSyncFile) {
                Error.INVALID_FILE
            } catch (e: IOException) {
                Log.w(TAG, "File di sincronizzazione non raggiungibile", e)
                Error.UNREACHABLE
            } catch (e: SecurityException) {
                Log.w(TAG, "Permesso sul file di sincronizzazione perso", e)
                Error.UNREACHABLE
            } finally {
                workDir.deleteRecursively()
            }
            if (error == null) prefs.edit().putLong(KEY_LAST, clock()).apply()
            _state.update { it.copy(running = false, error = error, lastSync = prefs.getLong(KEY_LAST, 0L)) }
            error
        }
    }

    private fun schedule(delayMs: Long) {
        scheduled?.cancel()
        scheduled = scope.launch {
            delay(delayMs)
            syncNow()
        }
    }

    private suspend fun run(uri: Uri) {
        val resolver = context.contentResolver
        // 1. Il file: copiato in locale per distinguere "vuoto" (appena creato) da "non valido".
        workDir.mkdirs()
        val copy = File(workDir, "remote.zip")
        val input = resolver.openInputStream(uri) ?: throw IOException("File non apribile")
        input.use { source -> copy.outputStream().use { source.copyTo(it) } }
        val zip: BackupZip.Contents?
        val remote: CardSnapshot
        if (copy.length() == 0L) {
            zip = null
            remote = CardSnapshot(emptyList())
        } else {
            zip = copy.inputStream().use { BackupZip.read(it, File(workDir, "images")) } ?: throw InvalidSyncFile()
            val content = try {
                BackupFormat.decode(zip.json)
            } catch (e: JSONException) {
                throw InvalidSyncFile()
            }
            remote = CardSnapshot(content.cards, content.deleted)
        }

        // 2. Unione e salvataggio sul telefono, con le immagini delle tessere arrivate dal file.
        val (merged, replaced) = repository.replace { current ->
            val result = SyncMerge.merge(current, remote, clock())
            val incoming = result.snapshot.cards.filter { it.id in result.fromRemote }
            val committed = Backup.commitImages(incoming, zip?.images.orEmpty(), images)
            val cards = result.snapshot.cards.map { if (it.id in result.fromRemote) it.copy(image = committed[it.id]) else it }
            val next = CardSnapshot(cards, result.snapshot.deleted)
            // Prima del salvataggio: la modifica che arriva dal file non fa ripartire un'altra sincronizzazione.
            syncedKey = triggerKey(cards)
            next to (next to (current.cards.mapNotNull { it.image } + committed.values))
        }
        Backup.deleteUnusedImages(replaced, merged.cards, images)

        // 3. Il file si riscrive solo se è cambiato qualcosa (meno caricamenti su Drive).
        if (zip == null || !SyncMerge.sameContent(merged, remote)) {
            val imageFiles = Backup.imageEntries(merged.cards, images)
            val json = BackupFormat.encode(merged.cards, clock(), merged.deleted) {
                BackupFormat.imageEntryFor(it.id) in imageFiles
            }
            Backup.openOutput(context, uri).use { BackupZip.write(it, json, imageFiles) }
        }
        syncedKey = triggerKey(merged.cards)
    }

    private fun displayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    } catch (e: RuntimeException) {
        null
    }

    private class InvalidSyncFile : Exception()

    private companion object {
        const val TAG = "Sync"
        const val KEY_URI = "uri"
        const val KEY_NAME = "name"
        const val KEY_LAST = "lastSync"
        const val RW = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        const val CHANGE_DELAY_MS = 2_000L
        const val OPEN_DELAY_MS = 1_500L
        const val REOPEN_INTERVAL_MS = 30_000L

        /** Le aperture in cassa (statistiche d'uso) non fanno partire una sincronizzazione. */
        fun triggerKey(cards: List<Card>): List<Card> =
            cards.map { it.copy(lastUsed = 0L, useCount = 0) }.sortedBy { it.id }
    }
}
