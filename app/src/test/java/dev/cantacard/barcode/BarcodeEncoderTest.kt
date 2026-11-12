package dev.cantacard.barcode

import dev.cantacard.data.CodeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeEncoderTest {

    @Test
    fun ean13IsLinearWithoutMargin() {
        val m = requireCode(BarcodeEncoder.encode("4006381333931", CodeFormat.EAN_13))
        assertTrue(m.isLinear)
        assertEquals(95, m.width) // 95 moduli, nessuna quiet zone aggiunta da ZXing
        assertTrue(m[0, 0]) // barra di guardia iniziale "101"
        assertFalse(m[1, 0])
        assertTrue(m[2, 0])
    }

    @Test
    fun ean13ChecksumIsComputedWhenMissing() {
        val withCheck = BarcodeEncoder.encode("4006381333931", CodeFormat.EAN_13)!!
        val without = BarcodeEncoder.encode("400638133393", CodeFormat.EAN_13)!!
        assertEquals((0 until 95).map { withCheck[it, 0] }, (0 until 95).map { without[it, 0] })
    }

    @Test
    fun invalidContentReturnsNull() {
        assertNull(BarcodeEncoder.encode("4006381333932", CodeFormat.EAN_13)) // cifra di controllo errata
        assertNull(BarcodeEncoder.encode("ABC", CodeFormat.EAN_13))
        assertNull(BarcodeEncoder.encode("123", CodeFormat.EAN_8))
        assertNull(BarcodeEncoder.encode("", CodeFormat.QR_CODE))
    }

    @Test
    fun everyFormatEncodesATypicalValue() {
        val samples = mapOf(
            CodeFormat.EAN_13 to "8001234567897",
            CodeFormat.EAN_8 to "96385074",
            CodeFormat.UPC_A to "036000291452",
            CodeFormat.UPC_E to "01234565",
            CodeFormat.CODE_128 to "CARD-0042-xy",
            CodeFormat.CODE_39 to "ABC12345",
            CodeFormat.CODE_93 to "ABC12345",
            CodeFormat.CODABAR to "A123456B",
            CodeFormat.ITF to "12345678",
            CodeFormat.QR_CODE to "https://example.com/tessera/123",
            CodeFormat.DATA_MATRIX to "123456789",
            CodeFormat.AZTEC to "123456789",
            CodeFormat.PDF_417 to "123456789",
        )
        assertEquals(CodeFormat.entries.toSet(), samples.keys)
        samples.forEach { (format, value) ->
            val m = BarcodeEncoder.encode(value, format)
            assertNotNull("$format non generato", m)
        }
    }

    @Test
    fun qrIsSquareMatrixAndAcceptsUnicode() {
        val m = requireCode(BarcodeEncoder.encode("Tessera Caffè ☕", CodeFormat.QR_CODE))
        assertFalse(m.isLinear)
        assertEquals(m.width, m.height)
        assertTrue(m[0, 0]) // angolo del pattern di posizionamento: nessun margine
    }

    private fun requireCode(value: BarcodeMatrix?): BarcodeMatrix {
        assertNotNull("codice non generato", value)
        return value!!
    }
}
