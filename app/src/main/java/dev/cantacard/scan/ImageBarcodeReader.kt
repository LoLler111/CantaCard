package dev.cantacard.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import dev.cantacard.barcode.BarcodeDecoder
import dev.cantacard.barcode.ScannedCode
import java.io.IOException
import kotlin.math.max

/** Legge un codice da un'immagine scelta dall'utente (foto o screenshot della tessera). */
object ImageBarcodeReader {

    /** Lato massimo dell'immagine analizzata: basta per i codici e limita memoria e tempo. */
    private const val MAX_SIDE = 2048

    sealed interface Result {
        data class Found(val code: ScannedCode) : Result
        data object NotFound : Result
        data object Unreadable : Result
    }

    /** Da chiamare fuori dal thread principale. */
    fun read(context: Context, uri: Uri): Result {
        val bitmap = try {
            load(context, uri)
        } catch (e: IOException) {
            null
        } catch (e: RuntimeException) {
            null
        } ?: return Result.Unreadable
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val code = BarcodeDecoder.decodeImage(pixels, bitmap.width, bitmap.height)
        bitmap.recycle()
        return if (code != null) Result.Found(code) else Result.NotFound
    }

    private fun load(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val side = max(info.size.width, info.size.height)
                if (side > MAX_SIDE) {
                    val ratio = MAX_SIDE.toFloat() / side
                    decoder.setTargetSize(
                        (info.size.width * ratio).toInt().coerceAtLeast(1),
                        (info.size.height * ratio).toInt().coerceAtLeast(1),
                    )
                }
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }
}
