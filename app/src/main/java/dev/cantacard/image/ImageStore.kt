package dev.cantacard.image

import android.content.Context
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * File delle immagini delle tessere, solo in locale:
 * - definitive in `files/images/<idTessera>-<versione>.webp` (il nome cambia a ogni sostituzione,
 *   così la cache e la UI si aggiornano senza confronti sul contenuto);
 * - provvisorie, durante la modifica, in `cache/edit-images/` (si perdono se si annulla).
 */
class ImageStore(context: Context) {

    val dir = File(context.filesDir, "images")
    private val tempDir = File(context.cacheDir, "edit-images")

    fun file(name: String): File = File(dir, name)

    fun newTemp(extension: String): File {
        tempDir.mkdirs()
        return File(tempDir, "${UUID.randomUUID()}.$extension")
    }

    /** Svuota i file provvisori (all'apertura di un nuovo modulo), tranne [except]. */
    fun clearTemp(except: String? = null) {
        tempDir.listFiles()?.forEach { if (it.path != except) it.delete() }
    }

    /** Sposta l'immagine provvisoria tra quelle definitive e restituisce il nuovo nome. */
    @Throws(IOException::class)
    fun commit(cardId: String, temp: File): String {
        dir.mkdirs()
        val name = "$cardId-${System.currentTimeMillis()}.webp"
        val target = File(dir, name)
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        return name
    }

    fun delete(name: String?) {
        if (name.isNullOrEmpty() || name.contains('/') || name.contains(File.separatorChar)) return
        File(dir, name).delete()
    }

    companion object {
        /** Icone dei negozi: lato massimo. */
        const val ICON_MAX_SIDE = 256

        /**
         * Foto della tessera: larghezza dopo il ritaglio. Più di 256 px perché riempie tutta la
         * tessera in lista (fino a ~480 px sullo schermo); in WebP pesa comunque ~20–40 KB.
         */
        const val PHOTO_WIDTH = 512

        /** Un'immagine più larga che alta (rapporto da tessera) è una foto, non un'icona. */
        fun isPhoto(width: Int, height: Int): Boolean = height > 0 && width.toFloat() / height > 1.3f
    }
}
