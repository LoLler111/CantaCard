package dev.cantacard.barcode

import dev.cantacard.data.CodeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Genera un codice, lo "fotografa" come immagine e verifica che venga riletto uguale. */
class BarcodeDecoderTest {

    /** Immagine ARGB del codice con quiet zone, [scale] px per modulo, barre alte [barHeight] px. */
    private fun render(matrix: BarcodeMatrix, scale: Int = 4, barHeight: Int = 120): Triple<IntArray, Int, Int> {
        val quiet = if (matrix.isLinear) 12 else 4
        val w = (matrix.width + 2 * quiet) * scale
        val h = if (matrix.isLinear) barHeight + 2 * quiet * scale else (matrix.height + 2 * quiet) * scale
        val pixels = IntArray(w * h) { WHITE }
        for (y in 0 until h) for (x in 0 until w) {
            val mx = x / scale - quiet
            val my = if (matrix.isLinear) 0 else y / scale - quiet
            val inside = mx in 0 until matrix.width && my in 0 until matrix.height &&
                (!matrix.isLinear || y in quiet * scale until h - quiet * scale)
            if (inside && matrix[mx, my]) pixels[y * w + x] = BLACK
        }
        return Triple(pixels, w, h)
    }

    private fun roundTrip(text: String, format: CodeFormat): ScannedCode? {
        val (pixels, w, h) = render(BarcodeEncoder.encode(text, format)!!)
        return BarcodeDecoder.decodeImage(pixels, w, h)
    }

    @Test
    fun readsBackCommonLoyaltyFormats() {
        listOf(
            CodeFormat.EAN_13 to "8001234567897",
            CodeFormat.EAN_8 to "96385074",
            CodeFormat.CODE_128 to "CARD-0042-xy",
            CodeFormat.CODE_39 to "ABC12345",
            CodeFormat.ITF to "12345678",
            CodeFormat.QR_CODE to "https://example.com/tessera/123",
            CodeFormat.DATA_MATRIX to "123456789",
            CodeFormat.PDF_417 to "123456789",
        ).forEach { (format, text) ->
            assertEquals("$format", ScannedCode(text, format), roundTrip(text, format))
        }
    }

    @Test
    fun readsALinearCodeRotatedByNinetyDegrees() {
        val (pixels, w, h) = render(BarcodeEncoder.encode("4006381333931", CodeFormat.EAN_13)!!)
        // Ruota l'immagine: le barre diventano orizzontali.
        val rotated = IntArray(pixels.size)
        for (y in 0 until h) for (x in 0 until w) rotated[x * h + (h - 1 - y)] = pixels[y * w + x]
        assertEquals(
            ScannedCode("4006381333931", CodeFormat.EAN_13),
            BarcodeDecoder.decodeImage(rotated, h, w),
        )
    }

    @Test
    fun blankImageHasNoCode() {
        assertNull(BarcodeDecoder.decodeImage(IntArray(200 * 100) { WHITE }, 200, 100))
    }

    private companion object {
        const val BLACK = 0xFF000000.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
    }
}
