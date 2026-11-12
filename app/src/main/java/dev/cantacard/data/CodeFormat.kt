package dev.cantacard.data

/**
 * Formati di codice supportati.
 *
 * I nomi coincidono con `com.google.zxing.BarcodeFormat` e vengono salvati come stringa
 * (in `cards.json`, nei backup e nel catalogo): **non rinominarli e non usare l'ordinal**.
 * [label] è il nome tecnico del formato, uguale in tutte le lingue.
 */
enum class CodeFormat(val label: String) {
    EAN_13("EAN-13"),
    EAN_8("EAN-8"),
    UPC_A("UPC-A"),
    UPC_E("UPC-E"),
    CODE_128("Code 128"),
    CODE_39("Code 39"),
    CODE_93("Code 93"),
    CODABAR("Codabar"),
    ITF("ITF"),
    QR_CODE("QR Code"),
    DATA_MATRIX("Data Matrix"),
    AZTEC("Aztec"),
    PDF_417("PDF417");

    companion object {
        fun fromName(name: String?): CodeFormat? = entries.firstOrNull { it.name == name }
    }
}
