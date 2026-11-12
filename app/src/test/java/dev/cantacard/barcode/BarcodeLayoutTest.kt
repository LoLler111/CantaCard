package dev.cantacard.barcode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeLayoutTest {

    @Test
    fun linearCodeUsesIntegerScaleAndGenerousQuietZone() {
        // EAN-13 (95 moduli) su uno schermo largo 1048 px.
        val l = BarcodeLayout.compute(95, 1, linear = true, maxWidth = 1048f, maxHeight = 2000f)
        assertEquals(BarcodeLayout.LINEAR_QUIET_ZONE, l.quietZone)
        assertEquals(8f, l.scale) // 1048 / (95 + 24) = 8,8 -> 8
        assertEquals(760f, l.codeWidth)
        assertTrue(l.totalWidth <= 1048f)
    }

    @Test
    fun matrixCodeIsLimitedByTheShorterSide() {
        // QR 29x29 in orizzontale: limita l'altezza.
        val l = BarcodeLayout.compute(29, 29, linear = false, maxWidth = 2000f, maxHeight = 600f)
        assertEquals(BarcodeLayout.MATRIX_QUIET_ZONE, l.quietZone)
        assertEquals(16f, l.scale) // 600 / (29 + 8) = 16,2 -> 16
        assertEquals(l.codeWidth, l.codeHeight)
        assertTrue(l.totalHeight <= 600f)
    }

    @Test
    fun linearCodeFitsInAShortSpace() {
        val l = BarcodeLayout.compute(95, 1, linear = true, maxWidth = 2000f, maxHeight = 200f)
        assertTrue(l.totalHeight <= 200f)
        assertTrue(l.totalWidth <= 2000f)
    }

    @Test
    fun veryLongCodeFallsBackToFractionalScale() {
        val l = BarcodeLayout.compute(600, 1, linear = true, maxWidth = 1000f, maxHeight = 2000f)
        assertTrue(l.scale in 1f..2f)
        assertTrue(l.totalWidth <= 1000f + 0.01f)
    }
}
