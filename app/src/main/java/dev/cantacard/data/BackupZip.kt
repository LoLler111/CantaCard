package dev.cantacard.data

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Lettura e scrittura dello zip di backup e di sincronizzazione (stesso formato). */
internal object BackupZip {

    class Contents(
        val json: String,
        /** File provvisori delle immagini, per nome della voce nello zip. */
        val images: Map<String, File>,
    )

    private const val MAX_ENTRIES = 5_000
    private const val MAX_JSON_BYTES = 10 * 1024 * 1024
    private const val MAX_IMAGE_BYTES = 2 * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 200L * 1024 * 1024

    /** @param images voce dello zip → file da includere. */
    @Throws(IOException::class)
    fun write(out: OutputStream, json: String, images: Map<String, File>) {
        ZipOutputStream(BufferedOutputStream(out)).use { zip ->
            zip.putNextEntry(ZipEntry(BackupFormat.JSON_ENTRY))
            zip.write(json.toByteArray())
            zip.closeEntry()
            for ((entry, file) in images) {
                zip.putNextEntry(ZipEntry(entry))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /**
     * Legge lo zip mettendo le immagini in [dir] (svuotata prima). `null` se non è uno zip valido
     * o non contiene `cantacard.json`. Limiti di dimensione contro file enormi o malformati.
     */
    fun read(input: InputStream, dir: File): Contents? {
        dir.deleteRecursively()
        dir.mkdirs()
        var json: String? = null
        val files = HashMap<String, File>()
        var total = 0L
        var entries = 0
        try {
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (++entries > MAX_ENTRIES) return null
                    val name = entry.name
                    if (entry.isDirectory) continue
                    if (name == BackupFormat.JSON_ENTRY) {
                        json = zip.readAtMost(MAX_JSON_BYTES)?.decodeToString() ?: return null
                    } else if (BackupFormat.isImageEntry(name) && name !in files) {
                        // Immagine troppo grande: la tessera resta senza.
                        val bytes = zip.readAtMost(MAX_IMAGE_BYTES) ?: continue
                        total += bytes.size
                        if (total > MAX_TOTAL_BYTES) return null
                        val file = File(dir, "${files.size}.webp")
                        file.writeBytes(bytes)
                        files[name] = file
                    }
                }
            }
        } catch (e: IOException) {
            return null
        } catch (e: IllegalArgumentException) {
            // Nomi di voce non validi nello zip.
            return null
        }
        return Contents(json ?: return null, files)
    }

    /** Legge la voce corrente fino a [limit] byte; `null` se è più grande. */
    private fun InputStream.readAtMost(limit: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > limit) return null
        }
        return out.toByteArray()
    }
}
