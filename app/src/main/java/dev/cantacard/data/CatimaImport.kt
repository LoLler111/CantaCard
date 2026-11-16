package dev.cantacard.data

import android.content.Context
import android.net.Uri
import dev.cantacard.image.Bitmaps
import dev.cantacard.image.ImageStore
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/**
 * Importazione dall'esportazione di Catima: lo zip (`catima.csv` più le immagini
 * `card_<id>_front.png`, `_back.png`, `_icon.png`) oppure il solo CSV. Le immagini diventano WebP
 * come quelle dell'app (fronte largo 512 px, icona 256 px). Il risultato ha la stessa forma di un
 * backup letto, così l'aggiunta passa dallo stesso ripristino (senza doppioni).
 */
internal object CatimaImport {

    sealed interface Result {
        class Read(val content: BackupFormat.Content, val images: Map<String, File>) : Result
        data object NotCatima : Result
        data object Encrypted : Result
    }

    private val imageEntry = Regex("(?:.*/)?card_([A-Za-z0-9-]{1,40})_(front|back|icon)\\.png")
    private const val MAX_CSV_BYTES = 10 * 1024 * 1024
    private const val MAX_IMAGE_BYTES = 15 * 1024 * 1024
    private const val MAX_ENTRIES = 10_000

    @Throws(IOException::class)
    fun read(context: Context, uri: Uri, dir: File): Result {
        dir.deleteRecursively()
        dir.mkdirs()
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("File non apribile")
        return BufferedInputStream(input).use { stream ->
            stream.mark(4)
            val zip = stream.read() == 'P'.code && stream.read() == 'K'.code
            stream.reset()
            if (zip) readZip(stream, dir) else fromCsv(stream.readAtMost(MAX_CSV_BYTES)?.decodeToString(), emptyMap(), dir)
        }
    }

    private fun readZip(stream: InputStream, dir: File): Result {
        var csv: String? = null
        val raw = HashMap<String, File>()
        try {
            ZipInputStream(stream).use { zip ->
                var count = 0
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (++count > MAX_ENTRIES || entry.isDirectory) continue
                    val name = entry.name
                    if (name.endsWith(".csv", ignoreCase = true) && csv == null) {
                        csv = zip.readAtMost(MAX_CSV_BYTES)?.decodeToString()
                    } else {
                        val match = imageEntry.matchEntire(name) ?: continue
                        val key = match.groupValues[1] + "_" + match.groupValues[2]
                        val bytes = zip.readAtMost(MAX_IMAGE_BYTES) ?: continue
                        val file = File(dir, "raw-${raw.size}.png")
                        file.writeBytes(bytes)
                        raw[key] = file
                    }
                }
            }
        } catch (e: ZipException) {
            // Zip con password: java.util.zip non legge le voci cifrate.
            if (e.message?.contains("encrypt", ignoreCase = true) == true) return Result.Encrypted
            throw e
        }
        return fromCsv(csv, raw, dir)
    }

    private fun fromCsv(csv: String?, raw: Map<String, File>, dir: File): Result {
        val entries = csv?.let { CatimaCsv.parse(it) } ?: return Result.NotCatima
        val images = HashMap<String, File>()
        val cards = entries.map { entry ->
            val id = entry.card.id
            val front = raw[entry.catimaId + "_front"]?.let { convert(it, ImageStore.PHOTO_WIDTH, File(dir, "$id.webp")) }
                ?: raw[entry.catimaId + "_icon"]?.let { convert(it, ImageStore.ICON_MAX_SIDE, File(dir, "$id.webp")) }
            val back = raw[entry.catimaId + "_back"]?.let { convert(it, ImageStore.PHOTO_WIDTH, File(dir, "$id-back.webp")) }
            front?.let { images[BackupFormat.imageEntryFor(id)] = it }
            back?.let { images[BackupFormat.backImageEntryFor(id)] = it }
            entry.card.copy(
                image = front?.let { BackupFormat.imageEntryFor(id) },
                backImage = back?.let { BackupFormat.backImageEntryFor(id) },
            )
        }
        raw.values.forEach { it.delete() }
        return Result.Read(BackupFormat.Content(BackupFormat.FORMAT_VERSION, 0L, cards, skipped = 0), images)
    }

    /** PNG di Catima → WebP ridimensionato; `null` se l'immagine non si legge. */
    private fun convert(source: File, maxSide: Int, target: File): File? {
        val bitmap = Bitmaps.decodeFile(source) ?: return null
        return try {
            Bitmaps.writeWebp(Bitmaps.fit(bitmap, maxSide), target)
            target
        } catch (e: IOException) {
            null
        }
    }

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
