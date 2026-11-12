package dev.cantacard.data

import android.util.AtomicFile
import android.util.Log
import org.json.JSONException
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Persistenza delle tessere su un singolo file JSON, con scrittura atomica.
 * Non è thread-safe: le chiamate vanno serializzate (lo fa [CardRepository]).
 */
class CardStore(private val file: File) {

    private val atomic = AtomicFile(file)

    fun read(): List<Card> {
        val text = try {
            atomic.readFully().decodeToString()
        } catch (e: FileNotFoundException) {
            return emptyList()
        }
        return try {
            val decoded = CardJson.decode(text)
            // Record scartati: teniamo una copia del file originale prima di riscriverlo.
            if (decoded.skipped > 0) setAside("partial")
            decoded.cards
        } catch (e: JSONException) {
            // File illeggibile: lo mettiamo da parte invece di sovrascriverlo.
            setAside("corrupt")
            emptyList()
        }
    }

    @Throws(IOException::class)
    fun write(cards: List<Card>) {
        val out = atomic.startWrite()
        try {
            out.write(CardJson.encode(cards).toByteArray())
            atomic.finishWrite(out)
        } catch (e: IOException) {
            atomic.failWrite(out)
            throw e
        }
    }

    private fun setAside(reason: String) {
        val copy = File(file.parentFile, "${file.name}.$reason-${System.currentTimeMillis()}")
        try {
            file.copyTo(copy, overwrite = true)
            Log.w(TAG, "${file.name} non leggibile del tutto, copia in ${copy.name}")
        } catch (e: IOException) {
            Log.e(TAG, "Impossibile salvare la copia di ${file.name}", e)
        }
    }

    private companion object {
        const val TAG = "CardStore"
    }
}
