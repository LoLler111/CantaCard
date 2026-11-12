package dev.cantacard.scan

/** Piano di luminanza (Y) di un frame, compatto (senza padding di riga). */
class Luma(val data: ByteArray, val width: Int, val height: Int) {

    companion object {
        /**
         * Copia il piano Y togliendo il padding di riga e lo ruota in senso orario di
         * [degrees] (0, 90, 180, 270), così il frame è dritto come lo vede l'utente.
         * [out] viene riusato se abbastanza grande, per non allocare a ogni frame.
         */
        fun rotate(
            plane: ByteArray,
            width: Int,
            height: Int,
            rowStride: Int,
            degrees: Int,
            out: ByteArray? = null,
        ): Luma {
            val size = width * height
            val dst = if (out != null && out.size >= size) out else ByteArray(size)
            when (Math.floorMod(degrees, 360)) {
                0 -> {
                    for (y in 0 until height) System.arraycopy(plane, y * rowStride, dst, y * width, width)
                    return Luma(dst, width, height)
                }
                90 -> {
                    for (y in 0 until height) {
                        val row = y * rowStride
                        for (x in 0 until width) dst[x * height + (height - 1 - y)] = plane[row + x]
                    }
                    return Luma(dst, height, width)
                }
                180 -> {
                    for (y in 0 until height) {
                        val row = y * rowStride
                        val outRow = (height - 1 - y) * width
                        for (x in 0 until width) dst[outRow + (width - 1 - x)] = plane[row + x]
                    }
                    return Luma(dst, width, height)
                }
                270 -> {
                    for (y in 0 until height) {
                        val row = y * rowStride
                        for (x in 0 until width) dst[(width - 1 - x) * height + y] = plane[row + x]
                    }
                    return Luma(dst, height, width)
                }
                else -> throw IllegalArgumentException("Rotazione non supportata: $degrees")
            }
        }
    }
}
