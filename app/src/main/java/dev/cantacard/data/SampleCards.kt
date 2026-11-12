package dev.cantacard.data

/**
 * Tessere fittizie per provare la UI nelle build di debug (nessun marchio reale).
 * In release non è raggiungibile e R8 la rimuove.
 */
object SampleCards {

    fun create(now: Long = System.currentTimeMillis()): List<Card> = listOf(
        sample("Supermercato Esempio", "8001234567897", CodeFormat.EAN_13, now, favorite = true),
        sample("Profumeria Demo", "96385074", CodeFormat.EAN_8, now, lastUsedAgo = HOUR),
        sample("Elettronica Prova", "CARD-0042-XY", CodeFormat.CODE_128, now, lastUsedAgo = DAY),
        sample("Benzina Test", "ABC12345", CodeFormat.CODE_39, now),
        sample("Libreria Campione", "https://example.com/tessera/123", CodeFormat.QR_CODE, now),
        sample("Moda Modello", "4006381333931", CodeFormat.EAN_13, now, lastUsedAgo = 7 * DAY),
    )

    private fun sample(
        name: String,
        number: String,
        format: CodeFormat,
        now: Long,
        favorite: Boolean = false,
        lastUsedAgo: Long? = null,
    ) = Card(
        id = Card.newId(),
        name = name,
        number = number,
        formatName = format.name,
        color = CardColors.forName(name),
        favorite = favorite,
        lastUsed = lastUsedAgo?.let { now - it } ?: 0L,
        createdAt = now,
    )

    private const val HOUR = 3_600_000L
    private const val DAY = 24 * HOUR
}
