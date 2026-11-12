package dev.cantacard.barcode

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import dev.cantacard.data.CodeFormat
import java.nio.charset.StandardCharsets

/**
 * Codice generato alla risoluzione nativa: un elemento della matrice = un modulo, senza margini
 * (la quiet zone la aggiunge [BarcodeLayout]). Per i codici lineari [height] è 1.
 */
class BarcodeMatrix(private val bits: BitMatrix) {
    val width: Int get() = bits.width
    val height: Int get() = bits.height
    val isLinear: Boolean get() = bits.height == 1

    operator fun get(x: Int, y: Int): Boolean = bits[x, y]
}

/** Generazione dei codici con ZXing (JVM puro, testabile senza Android). */
object BarcodeEncoder {

    /** `null` se il contenuto non è valido per il formato (es. cifra di controllo EAN errata). */
    fun encode(content: String, format: CodeFormat): BarcodeMatrix? {
        if (content.isEmpty()) return null
        val hints = HashMap<EncodeHintType, Any>().apply {
            put(EncodeHintType.MARGIN, 0)
            if (format == CodeFormat.QR_CODE) put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
            // UTF-8 solo se serve: dichiararlo aggiunge un segmento ECI che alcuni lettori vecchi
            // in cassa non gestiscono.
            if (!StandardCharsets.ISO_8859_1.newEncoder().canEncode(content)) {
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
            }
        }
        return try {
            // Dimensioni 0: ZXing restituisce la matrice alla dimensione minima (1 px per modulo),
            // con altezza 1 per i codici lineari.
            val bits = MultiFormatWriter().encode(content, BarcodeFormat.valueOf(format.name), 0, 0, hints)
            if (bits.width > 0 && bits.height > 0) BarcodeMatrix(bits) else null
        } catch (e: Exception) {
            // WriterException / IllegalArgumentException: contenuto non codificabile nel formato.
            null
        }
    }
}
