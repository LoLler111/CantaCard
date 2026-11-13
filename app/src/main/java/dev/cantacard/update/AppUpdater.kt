package dev.cantacard.update

import dev.cantacard.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Controllo degli aggiornamenti su GitHub Releases, a ogni avvio, in background e senza errori
 * visibili. L'app non installa niente: la riga nella lista apre nel browser il link dell'APK della
 * versione più recente, letto qui a ogni controllo, e l'installazione la fa Android.
 * (Un'app con `REQUEST_INSTALL_PACKAGES` che scarica APK viene segnalata da Play Protect.)
 */
class AppUpdater(private val scope: CoroutineScope) {

    private val _available = MutableStateFlow<AppRelease?>(null)

    /** La versione più recente di quella installata, `null` se non ce n'è o non si sa. */
    val available: StateFlow<AppRelease?> = _available.asStateFlow()

    private val checked = AtomicBoolean(false)

    /** Una volta per avvio dell'app: chiamarlo quando la lista è già mostrata. */
    fun checkOnStart() {
        if (!checked.compareAndSet(false, true)) return
        scope.launch(Dispatchers.IO) {
            val json = fetch() ?: return@launch
            _available.value = UpdateJson.newestApk(json, BuildConfig.VERSION_NAME)
        }
    }

    /** Elenco delle release dall'API di GitHub, con timeout brevi; `null` se fallisce. */
    private fun fetch(): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(RELEASES_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5_000
                readTimeout = 10_000
                useCaches = false
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "CantaCard")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            connection.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
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
            // Mai crash per un controllo facoltativo.
            null
        } finally {
            connection?.disconnect()
        }
    }

    private companion object {
        const val RELEASES_URL = "https://api.github.com/repos/LoLler111/CantaCard/releases?per_page=10"
        const val MAX_BYTES = 1024 * 1024
    }
}
