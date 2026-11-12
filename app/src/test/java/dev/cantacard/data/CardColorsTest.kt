package dev.cantacard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardColorsTest {

    @Test
    fun hexRoundTrip() {
        assertEquals("#00695C", CardColors.toHex(0xFF00695C.toInt()))
        assertEquals(0xFF00695C.toInt(), CardColors.parse("#00695C"))
        assertEquals("#8000695C", CardColors.toHex(0x8000695C.toInt()))
        assertEquals(0x8000695C.toInt(), CardColors.parse("8000695c"))
    }

    @Test
    fun invalidColorsAreRejected() {
        assertNull(CardColors.parse(null))
        assertNull(CardColors.parse(""))
        assertNull(CardColors.parse("#12345"))
        assertNull(CardColors.parse("#GGGGGG"))
    }

    @Test
    fun fallbackColorIsStable() {
        assertEquals(CardColors.forName("Esselunga"), CardColors.forName("  esselunga "))
    }
}
