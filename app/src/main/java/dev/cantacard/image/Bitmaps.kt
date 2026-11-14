package dev.cantacard.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/** Decodifica, ridimensionamento e salvataggio WebP, solo con le API del framework. */
object Bitmaps {

    /** Lato massimo delle immagini da ritagliare: abbastanza per una foto nitida, poca memoria. */
    const val SOURCE_MAX_SIDE = 2048

    /**
     * Decodifica un'immagine (Photo Picker, condivisione) ridotta a [maxSide], già raddrizzata
     * secondo l'EXIF. `null` se non è un'immagine leggibile. Da chiamare fuori dal thread principale.
     */
    fun decode(context: Context, uri: Uri, maxSide: Int = SOURCE_MAX_SIDE): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder applica già l'orientamento EXIF.
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val side = max(info.size.width, info.size.height)
                if (side > maxSide) {
                    val ratio = maxSide.toFloat() / side
                    decoder.setTargetSize(
                        (info.size.width * ratio).roundToInt().coerceAtLeast(1),
                        (info.size.height * ratio).roundToInt().coerceAtLeast(1),
                    )
                }
            }
        } else {
            decodeLegacy(context, uri, maxSide)
        }
    } catch (e: IOException) {
        null
    } catch (e: RuntimeException) {
        null
    }

    /** Decodifica byte già scaricati (icone dei siti, anche .ico); `null` se non leggibili. */
    fun decodeBytes(bytes: ByteArray): Bitmap? = try {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: RuntimeException) {
        null
    }

    /** Riduce l'immagine perché il lato maggiore non superi [maxSide] (mai ingrandita). */
    fun fit(bitmap: Bitmap, maxSide: Int): Bitmap {
        val side = max(bitmap.width, bitmap.height)
        if (side <= maxSide) return bitmap
        val ratio = maxSide.toFloat() / side
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).roundToInt().coerceAtLeast(1),
            (bitmap.height * ratio).roundToInt().coerceAtLeast(1),
            true,
        )
    }

    /** Toglie i bordi del tutto trasparenti attorno a un logo (resta com'è se non ce ne sono). */
    fun trimTransparent(bitmap: Bitmap): Bitmap {
        if (!bitmap.hasAlpha()) return bitmap
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        var left = w
        var right = -1
        var top = h
        var bottom = -1
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (pixels[y * w + x] ushr 24 > ALPHA_THRESHOLD) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        if (right < 0) return bitmap
        if (left == 0 && top == 0 && right == w - 1 && bottom == h - 1) return bitmap
        return Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1)
    }

    private const val ALPHA_THRESHOLD = 8

    /** Centra l'immagine su un quadrato trasparente (un logo largo resta un'icona, non una foto). */
    fun padToSquare(bitmap: Bitmap): Bitmap {
        if (bitmap.width == bitmap.height) return bitmap
        val side = max(bitmap.width, bitmap.height)
        val square = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        Canvas(square).drawBitmap(
            bitmap,
            (side - bitmap.width) / 2f,
            (side - bitmap.height) / 2f,
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        return square
    }

    fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** Scrive [bitmap] in WebP su [file] passando da un file temporaneo (mai file a metà). */
    @Throws(IOException::class)
    fun writeWebp(bitmap: Bitmap, file: File, quality: Int = 85) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        @Suppress("DEPRECATION")
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }
        FileOutputStream(tmp).use { out ->
            if (!bitmap.compress(format, quality, out)) throw IOException("Compressione WebP non riuscita")
            out.fd.sync()
        }
        if (!tmp.renameTo(file)) {
            tmp.delete()
            throw IOException("Impossibile salvare ${file.name}")
        }
    }

    /** JPEG per i file temporanei da ritagliare (più veloce del WebP, nessuna perdita visibile). */
    @Throws(IOException::class)
    fun writeJpeg(bitmap: Bitmap, file: File) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)) throw IOException("Compressione JPEG non riuscita")
        }
    }

    fun decodeFile(file: File): Bitmap? = try {
        BitmapFactory.decodeFile(file.path)
    } catch (e: RuntimeException) {
        null
    }

    private fun decodeLegacy(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return null
        val degrees = resolver.openInputStream(uri)?.use { ExifRotation.degrees(it) } ?: 0
        return fit(rotate(bitmap, degrees), maxSide)
    }
}
