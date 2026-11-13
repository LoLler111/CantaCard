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

    fun read(): CardSnapshot {
        val text = try {
            atomic.readFully().decodeToString()
        } catch (e: FileNotFoundException) {
            return CardSnapshot(emptyList())
        }
        return try {
            val decoded = CardJson.decode(text)
            // Record scartati: teniamo una copia del file originale prima di riscriverlo.
            if (decoded.skipped > 0) setAside("partial")
            CardSnapshot(decoded.cards, decoded.deleted)
        } catch (e: JSONException) {
            // File illeggibile: lo mettiamo da parte invece di sovrascriverlo.
            setAside("corrupt")
            CardSnapshot(emptyList())
        }
    }

    @Throws(IOException::class)
    fun write(snapshot: CardSnapshot) {
        val out = atomic.startWrite()
        try {
            out.write(CardJson.encode(snapshot.cards, snapshot.deleted).toByteArray())
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
