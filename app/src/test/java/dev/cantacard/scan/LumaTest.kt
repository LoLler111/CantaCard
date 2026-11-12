package dev.cantacard.scan

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class LumaTest {

    // Frame 3x2 con 2 byte di padding per riga (rowStride 5):
    //   1 2 3
    //   4 5 6
    private val plane = byteArrayOf(1, 2, 3, 0, 0, 4, 5, 6, 0, 0)

    private fun rotate(degrees: Int) = Luma.rotate(plane, width = 3, height = 2, rowStride = 5, degrees = degrees)

    @Test
    fun noRotationRemovesRowPadding() {
        val l = rotate(0)
        assertEquals(3, l.width)
        assertEquals(2, l.height)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6), l.data.copyOf(6))
    }

    @Test
    fun clockwise90() {
        // 4 1
        // 5 2
        // 6 3
        val l = rotate(90)
        assertEquals(2, l.width)
        assertEquals(3, l.height)
        assertArrayEquals(byteArrayOf(4, 1, 5, 2, 6, 3), l.data.copyOf(6))
    }

    @Test
    fun rotate180() {
        assertArrayEquals(byteArrayOf(6, 5, 4, 3, 2, 1), rotate(180).data.copyOf(6))
    }

    @Test
    fun clockwise270() {
        // 3 6
        // 2 5
        // 1 4
        val l = rotate(270)
        assertEquals(2, l.width)
        assertArrayEquals(byteArrayOf(3, 6, 2, 5, 1, 4), l.data.copyOf(6))
    }

    @Test
    fun rotationAbove360Wraps() {
        assertArrayEquals(rotate(90).data.copyOf(6), rotate(450).data.copyOf(6))
    }

    @Test
    fun lastRowMayBeShorterThanRowStride() {
        // Come in CameraX: il buffer finisce subito dopo l'ultimo pixel.
        val l = Luma.rotate(plane.copyOf(8), width = 3, height = 2, rowStride = 5, degrees = 90)
        assertArrayEquals(byteArrayOf(4, 1, 5, 2, 6, 3), l.data.copyOf(6))
    }
}
