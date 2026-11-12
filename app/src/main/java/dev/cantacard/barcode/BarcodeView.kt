package dev.cantacard.barcode

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * Disegna il codice centrato nello spazio disponibile, su bianco, con quiet zone e scala
 * calcolate da [BarcodeLayout]. L'immagine è a 1 px per modulo e viene ingrandita senza
 * filtro, così i bordi delle barre restano netti.
 */
@Composable
fun BarcodeView(matrix: BarcodeMatrix, modifier: Modifier = Modifier) {
    val image = remember(matrix) { matrix.toImageBitmap() }
    Canvas(modifier) {
        drawRect(Color.White)
        val layout = BarcodeLayout.compute(matrix, size.width, size.height)
        val width = layout.codeWidth.roundToInt()
        val height = layout.codeHeight.roundToInt()
        if (width <= 0 || height <= 0) return@Canvas
        drawImage(
            image = image,
            dstOffset = IntOffset(
                ((size.width - width) / 2).roundToInt(),
                ((size.height - height) / 2).roundToInt(),
            ),
            dstSize = IntSize(width, height),
            filterQuality = FilterQuality.None,
        )
    }
}

private fun BarcodeMatrix.toImageBitmap(): ImageBitmap {
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        val row = y * width
        for (x in 0 until width) {
            pixels[row + x] = if (this[x, y]) BLACK else WHITE
        }
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
}

private const val BLACK = 0xFF000000.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()
