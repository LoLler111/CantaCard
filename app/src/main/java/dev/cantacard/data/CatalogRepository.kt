package dev.cantacard.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Catalogo negozi: la copia inclusa nell'APK (asset `stores.json`) oppure quella scaricata da
 * GitHub, se ha una `version` più alta. Senza rete si usa sempre la copia locale.
 *
 * Il controllo automatico ([checkInBackground]) parte al massimo una volta a settimana e non
 * segnala mai errori; quello manuale ([updateNow]) restituisce l'esito.
 */
class CatalogRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    enum class UpdateResult { UPDATED, UP_TO_DATE, FAILED }

    private val _catalog = MutableStateFlow<Catalog?>(null)

    /** `null` finché non è stato letto. */
    val catalog: StateFlow<Catalog?> = _catalog.asStateFlow()

    private val downloaded = AtomicFile(File(context.filesDir, "catalog/stores.json"))
    private val prefs = context.getSharedPreferences("catalog", Context.MODE_PRIVATE)
    private val updateLock = Mutex()

    init {
        scope.launch(Dispatchers.IO) {
            val bundled = readBundled()
            val local = readDownloaded()
            _catalog.compareAndSet(null, CatalogJson.newest(bundled, local))
        }
    }

    /** Controllo silenzioso, al massimo una volta ogni [CHECK_INTERVAL_MS]. */
    fun checkInBackground() {
        val last = prefs.getLong(KEY_LAST_CHECK, 0L)
        val now = System.currentTimeMillis()
        if (now - last in 0 until CHECK_INTERVAL_MS) return
        scope.launch { update() }
    }

    /** Controllo richiesto dall'utente. */
    suspend fun updateNow(): UpdateResult = update()

    private suspend fun update(): UpdateResult = updateLock.withLock {
        withContext(Dispatchers.IO) {
            val text = download() ?: return@withContext UpdateResult.FAILED
            prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
            val remote = CatalogJson.decodeOrNull(text) ?: return@withContext UpdateResult.FAILED
            val current = _catalog.value ?: CatalogJson.newest(readBundled(), readDownloaded())
            if (remote.version <= current.version) return@withContext UpdateResult.UP_TO_DATE
            try {
                write(text)
            } catch (e: IOException) {
                Log.w(TAG, "Impossibile salvare il catalogo scaricato", e)
            }
            _catalog.value = remote
            UpdateResult.UPDATED
        }
    }

    private fun readBundled(): Catalog? = try {
        context.assets.open(ASSET).use { CatalogJson.decodeOrNull(it.readBytes().decodeToString()) }
    } catch (e: IOException) {
        Log.e(TAG, "Catalogo incluso non leggibile", e)
        null
    }

    private fun readDownloaded(): Catalog? = try {
        CatalogJson.decodeOrNull(downloaded.readFully().decodeToString())
    } catch (e: FileNotFoundException) {
        null
    } catch (e: IOException) {
        null
    }

    private fun write(text: String) {
        downloaded.baseFile.parentFile?.mkdirs()
        val out = downloaded.startWrite()
        try {
            out.write(text.toByteArray())
            downloaded.finishWrite(out)
        } catch (e: IOException) {
            downloaded.failWrite(out)
            throw e
        }
    }

    /** Scarica il file raw con timeout brevi e un limite di dimensione; `null` se fallisce. */
    private fun download(): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(UPDATE_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5_000
                readTimeout = 10_000
                instanceFollowRedirects = true
                useCaches = false
                setRequestProperty("Accept", "application/json")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            connection.inputStream.use { input ->
                val buffer = ByteArray(8 * 1024)
                val out = java.io.ByteArrayOutputStream()
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    if (out.size() > MAX_BYTES) return null
                }
                out.toByteArray().decodeToString()
            }
        } catch (e: IOException) {
            null
        } catch (e: RuntimeException) {
            // Es. SecurityException o URL malformato: mai crash per un aggiornamento facoltativo.
            null
        } finally {
            connection?.disconnect()
        }
    }

    private companion object {
        const val TAG = "CatalogRepository"
        const val ASSET = "stores.json"
        const val UPDATE_URL =
            "https://raw.githubusercontent.com/LoLler111/CantaCard/main/catalog/stores.json"
        const val KEY_LAST_CHECK = "lastCheck"
        const val CHECK_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000
        const val MAX_BYTES = 512 * 1024
    }
}
