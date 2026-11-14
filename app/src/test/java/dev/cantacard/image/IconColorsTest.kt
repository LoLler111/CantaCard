package dev.cantacard.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IconColorsTest {

    private fun pixels(vararg parts: Pair<Int, Int>): IntArray =
        parts.flatMap { (color, count) -> List(count) { color } }.toIntArray()

    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()
    private val transparent = 0x00FF0000
    private val red = 0xFFE2001A.toInt()
    private val blue = 0xFF005CA9.toInt()

    @Test
    fun redLogoOnWhiteGivesRed() {
        assertEquals(red, IconColors.dominant(pixels(white to 700, red to 300)))
    }

    @Test
    fun transparentAndBlackAreIgnored() {
        assertEquals(blue, IconColors.dominant(pixels(transparent to 500, black to 300, blue to 200)))
    }

    @Test
    fun largestColorWins() {
        assertEquals(blue, IconColors.dominant(pixels(white to 300, red to 100, blue to 600)))
    }

    @Test
    fun catalogColorWinsWhenItIsPartOfTheLogo() {
        val yellow = 0xFFFFF000.toInt()
        val lidl = pixels(yellow to 600, blue to 350, red to 50)
        assertEquals(yellow, IconColors.dominant(lidl))
        assertEquals(blue, IconColors.dominant(lidl, preferred = blue))
        // Un colore del catalogo che nel logo non c'è (o quasi) non vince.
        assertEquals(yellow, IconColors.dominant(lidl, preferred = 0xFF2E7D32.toInt()))
        assertEquals(yellow, IconColors.dominant(pixels(yellow to 950, blue to 50), preferred = blue))
        // Un colore del catalogo grigio non conta.
        assertEquals(yellow, IconColors.dominant(lidl, preferred = 0xFF37474F.toInt()))
    }

    @Test
    fun blackAndWhiteLogoHasNoColor() {
        assertNull(IconColors.dominant(pixels(white to 500, black to 400, 0xFF808080.toInt() to 100)))
        assertNull(IconColors.dominant(pixels(transparent to 10)))
        assertNull(IconColors.dominant(IntArray(0)))
    }

    @Test
    fun aFewColoredPixelsAreNotEnough() {
        assertNull(IconColors.dominant(pixels(white to 990, red to 10)))
    }

    @Test
    fun paleAntialiasingDoesNotWashOutTheColor() {
        // Bordo sfumato tra rosso e bianco: stesso rosso, ma pochissimo saturo.
        val pale = 0xFFF2A0A8.toInt()
        val result = IconColors.dominant(pixels(white to 500, red to 300, pale to 200))!!
        val r = result shr 16 and 0xFF
        val g = result shr 8 and 0xFF
        assert(r > 0xD0 && g < 0x40) { "colore troppo chiaro: %08X".format(result) }
    }
}
