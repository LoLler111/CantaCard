package dev.cantacard.data

import android.content.Context
import android.net.Uri
import dev.cantacard.image.ImageLoader
import dev.cantacard.image.ImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backup e ripristino su un file `.zip` scelto dall'utente con lo Storage Access Framework
 * (Drive, OneDrive, cartella del telefono...): nessun permesso storage, nessuna rete.
 * Formato in [BackupFormat].
 */
class Backup(
    private val context: Context,
    private val repository: CardRepository,
    private val images: ImageStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Backup letto, in attesa che l'utente scelga come ripristinarlo (resiste alla rotazione). */
    val pending = MutableStateFlow<Pending?>(null)

    class Pending(
        val content: BackupFormat.Content,
        /** File provvisori delle immagini, per nome della voce nello zip. */
        internal val imageFiles: Map<String, File>,
    )

    class Summary(val added: Int, val updated: Int, val duplicates: Int, val skipped: Int)

    private val restoreDir = File(context.cacheDir, "restore")

    /** Scrive il backup in [uri] e restituisce il numero di tessere salvate. */
    @Throws(IOException::class)
    suspend fun export(uri: Uri): Int = withContext(Dispatchers.IO) {
        val cards = repository.cards.value ?: throw IOException("Tessere non ancora caricate")
        val imageFiles = cards.mapNotNull { card ->
            card.image?.let(images::file)?.takeIf { it.isFile }?.let { card.id to it }
        }.toMap()
        val json = BackupFormat.encode(cards, clock()) { it.id in imageFiles }
        openOutput(uri).use { raw ->
            ZipOutputStream(BufferedOutputStream(raw)).use { zip ->
                zip.putNextEntry(ZipEntry(BackupFormat.JSON_ENTRY))
                zip.write(json.toByteArray())
                zip.closeEntry()
                for (card in cards) {
                    val file = imageFiles[card.id] ?: continue
                    zip.putNextEntry(ZipEntry(BackupFormat.imageEntryFor(card.id)))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        cards.size
    }

    /** Legge il backup in [uri] e lo mette in [pending]; `false` se non è un backup valido. */
    suspend fun read(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val result = readBlocking(uri)
        if (result == null) restoreDir.deleteRecursively()
        pending.value = result
        result != null
    }

    /** Ripristina [pending] (se c'è) nel modo scelto dall'utente. */
    suspend fun restore(mode: RestoreMode): Summary? = withContext(Dispatchers.IO) {
        val backup = pending.value ?: return@withContext null
        // Il dialogo si chiude subito: niente secondo tocco mentre il ripristino è in corso.
        pending.value = null
        // Prima le immagini tra quelle definitive, poi l'elenco in un colpo solo.
        val committed = HashMap<String, String>()
        for (card in backup.content.cards) {
            val file = card.image?.let { backup.imageFiles[it] } ?: continue
            try {
                committed[card.id] = images.commit(card.id, file)
            } catch (e: IOException) {
                // Tessera ripristinata senza immagine.
            }
        }
        val incoming = backup.content.cards.map { it.copy(image = committed[it.id]) }
        val (merged, previousImages) = repository.replace { current ->
            val merged = BackupMerge.merge(current, incoming, mode)
            merged.cards to (merged to current.mapNotNull { it.image })
        }
        // Via le immagini non più usate: tessere sostituite e doppioni lasciati fuori.
        val used = merged.cards.mapNotNullTo(HashSet()) { it.image }
        for (name in previousImages + committed.values) {
            if (name !in used) {
                ImageLoader.evict(images.file(name))
                images.delete(name)
            }
        }
        cancel()
        Summary(merged.added, merged.updated, merged.duplicates, backup.content.skipped)
    }

    fun cancel() {
        pending.value = null
        restoreDir.deleteRecursively()
    }

    private fun openOutput(uri: Uri): OutputStream {
        val resolver = context.contentResolver
        // "wt" tronca un file esistente; alcuni provider accettano solo "w".
        val stream = try {
            resolver.openOutputStream(uri, "wt")
        } catch (e: FileNotFoundException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        } ?: resolver.openOutputStream(uri, "w")
        return stream ?: throw IOException("Impossibile scrivere $uri")
    }

    private fun readBlocking(uri: Uri): Pending? {
        restoreDir.deleteRecursively()
        restoreDir.mkdirs()
        var json: String? = null
        val files = HashMap<String, File>()
        var total = 0L
        var entries = 0
        try {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (++entries > MAX_ENTRIES) return null
                    val name = entry.name
                    if (entry.isDirectory) continue
                    if (name == BackupFormat.JSON_ENTRY) {
                        json = zip.readAtMost(MAX_JSON_BYTES)?.decodeToString() ?: return null
                    } else if (BackupFormat.isImageEntry(name) && name !in files) {
                        // Immagine troppo grande: la tessera torna senza.
                        val bytes = zip.readAtMost(MAX_IMAGE_BYTES) ?: continue
                        total += bytes.size
                        if (total > MAX_TOTAL_BYTES) return null
                        val file = File(restoreDir, "${files.size}.webp")
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
        } catch (e: SecurityException) {
            return null
        }
        val content = try {
            BackupFormat.decode(json ?: return null)
        } catch (e: JSONException) {
            return null
        }
        return Pending(content, files)
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

    private companion object {
        const val MAX_ENTRIES = 5_000
        const val MAX_JSON_BYTES = 10 * 1024 * 1024
        const val MAX_IMAGE_BYTES = 2 * 1024 * 1024
        const val MAX_TOTAL_BYTES = 200L * 1024 * 1024
    }
}
