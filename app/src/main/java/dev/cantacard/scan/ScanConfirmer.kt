package dev.cantacard.scan

import dev.cantacard.barcode.ScannedCode
import dev.cantacard.data.CodeFormat

/**
 * Evita letture sbagliate dalla fotocamera: i formati senza una cifra di controllo robusta
 * (Code 39, Code 93, Codabar, ITF) devono dare lo stesso risultato per due frame di fila.
 */
class ScanConfirmer {

    private var last: ScannedCode? = null
    private var count = 0

    /** `true` quando il codice è confermato. */
    fun offer(code: ScannedCode): Boolean {
        if (code == last) count++ else {
            last = code
            count = 1
        }
        return count >= required(code.format)
    }

    private fun required(format: CodeFormat): Int = if (format in weakFormats) 2 else 1

    private companion object {
        val weakFormats = setOf(CodeFormat.CODE_39, CodeFormat.CODE_93, CodeFormat.CODABAR, CodeFormat.ITF)
    }
}
