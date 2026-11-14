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

    private const val MAX_PAGE_BYTES = 256 * 1024
    private const val MAX_MANIFEST_BYTES = 128 * 1024
    private const val MAX_IMAGE_BYTES = 1024 * 1024
    private const val MAX_ATTEMPTS = 8
    private const val MIN_SIDE = 32

    /** Sotto [MIN_SIDE] un'icona si usa solo se non c'è di meglio, e mai con il lato lungo sotto questo. */
    private const val MIN_SMALL_SIDE = 12
    private const val SMALL_TARGET_SIDE = 128

    // Alcuni siti rifiutano i client sconosciuti: intestazione da browser generico, nessun dato personale.
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Mobile"

    /**
     * Icona di [domain] (es. "esselunga.it") già ridotta a [ImageStore.ICON_MAX_SIDE]. Se il
     * catalogo indica un'immagine del logo ([iconUrl], già validata), si prova prima quella:
     * un logo largo viene centrato su un quadrato trasparente, così resta un'icona nel tondo.
     */
    suspend fun fetch(domain: String, iconUrl: String? = null): Bitmap? = withContext(Dispatchers.IO) {
        iconUrl?.let { url ->
            val logo = try {
                get(URL(url), MAX_IMAGE_BYTES, "image/*")?.bytes?.let(Bitmaps::decodeBytes)
            } catch (e: MalformedURLException) {
                null
            }
            if (logo != null && maxOf(logo.width, logo.height) >= MIN_SIDE) {
                return@withContext Bitmaps.fit(Bitmaps.padToSquare(Bitmaps.trimTransparent(logo)), ImageStore.ICON_MAX_SIDE)
            }
        }
        val tried = HashSet<String>()
        var small: Bitmap? = null
        // Alcuni siti rispondono solo su "www." (il dominio nudo non risolve o non ha HTTPS):
        // si prova anche quello, ma solo se il primo non ha dato nemmeno la pagina.
        for (host in hosts(domain)) {
            val start = try {
                URL("https://$host/")
            } catch (e: MalformedURLException) {
                return@withContext null
            }
            val page = get(start, MAX_PAGE_BYTES, "text/html", truncate = true)
            val base = page?.finalUrl ?: start
            val parsed = page?.let { SiteIconParser.parsePage(it.text(), base) }
                ?: SiteIconParser.Page(null, emptyList(), emptyList())
            val manifest = parsed.manifestUrl?.let { url ->
                get(URL(url), MAX_MANIFEST_BYTES, "application/manifest+json,application/json")
                    ?.let { SiteIconParser.parseManifest(it.text(), it.finalUrl) }
            }.orEmpty()
            for (candidate in SiteIconParser.ordered(parsed, manifest, base)) {
                if (!tried.add(candidate.url)) continue
                if (tried.size > MAX_ATTEMPTS) return@withContext null
                val bytes = get(URL(candidate.url), MAX_IMAGE_BYTES, "image/*")?.bytes ?: continue
                val bitmap = Bitmaps.decodeBytes(bytes) ?: continue
                val side = minOf(bitmap.width, bitmap.height)
                if (side >= MIN_SIDE) {
                    return@withContext Bitmaps.fit(bitmap, ImageStore.ICON_MAX_SIDE)
                }
                val longSide = maxOf(bitmap.width, bitmap.height)
                if (longSide >= MIN_SMALL_SIDE && longSide > (small?.let { maxOf(it.width, it.height) } ?: 0)) {
                    small = bitmap
                }
            }
            if (page != null) break
        }
        // Solo una favicon piccola (es. Esselunga, 16×16): meglio quella, ingrandita a pixel netti.
        small?.let(::enlargeSharp)
    }

    /** Ingrandisce di un fattore intero senza filtro, così una favicon da 16 px resta nitida. */
    private fun enlargeSharp(bitmap: Bitmap): Bitmap {
        val factor = (SMALL_TARGET_SIDE / maxOf(bitmap.width, bitmap.height)).coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, bitmap.width * factor, bitmap.height * factor, false)
    }

    /** Il dominio del catalogo e, se non lo è già, la sua variante "www.". */
    internal fun hosts(domain: String): List<String> =
        if (domain.startsWith("www.")) listOf(domain) else listOf(domain, "www.$domain")

    private class Response(val bytes: ByteArray, val finalUrl: URL) {
        fun text(): String = bytes.decodeToString()
    }

    /**
     * Scarica al massimo [maxBytes]. Oltre il limite il file si scarta, tranne con [truncate]
     * (pagine HTML: i `<link>` stanno in `<head>`, all'inizio, anche in pagine da qualche MB).
     */
    private fun get(url: URL, maxBytes: Int, accept: String, truncate: Boolean = false): Response? {
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
            if (!truncate && connection.contentLengthLong > maxBytes) return null
            connection.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    if (out.size() > maxBytes) {
                        if (truncate) break else return null
                    }
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
