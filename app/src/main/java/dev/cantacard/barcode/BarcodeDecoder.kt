package dev.cantacard.barcode

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import dev.cantacard.data.CodeFormat

/** Codice letto da fotocamera o immagine. */
data class ScannedCode(val text: String, val format: CodeFormat)

/** Lettura dei codici con ZXing (JVM puro, testabile senza Android). */
object BarcodeDecoder {

    private val formats: List<BarcodeFormat> = CodeFormat.entries.map { BarcodeFormat.valueOf(it.name) }

    /**
     * Lettore riutilizzabile, non thread-safe. [tryHarder] è più lento: va bene per una singola
     * immagine, non per i frame della fotocamera.
     */
    fun newReader(tryHarder: Boolean): MultiFormatReader = MultiFormatReader().apply {
        val hints = HashMap<DecodeHintType, Any>()
        hints[DecodeHintType.POSSIBLE_FORMATS] = formats
        if (tryHarder) hints[DecodeHintType.TRY_HARDER] = true
        setHints(hints)
    }

    fun decode(reader: MultiFormatReader, source: LuminanceSource): ScannedCode? = try {
        val result = reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
        val format = CodeFormat.fromName(result.barcodeFormat.name)
        if (format != null && !result.text.isNullOrEmpty()) ScannedCode(result.text, format) else null
    } catch (e: ReaderException) {
        null
    } finally {
        reader.reset()
    }

    /**
     * Cerca un codice in un'immagine ARGB (foto o screenshot), provando anche ruotata di 90°:
     * i lettori 1D di ZXing leggono solo barre verticali.
     */
    fun decodeImage(pixels: IntArray, width: Int, height: Int): ScannedCode? {
        val reader = newReader(tryHarder = true)
        decode(reader, RGBLuminanceSource(width, height, pixels))?.let { return it }
        val rotated = IntArray(pixels.size)
        // Rotazione oraria: (x, y) -> (height - 1 - y, x) in un'immagine larga height.
        for (y in 0 until height) {
            for (x in 0 until width) {
                rotated[x * height + (height - 1 - y)] = pixels[y * width + x]
            }
        }
        return decode(reader, RGBLuminanceSource(height, width, rotated))
    }
}
