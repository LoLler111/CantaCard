package dev.cantacard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Le versioni nuove devono leggere i dati di **qualsiasi** versione precedente, perché gli
 * aggiornamenti si possono saltare. I file in `src/test/resources/compat/` sono scritti esattamente
 * come li salvano le versioni pubblicate: quando il formato cambia, aggiungere i file della nuova
 * versione e non toglierne mai.
 *
 * - `v0.1.0-*`: formato delle versioni 0.1.0–0.1.6 (`cards.json`, `cantacard.json` del backup e del
 *   file di sincronizzazione);
 * - `v0.2.0-*`: dalla 0.2.0, con retro, scadenza, etichette, archivio e ordine personalizzato;
 * - `prerelease-cards.json`: prima della 0.1.0, senza `updatedAt` né `deleted` (mai pubblicato,
 *   tenuto perché più povero di tutti).
 */
class DataCompatibilityTest {

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/compat/$name")) { "Manca il file $name" }.readText()

    private val esselunga = Card(
        id = "6f1c2a9e-1b2c-4d3e-8f90-0a1b2c3d4e5f",
        name = "Esselunga Fidaty",
        number = "0412345678901",
        formatName = "EAN_13",
        color = 0xFF0A5BA8.toInt(),
        storeId = "esselunga",
        image = "6f1c2a9e-1b2c-4d3e-8f90-0a1b2c3d4e5f-1794500000000.webp",
        favorite = true,
        lastUsed = 1794510000000,
        useCount = 12,
        createdAt = 1794400000000,
        note = "Carta di famiglia",
        updatedAt = 1794450000000,
    )

    private val palestra = Card(
        id = "0b7e5d1c-2a3b-4c5d-9e8f-112233445566",
        name = "Palestra",
        number = "https://example.org/tessera?id=42&k=x y",
        formatName = "QR_CODE",
        color = 0xFF37474F.toInt(),
        createdAt = 1794400100000,
    )

    private val biblioteca = Card(
        id = "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d",
        name = "Biblioteca",
        number = "A123-B",
        formatName = "CODE_39",
        color = 0xFFFF7A59.toInt(),
        lastUsed = 1794300000000,
        useCount = 3,
        createdAt = 1794200000000,
        updatedAt = 1794200000000,
    )

    @Test
    fun readsCardsFileOf010() {
        val decoded = CardJson.decode(fixture("v0.1.0-cards.json"))
        assertEquals(0, decoded.skipped)
        assertEquals(listOf(esselunga, palestra, biblioteca), decoded.cards)
        assertEquals(mapOf("deadbeef-0000-4000-8000-000000000001" to 1794460000000), decoded.deleted)
    }

    @Test
    fun cardsFileOf010SurvivesRewrite() {
        val first = CardJson.decode(fixture("v0.1.0-cards.json"))
        val again = CardJson.decode(CardJson.encode(first.cards, first.deleted))
        assertEquals(first.cards, again.cards)
        assertEquals(first.deleted, again.deleted)
    }

    @Test
    fun readsBackupOf010() {
        val content = BackupFormat.decode(fixture("v0.1.0-backup.json"))
        assertFalse(content.isNewer)
        assertEquals(1794520000000, content.exportedAt)
        assertEquals(0, content.skipped)
        assertEquals(
            listOf(esselunga.copy(image = "images/${esselunga.id}.webp"), palestra),
            content.cards,
        )
    }

    @Test
    fun readsSyncFileOf010() {
        val content = BackupFormat.decode(fixture("v0.1.0-sync.json"))
        assertEquals(listOf(biblioteca), content.cards)
        assertEquals(
            mapOf(
                "deadbeef-0000-4000-8000-000000000001" to 1794460000000,
                "deadbeef-0000-4000-8000-000000000002" to 1794470000000,
            ),
            content.deleted,
        )
    }

    @Test
    fun cardsOf010HaveDefaultsForNewFields() {
        CardJson.decode(fixture("v0.1.0-cards.json")).cards.forEach { card ->
            assertNull(card.backImage)
            assertNull(card.expiry)
            assertEquals(emptyList<String>(), card.labels)
            assertFalse(card.archived)
            assertNull(card.rank)
        }
    }

    @Test
    fun readsCardsFileOf020() {
        val decoded = CardJson.decode(fixture("v0.2.0-cards.json"))
        assertEquals(0, decoded.skipped)
        assertEquals(
            listOf(
                esselunga.copy(
                    backImage = "6f1c2a9e-1b2c-4d3e-8f90-0a1b2c3d4e5f-back-1794500000001.webp",
                    expiry = "2027-03-31",
                    labels = listOf("Spesa", "Famiglia"),
                    rank = 1.5,
                ),
                palestra.copy(archived = true),
            ),
            decoded.cards,
        )
        val again = CardJson.decode(CardJson.encode(decoded.cards))
        assertEquals(decoded.cards, again.cards)
    }

    @Test
    fun readsBackupOf020() {
        val card = BackupFormat.decode(fixture("v0.2.0-backup.json")).cards.single()
        assertEquals("images/${esselunga.id}.webp", card.image)
        assertEquals("images/${esselunga.id}-back.webp", card.backImage)
        assertEquals("2027-03-31", card.expiry)
        assertEquals(listOf("Spesa", "Famiglia"), card.labels)
        assertEquals(1.5, card.rank!!, 0.0)
    }

    @Test
    fun readsPrereleaseCardsWithDefaults() {
        val decoded = CardJson.decode(fixture("prerelease-cards.json"))
        val card = decoded.cards.single()
        assertEquals("Coop", card.name)
        assertEquals(0L, card.updatedAt)
        assertNull(card.image)
        assertNull(card.note)
        assertEquals(emptyMap<String, Long>(), decoded.deleted)
    }
}
