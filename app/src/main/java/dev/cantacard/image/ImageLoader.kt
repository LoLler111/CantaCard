package dev.cantacard.image

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Immagine pronta da disegnare, con il suo tipo (foto della tessera o icona del negozio). */
@Immutable
class CardImage(val bitmap: ImageBitmap, val isPhoto: Boolean)

/** Caricatore minimo: decodifica in background e tiene in memoria le immagini recenti. */
object ImageLoader {

    // Le immagini sono già piccole (≤ 512 px): 8 MB bastano per decine di tessere.
    private val cache = object : LruCache<String, CardImage>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: CardImage): Int = value.bitmap.width * value.bitmap.height * 4
    }

    fun cached(file: File): CardImage? = cache.get(file.path)

    suspend fun load(file: File): CardImage? = cached(file) ?: withContext(Dispatchers.IO) {
        val bitmap: Bitmap = Bitmaps.decodeFile(file) ?: return@withContext null
        CardImage(bitmap.asImageBitmap(), ImageStore.isPhoto(bitmap.width, bitmap.height))
            .also { cache.put(file.path, it) }
    }

    fun evict(file: File) {
        cache.remove(file.path)
    }
}

/** Immagine della tessera da [file] (`null` se assente o illeggibile), caricata in background. */
@Composable
fun rememberCardImage(file: File?): CardImage? {
    val image by produceState(initialValue = file?.let(ImageLoader::cached), file) {
        value = file?.let { ImageLoader.load(it) }
    }
    return image
}
