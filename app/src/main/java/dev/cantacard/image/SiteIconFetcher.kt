package dev.cantacard.image

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL

/**
 * Scarica l'icona dal sito ufficiale del negozio (mai da servizi di terzi come Google s2 o
 * DuckDuckGo). Solo HTTPS, timeout brevi, limiti di dimensione; ogni errore dà `null`.
 */
object SiteIconFetcher {

    private const val MAX_PAGE_BYTES = 512 * 1024
    private const val MAX_MANIFEST_BYTES = 128 * 1024
    private const val MAX_IMAGE_BYTES = 1024 * 1024
    private const val MAX_ATTEMPTS = 6
    private const val MIN_SIDE = 32

    // Alcuni siti rifiutano i client sconosciuti: intestazione da browser generico, nessun dato personale.
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Mobile"

    /** Icona di [domain] (es. "esselunga.it") già ridotta a [ImageStore.ICON_MAX_SIDE]. */
    suspend fun fetch(domain: String): Bitmap? = withContext(Dispatchers.IO) {
        val start = try {
            URL("https://$domain/")
        } catch (e: MalformedURLException) {
            return@withContext null
        }
        val page = get(start, MAX_PAGE_BYTES, "text/html")
        val base = page?.finalUrl ?: start
        val parsed = page?.let { SiteIconParser.parsePage(it.text(), base) }
            ?: SiteIconParser.Page(null, emptyList(), emptyList())
        val manifest = parsed.manifestUrl?.let { url ->
            get(URL(url), MAX_MANIFEST_BYTES, "application/manifest+json,application/json")
                ?.let { SiteIconParser.parseManifest(it.text(), it.finalUrl) }
        }.orEmpty()
        for (candidate in SiteIconParser.ordered(parsed, manifest, base).take(MAX_ATTEMPTS)) {
            val bytes = get(URL(candidate.url), MAX_IMAGE_BYTES, "image/*")?.bytes ?: continue
            val bitmap = Bitmaps.decodeBytes(bytes) ?: continue
            if (minOf(bitmap.width, bitmap.height) >= MIN_SIDE) {
                return@withContext Bitmaps.fit(bitmap, ImageStore.ICON_MAX_SIDE)
            }
        }
        null
    }

    private class Response(val bytes: ByteArray, val finalUrl: URL) {
        fun text(): String = bytes.decodeToString()
    }

    private fun get(url: URL, maxBytes: Int, accept: String): Response? {
        if (!url.protocol.equals("https", ignoreCase = true)) return null
        var connection: HttpURLConnection? = null
        return try {
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5_000
                readTimeout = 8_000
                // Segue i redirect solo con lo stesso protocollo: https → http viene fermato.
                instanceFollowRedirects = true
                useCaches = false
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", accept)
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            if (!connection.url.protocol.equals("https", ignoreCase = true)) return null
            if (connection.contentLengthLong > maxBytes) return null
            connection.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    if (out.size() > maxBytes) return null
                }
                Response(out.toByteArray(), connection.url)
            }
        } catch (e: IOException) {
            null
        } catch (e: RuntimeException) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
