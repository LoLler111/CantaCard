package dev.cantacard.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CardInputTest {

    @Test
    fun numericFormatsDropSpaces() {
        assertEquals("8001234567897", CardInput.normalizeNumber(" 8001 2345 6789 7 ", CodeFormat.EAN_13))
    }

    @Test
    fun textFormatsKeepInnerSpaces() {
        assertEquals("AB 12", CardInput.normalizeNumber("  AB 12 ", CodeFormat.CODE_128))
        assertEquals("AB 12", CardInput.normalizeNumber("  AB 12 ", null))
    }

    @Test
    fun guessesEanFromDigitCount() {
        assertEquals(CodeFormat.EAN_13, CardInput.guessFormat("8001 2345 6789 7"))
        assertEquals(CodeFormat.EAN_8, CardInput.guessFormat("96385074"))
        assertEquals(CodeFormat.CODE_128, CardInput.guessFormat("123456"))
        assertEquals(CodeFormat.CODE_128, CardInput.guessFormat("ABC12345"))
        assertEquals(CodeFormat.CODE_128, CardInput.guessFormat(""))
    }
}
