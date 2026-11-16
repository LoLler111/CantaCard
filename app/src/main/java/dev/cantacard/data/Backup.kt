package dev.cantacard.data

import android.content.Context
import android.net.Uri
import dev.cantacard.image.ImageLoader
import dev.cantacard.image.ImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream

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
        val imageFiles = imageEntries(cards, images)
        val json = BackupFormat.encode(cards, clock()) { it in imageFiles }
        openOutput(context, uri).use { BackupZip.write(it, json, imageFiles) }
        cards.size
    }

    /** Legge il backup in [uri] e lo mette in [pending]; `false` se non è un backup valido. */
    suspend fun read(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val result = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BackupZip.read(input, restoreDir)?.let { zip ->
                    Pending(BackupFormat.decode(zip.json), zip.images)
                }
            }
        } catch (e: IOException) {
            null
        } catch (e: JSONException) {
            null
        } catch (e: SecurityException) {
            null
        }
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
        val committed = commitImages(backup.content.cards, backup.imageFiles, images)
        val incoming = backup.content.cards.map { it.withImages(committed[it.id]) }
        val (merged, previous) = repository.replace { current ->
            val merged = BackupMerge.merge(current.cards, incoming, mode)
            val now = clock()
            val ids = merged.cards.mapTo(HashSet()) { it.id }
            // Una tessera ripristinata che era stata cancellata qui torna "modificata adesso",
            // così la sincronizzazione non la ricancella; quelle sostituite contano come cancellate.
            val cards = merged.cards.map { if (it.id in current.deleted) it.copy(updatedAt = maxOf(it.updatedAt, now)) else it }
            val removed = current.cards.filter { it.id !in ids }.associate { it.id to now }
            CardSnapshot(cards, current.deleted - ids + removed) to (merged to current.cards)
        }
        val candidates = previous.flatMap { it.imageFiles() } + committed.values.flatMap { it.files() }
        deleteUnusedImages(candidates, merged.cards, images)
        restoreDir.deleteRecursively()
        Summary(merged.added, merged.updated, merged.duplicates, backup.content.skipped)
    }

    fun cancel() {
        pending.value = null
        restoreDir.deleteRecursively()
    }

    /** File definitivi delle immagini di una tessera arrivata da uno zip. */
    data class CardImages(val front: String?, val back: String?) {
        fun files(): List<String> = listOfNotNull(front, back)
    }

    internal companion object {
        /** Voce dello zip → file immagine (fronte e retro), per le tessere che hanno davvero il file. */
        fun imageEntries(cards: List<Card>, images: ImageStore): Map<String, File> = buildMap {
            for (card in cards) {
                card.image?.let(images::file)?.takeIf { it.isFile }?.let { put(BackupFormat.imageEntryFor(card.id), it) }
                card.backImage?.let(images::file)?.takeIf { it.isFile }?.let {
                    put(BackupFormat.backImageEntryFor(card.id), it)
                }
            }
        }

        /** Sposta tra le immagini definitive quelle dello zip; id tessera → nuovi nomi dei file. */
        fun commitImages(cards: List<Card>, files: Map<String, File>, images: ImageStore): Map<String, CardImages> {
            val committed = HashMap<String, CardImages>()
            for (card in cards) {
                val front = commitOne(card.image?.let { files[it] }, card.id, images)
                val back = commitOne(card.backImage?.let { files[it] }, card.id + "-back", images)
                if (front != null || back != null) committed[card.id] = CardImages(front, back)
            }
            return committed
        }

        private fun commitOne(file: File?, prefix: String, images: ImageStore): String? {
            file ?: return null
            return try {
                images.commit(prefix, file)
            } catch (e: IOException) {
                // Tessera senza questa immagine.
                null
            }
        }

        /** Cancella i file in [candidates] che nessuna tessera di [cards] usa più. */
        fun deleteUnusedImages(candidates: Collection<String>, cards: List<Card>, images: ImageStore) {
            val used = cards.flatMapTo(HashSet()) { it.imageFiles() }
            for (name in candidates) {
                if (name !in used) {
                    ImageLoader.evict(images.file(name))
                    images.delete(name)
                }
            }
        }

        @Throws(IOException::class)
        fun openOutput(context: Context, uri: Uri): OutputStream {
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
    }
}

/** Nomi dei file immagine (fronte e retro) di una tessera. */
fun Card.imageFiles(): List<String> = listOfNotNull(image, backImage)

/** La tessera con le immagini arrivate da uno zip (nessuna se [images] è `null`). */
internal fun Card.withImages(images: Backup.CardImages?): Card = copy(image = images?.front, backImage = images?.back)
