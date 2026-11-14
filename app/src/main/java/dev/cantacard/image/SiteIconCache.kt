package dev.cantacard.image

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Icone dei siti già scaricate, una per dominio, in `cache/site-icons/<dominio>.webp`: chi
 * aggiunge una seconda tessera dello stesso negozio (o cambia negozio avanti e indietro) non
 * rifà le richieste. Una copia più vecchia di [MAX_AGE_MS] si riscarica alla prossima richiesta;
 * se la rete non risponde si usa comunque quella vecchia. È una cache vera: Android può svuotarla
 * quando manca spazio, e le tessere non ne risentono perché hanno la propria copia in `files/images`.
 */
class SiteIconCache(private val dir: File) {

    /**
     * File WebP con l'icona di [domain], da copiare tra le immagini provvisorie; `null` se non si
     * trova. Con [refresh] si scarica sempre di nuovo (pulsante "Aggiorna icona"). [iconUrl] è
     * l'immagine del logo indicata dal catalogo, se c'è (stessa cache del dominio).
     */
    suspend fun get(domain: String, refresh: Boolean = false, iconUrl: String? = null): File? = withContext(Dispatchers.IO) {
        val name = fileName(domain) ?: return@withContext null
        val file = File(dir, name)
        val cached = file.takeIf { it.isFile && it.length() > 0 }
        if (!refresh && cached != null && isFresh(cached.lastModified(), System.currentTimeMillis())) {
            return@withContext cached
        }
        val bitmap = SiteIconFetcher.fetch(domain, iconUrl)
        if (bitmap != null) {
            try {
                Bitmaps.writeWebp(bitmap, file)
                return@withContext file
            } catch (e: IOException) {
                // Si ricade sulla copia vecchia, se c'è.
            }
        }
        // Senza rete o sito irraggiungibile: meglio l'icona vecchia che nessuna.
        if (refresh) null else cached
    }

    companion object {
        /** Dopo 30 giorni l'icona si riscarica (il negozio potrebbe aver cambiato grafica). */
        val MAX_AGE_MS: Long = TimeUnit.DAYS.toMillis(30)

        fun isFresh(modified: Long, now: Long): Boolean = modified in (now - MAX_AGE_MS)..now

        /** Nome del file per [domain], solo con caratteri da dominio; `null` se non è valido. */
        fun fileName(domain: String): String? {
            val clean = domain.trim().lowercase(Locale.ROOT)
            if (clean.isEmpty() || clean.length > 253 || clean.startsWith('.')) return null
            if (!clean.all { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '-' }) return null
            return "$clean.webp"
        }
    }
}
