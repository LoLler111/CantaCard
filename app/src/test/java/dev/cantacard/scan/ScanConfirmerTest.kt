package dev.cantacard.scan

import dev.cantacard.barcode.ScannedCode
import dev.cantacard.data.CodeFormat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanConfirmerTest {

    @Test
    fun formatsWithStrongChecksumAreAcceptedAtOnce() {
        assertTrue(ScanConfirmer().offer(ScannedCode("8001234567897", CodeFormat.EAN_13)))
        assertTrue(ScanConfirmer().offer(ScannedCode("x", CodeFormat.QR_CODE)))
        assertTrue(ScanConfirmer().offer(ScannedCode("ABC", CodeFormat.CODE_128)))
    }

    @Test
    fun weakFormatsNeedTwoEqualReadsInARow() {
        val c = ScanConfirmer()
        assertFalse(c.offer(ScannedCode("ABC123", CodeFormat.CODE_39)))
        assertFalse(c.offer(ScannedCode("ABC124", CodeFormat.CODE_39))) // lettura diversa: si riparte
        assertTrue(c.offer(ScannedCode("ABC124", CodeFormat.CODE_39)))
    }
}
