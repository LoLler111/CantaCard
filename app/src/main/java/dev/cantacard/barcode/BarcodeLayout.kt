package dev.cantacard.barcode

import kotlin.math.floor
import kotlin.math.min

/**
 * Dimensioni a schermo di un codice, in pixel.
 *
 * - Quiet zone abbondante: [LINEAR_QUIET_ZONE] moduli per lato nei codici lineari (EAN ne chiede
 *   11/7, Code 128 10), [MATRIX_QUIET_ZONE] nei codici 2D (QR ne chiede 4).
 * - Scala intera quando possibile, così tutte le barre hanno la stessa larghezza in pixel e il
 *   codice resta nitido per lo scanner.
 */
data class BarcodeLayout(
    /** Pixel per modulo. */
    val scale: Float,
    /** Moduli di quiet zone su ogni lato. */
    val quietZone: Int,
    /** Larghezza del solo codice, senza quiet zone. */
    val codeWidth: Float,
    /** Altezza del solo codice, senza quiet zone. */
    val codeHeight: Float,
) {
    val totalWidth: Float get() = codeWidth + 2 * quietZone * scale
    val totalHeight: Float get() = codeHeight + 2 * quietZone * scale

    companion object {
        const val LINEAR_QUIET_ZONE = 12
        const val MATRIX_QUIET_ZONE = 4

        /** Altezza delle barre rispetto alla larghezza del codice lineare. */
        const val LINEAR_HEIGHT_RATIO = 0.4f

        /** Rapporto larghezza/altezza del codice con la sua quiet zone. */
        fun aspectRatio(matrix: BarcodeMatrix): Float {
            val quiet = if (matrix.isLinear) LINEAR_QUIET_ZONE else MATRIX_QUIET_ZONE
            val height = if (matrix.isLinear) matrix.width * LINEAR_HEIGHT_RATIO else matrix.height.toFloat()
            return (matrix.width + 2f * quiet) / (height + 2f * quiet)
        }

        fun compute(matrix: BarcodeMatrix, maxWidth: Float, maxHeight: Float): BarcodeLayout =
            compute(matrix.width, matrix.height, matrix.isLinear, maxWidth, maxHeight)

        fun compute(
            modulesWide: Int,
            modulesHigh: Int,
            linear: Boolean,
            maxWidth: Float,
            maxHeight: Float,
        ): BarcodeLayout {
            val quiet = if (linear) LINEAR_QUIET_ZONE else MATRIX_QUIET_ZONE
            val byWidth = maxWidth / (modulesWide + 2 * quiet)
            val raw = if (linear) {
                // Le barre si allungano in verticale: conta solo che stiano in altezza.
                min(byWidth, maxHeight / (modulesWide * LINEAR_HEIGHT_RATIO + 2 * quiet))
            } else {
                min(byWidth, maxHeight / (modulesHigh + 2 * quiet))
            }
            val scale = if (raw >= 2f) floor(raw) else raw.coerceAtLeast(0f)
            val width = modulesWide * scale
            val height = if (linear) floor(width * LINEAR_HEIGHT_RATIO) else modulesHigh * scale
            return BarcodeLayout(scale, quiet, width, height)
        }
    }
}
