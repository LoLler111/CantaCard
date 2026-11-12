package dev.cantacard.data

/** Regole per il numero inserito a mano (funzioni pure, testate in JVM). */
object CardInput {

    /** Formati solo numerici: gli spazi digitati per leggibilità vanno tolti. */
    private val numericFormats = setOf(
        CodeFormat.EAN_13, CodeFormat.EAN_8, CodeFormat.UPC_A, CodeFormat.UPC_E, CodeFormat.ITF,
    )

    fun normalizeNumber(raw: String, format: CodeFormat?): String =
        if (format in numericFormats) raw.filterNot { it.isWhitespace() } else raw.trim()

    /**
     * Formato proposto per l'inserimento manuale finché l'utente non ne sceglie uno:
     * 13 o 8 cifre sono quasi sempre EAN, il resto Code 128 (codifica qualsiasi testo ASCII).
     */
    fun guessFormat(number: String): CodeFormat {
        val compact = number.filterNot { it.isWhitespace() }
        val digits = compact.isNotEmpty() && compact.all { it in '0'..'9' }
        return when {
            digits && compact.length == 13 -> CodeFormat.EAN_13
            digits && compact.length == 8 -> CodeFormat.EAN_8
            else -> CodeFormat.CODE_128
        }
    }
}
