package dev.cantacard.data

/**
 * Formati di codice supportati.
 *
 * I nomi coincidono con `com.google.zxing.BarcodeFormat` e vengono salvati come stringa
 * (in `cards.json`, nei backup e nel catalogo): **non rinominarli e non usare l'ordinal**.
 */
enum class CodeFormat {
    EAN_13,
    EAN_8,
    UPC_A,
    UPC_E,
    CODE_128,
    CODE_39,
    CODE_93,
    CODABAR,
    ITF,
    QR_CODE,
    DATA_MATRIX,
    AZTEC,
    PDF_417;

    companion object {
        fun fromName(name: String?): CodeFormat? = entries.firstOrNull { it.name == name }
    }
}
