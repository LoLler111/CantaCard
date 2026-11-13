package dev.cantacard.update

import android.content.Context
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import android.widget.Toast
import dev.cantacard.BuildConfig
import dev.cantacard.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Aggiornamento dell'app da GitHub Releases: un controllo a ogni avvio (in background, senza
 * errori visibili) e, solo se l'utente tocca la riga nella lista, download dell'APK nella cache
 * privata e installazione con il [PackageInstaller] di Android, che chiede sempre conferma e
 * accetta solo un APK firmato con la stessa chiave.
 */
class AppUpdater(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    sealed interface State {
        data object None : State
        data class Available(val release: AppRelease) : State

        /** [progress] tra 0 e 1, `null` finché la dimensione non è nota. */
        data class Downloading(val release: AppRelease, val progress: Float?) : State
        data class Installing(val release: AppRelease) : State
    }

    private val _state = MutableStateFlow<State>(State.None)
    val state: StateFlow<State> = _state.asStateFlow()

    private val checked = AtomicBoolean(false)
    private val updatesDir get() = File(context.cacheDir, "updates")

    /** Una volta per avvio dell'app: chiamarlo quando la lista è già mostrata. */
    fun checkOnStart() {
        if (!checked.compareAndSet(false, true)) return
        scope.launch(Dispatchers.IO) {
            // APK rimasti da un aggiornamento precedente (installato o interrotto).
            updatesDir.deleteRecursively()
            val json = fetch() ?: return@launch
            val release = UpdateJson.newestApk(json, BuildConfig.VERSION_NAME) ?: return@launch
            _state.compareAndSet(State.None, State.Available(release))
        }
    }

    /** Scarica e installa la versione disponibile. */
    fun install() {
        val release = (_state.value as? State.Available)?.release ?: return
        if (!_state.compareAndSet(State.Available(release), State.Downloading(release, null))) return
        scope.launch(Dispatchers.IO) {
            val file = File(updatesDir, "update.apk")
            val error = when {
                !download(release, file) -> R.string.update_download_failed
                !isValidUpdate(file) -> R.string.update_invalid
                else -> {
                    _state.value = State.Installing(release)
                    if (commit(file)) null else R.string.update_install_failed
                }
            }
            file.delete()
            if (error != null) failed(error)
        }
    }

    /** Esito dell'installazione, da [UpdateStatusReceiver]. */
    internal fun onInstallStatus(status: Int) {
        val release = when (val current = _state.value) {
            is State.Installing -> current.release
            is State.Available -> current.release
            else -> return
        }
        when (status) {
            // Si apre la conferma di Android: se l'utente annulla, la riga torna come prima.
            PackageInstaller.STATUS_PENDING_USER_ACTION,
            PackageInstaller.STATUS_FAILURE_ABORTED -> _state.value = State.Available(release)
            // Con successo il processo viene chiuso da Android: non resta niente da fare.
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> scope.launch { failed(R.string.update_install_failed) }
        }
    }

    private suspend fun failed(message: Int) {
        when (val current = _state.value) {
            is State.Downloading -> _state.value = State.Available(current.release)
            is State.Installing -> _state.value = State.Available(current.release)
            else -> Unit
        }
        withContext(Dispatchers.Main) { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
    }

    /** Elenco delle release dall'API di GitHub, con timeout brevi; `null` se fallisce. */
    private fun fetch(): String? = connect(RELEASES_URL, "application/vnd.github+json") { connection ->
        connection.inputStream.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                if (out.size() > MAX_JSON_BYTES) return@connect null
            }
            out.toByteArray().decodeToString()
        }
    }

    private fun download(release: AppRelease, file: File): Boolean = connect(release.apkUrl, "application/octet-stream") { connection ->
        val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
        if (total > MAX_APK_BYTES) return@connect false
        file.parentFile?.mkdirs()
        var written = 0L
        var lastPercent = -1
        connection.inputStream.use { input ->
            file.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    written += n
                    if (written > MAX_APK_BYTES) return@connect false
                    if (total > 0) {
                        val percent = (written * 100 / total).toInt().coerceAtMost(100)
                        if (percent != lastPercent) {
                            lastPercent = percent
                            _state.value = State.Downloading(release, percent / 100f)
                        }
                    }
                }
            }
        }
        // Download troncato: la dimensione deve corrispondere a quella dichiarata.
        total <= 0 || written == total
    } ?: false

    /** Stesso pacchetto e versione più recente di quella installata (la firma la controlla Android). */
    private fun isValidUpdate(file: File): Boolean {
        val info = context.packageManager.getPackageArchiveInfo(file.path, 0) ?: return false
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        return info.packageName == context.packageName && versionCode > BuildConfig.VERSION_CODE
    }

    private fun commit(file: File): Boolean {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())
        }
        var sessionId = -1
        return try {
            sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                file.inputStream().use { input ->
                    session.openWrite("CantaCard.apk", 0, file.length()).use { out ->
                        input.copyTo(out)
                        session.fsync(out)
                    }
                }
                session.commit(UpdateStatusReceiver.pendingIntent(context).intentSender)
            }
            true
        } catch (e: IOException) {
            Log.w(TAG, "Installazione non avviata", e)
            if (sessionId >= 0) runCatching { installer.abandonSession(sessionId) }
            false
        } catch (e: RuntimeException) {
            // Es. SecurityException: mai crash per un aggiornamento.
            Log.w(TAG, "Installazione non avviata", e)
            if (sessionId >= 0) runCatching { installer.abandonSession(sessionId) }
            false
        }
    }

    /** Connessione HTTPS con timeout brevi; `null` se fallisce o la risposta non è 200. */
    private inline fun <T> connect(url: String, accept: String, block: (HttpURLConnection) -> T?): T? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5_000
                readTimeout = 15_000
                instanceFollowRedirects = true
                useCaches = false
                setRequestProperty("Accept", accept)
                setRequestProperty("User-Agent", "CantaCard")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            block(connection)
        } catch (e: IOException) {
            null
        } catch (e: RuntimeException) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private companion object {
        const val TAG = "AppUpdater"
        const val RELEASES_URL = "https://api.github.com/repos/LoLler111/CantaCard/releases?per_page=10"
        const val MAX_JSON_BYTES = 1024 * 1024
        const val MAX_APK_BYTES = 64L * 1024 * 1024
    }
}
