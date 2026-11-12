package dev.cantacard.image

import android.media.ExifInterface
import java.io.IOException
import java.io.InputStream

/** Rotazione EXIF delle foto, solo per Android 8 (da Android 9 la applica già ImageDecoder). */
internal object ExifRotation {
    fun degrees(input: InputStream): Int = try {
        when (ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (e: IOException) {
        0
    }
}
