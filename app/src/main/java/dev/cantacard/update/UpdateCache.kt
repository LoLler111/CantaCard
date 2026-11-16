package dev.cantacard.update

import java.io.File
import java.io.IOException

/**
 * Ultima risposta dell'elenco delle release con il suo `ETag`, per le richieste condizionali
 * (`If-None-Match`). Due file in [dir]: se uno manca o non si legge non si usa niente.
 */
class UpdateCache(private val dir: File) {

    class Entry(val etag: String, val body: String)

    private val etagFile get() = File(dir, "etag")
    private val bodyFile get() = File(dir, "releases.json")

    fun read(): Entry? = try {
        val etag = etagFile.takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
        val body = bodyFile.takeIf { it.isFile }?.readText()
        if (etag != null && body != null) Entry(etag, body) else null
    } catch (e: IOException) {
        null
    }

    /** Senza `ETag` (o con uno strano) si cancella la copia: la prossima volta si richiede tutto. */
    fun write(etag: String?, body: String) {
        try {
            val valid = etag?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_ETAG && '\n' !in it }
            if (valid == null) {
                clear()
                return
            }
            dir.mkdirs()
            // Prima il corpo, poi l'ETag: un ETag senza il suo corpo non viene mai usato.
            etagFile.delete()
            bodyFile.writeText(body)
            etagFile.writeText(valid)
        } catch (e: IOException) {
            clear()
        }
    }

    fun clear() {
        etagFile.delete()
        bodyFile.delete()
    }

    private companion object {
        const val MAX_ETAG = 256
    }
}
