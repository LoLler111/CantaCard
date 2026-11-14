package dev.cantacard.image

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Colore principale del logo di un negozio, per dare alla tessera il colore dell'icona.
 *
 * Si ignorano i pixel trasparenti, bianchi, neri e grigi (sfondi e scritte); tra quelli colorati
 * vince la tinta che copre più superficie, pesata per la saturazione (un rosso pieno conta più del
 * suo bordo sfumato). Il risultato è la media di quella tinta. `null` se il logo non ha abbastanza
 * colore (es. tutto bianco e nero): allora resta il colore del catalogo.
 *
 * Con un [preferred] (il colore del catalogo) che nel logo occupa una parte importante, vince
 * quello: in un logo a due colori (Lidl blu e giallo) resta il colore del marchio.
 */
object IconColors {

    /** Colore dell'immagine della tessera se è un'icona; `null` per una foto o senza immagine. */
    fun of(image: CardImage?, preferred: Int? = null): Int? =
        image?.takeUnless { it.isPhoto }?.let { dominant(it.bitmap.asAndroidBitmap(), preferred) }

    /** Colore principale di [bitmap] (un'icona, non una foto), o `null`. */
    fun dominant(bitmap: Bitmap, preferred: Int? = null): Int? {
        // Basta un campione: le icone sono già ≤ 256 px, una griglia di 64×64 punti è sufficiente.
        val step = max(1, max(bitmap.width, bitmap.height) / SAMPLE_SIDE)
        val w = bitmap.width
        val h = bitmap.height
        val row = IntArray(w)
        val pixels = ArrayList<Int>((w / step + 1) * (h / step + 1))
        try {
            for (y in 0 until h step step) {
                bitmap.getPixels(row, 0, w, 0, y, w, 1)
                for (x in 0 until w step step) pixels += row[x]
            }
        } catch (e: IllegalStateException) {
            return null // bitmap hardware o già riciclata
        }
        return dominant(pixels.toIntArray(), preferred)
    }

    /** Come [dominant] su pixel ARGB già letti (testabile senza Android). */
    fun dominant(pixels: IntArray, preferred: Int? = null): Int? {
        val weight = DoubleArray(HUE_BINS)
        var opaque = 0
        var colored = 0
        for (p in pixels) {
            if (p ushr 24 < MIN_ALPHA) continue
            opaque++
            val hsv = hsv(p) ?: continue
            colored++
            weight[hsv.bin] += hsv.saturation.toDouble()
        }
        if (opaque == 0 || colored < opaque * MIN_COLORED_FRACTION) return null
        // Tinta insieme alle due vicine: un colore a cavallo di due fasce non si divide.
        fun around(bin: Int): Double =
            weight[bin] + 0.5 * (weight[(bin + 1) % HUE_BINS] + weight[(bin + HUE_BINS - 1) % HUE_BINS])
        // Solo un colore del catalogo deciso: il grigio neutro (#37474F) non è un colore del marchio.
        preferred?.let(::hsv)?.takeIf { it.saturation >= PREFERRED_MIN_SATURATION }?.let { pref ->
            if (around(pref.bin) >= weight.sum() * PREFERRED_FRACTION) return preferred
        }
        var best = 0
        var bestWeight = -1.0
        for (bin in 0 until HUE_BINS) {
            val w = around(bin)
            if (w > bestWeight) {
                bestWeight = w
                best = bin
            }
        }
        var r = 0.0
        var g = 0.0
        var b = 0.0
        var total = 0.0
        for (p in pixels) {
            if (p ushr 24 < MIN_ALPHA) continue
            val hsv = hsv(p) ?: continue
            val distance = min(Math.floorMod(hsv.bin - best, HUE_BINS), Math.floorMod(best - hsv.bin, HUE_BINS))
            if (distance > 1) continue
            // Peso al quadrato della saturazione: i bordi sfumati verso il bianco non schiariscono il colore.
            val wgt = (hsv.saturation * hsv.saturation).toDouble()
            r += (p shr 16 and 0xFF) * wgt
            g += (p shr 8 and 0xFF) * wgt
            b += (p and 0xFF) * wgt
            total += wgt
        }
        if (total <= 0.0) return null
        return (0xFF shl 24) or
            ((r / total).roundToInt() shl 16) or
            ((g / total).roundToInt() shl 8) or
            (b / total).roundToInt()
    }

    private class Hsv(val bin: Int, val saturation: Float)

    /** Tinta e saturazione di un pixel colorato; `null` se è bianco, nero o grigio. */
    private fun hsv(p: Int): Hsv? {
        val r = p shr 16 and 0xFF
        val g = p shr 8 and 0xFF
        val b = p and 0xFF
        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        if (maxC < MIN_VALUE) return null
        val delta = maxC - minC
        val saturation = delta.toFloat() / maxC
        if (saturation < MIN_SATURATION) return null
        val hue = when (maxC) {
            r -> 60f * (((g - b).toFloat() / delta).mod(6f))
            g -> 60f * ((b - r).toFloat() / delta + 2f)
            else -> 60f * ((r - g).toFloat() / delta + 4f)
        }
        return Hsv(((hue / 360f) * HUE_BINS).toInt().coerceIn(0, HUE_BINS - 1), saturation)
    }

    private const val SAMPLE_SIDE = 64
    private const val HUE_BINS = 24 // fasce di 15°
    private const val MIN_ALPHA = 128
    private const val MIN_VALUE = 48 // più scuro di così è nero
    private const val MIN_SATURATION = 0.28f // meno saturo di così è bianco o grigio
    private const val MIN_COLORED_FRACTION = 0.04 // almeno il 4% del logo deve essere colorato
    private const val PREFERRED_FRACTION = 0.2 // il colore del catalogo vince se è almeno il 20% del colore
    private const val PREFERRED_MIN_SATURATION = 0.5f
}
