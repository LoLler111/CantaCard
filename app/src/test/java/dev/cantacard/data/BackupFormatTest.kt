package dev.cantacard.data

import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFormatTest {

    private fun card(id: String, number: String = "123$id", format: String = "EAN_13", image: String? = null) =
        Card(id = id, name = "Tessera $id", number = number, formatName = format, color = 0xFF112233.toInt(), image = image)

    @Test
    fun roundTripKeepsCardsAndPointsImagesInsideZip() {
        val cards = listOf(card("a", image = "a-1700000000000.webp"), card("b", image = "b-1.webp"), card("c"))
        // "b" ha un nome immagine ma il file non c'è: niente riferimento nel backup.
        val text = BackupFormat.encode(cards, exportedAt = 42L) { it.id == "a" }
        val content = BackupFormat.decode(text)
        assertEquals(BackupFormat.FORMAT_VERSION, content.formatVersion)
        assertEquals(42L, content.exportedAt)
        assertEquals(listOf("a", "b", "c"), content.cards.map { it.id })
        assertEquals("images/a.webp", content.cards[0].image)
        assertNull(content.cards[1].image)
        assertNull(content.cards[2].image)
        assertEquals(cards[2], content.cards[2])
        assertFalse(content.isNewer)
    }

    @Test
    fun futureVersionIsReadAndFlagged() {
        val text = """
            {"formatVersion": 7, "exportedAt": 1, "nuovoCampo": {"x": 1}, "cards": [
              {"id": "a", "name": "A", "number": "1", "format": "QR_CODE", "altro": true},
              {"id": "b", "name": "senza numero", "format": "QR_CODE"},
              {"id": "c", "name": "C", "number": "3", "format": "FORMATO_FUTURO", "image": "../../cards.json"}
            ]}
        """.trimIndent()
        val content = BackupFormat.decode(text)
        assertTrue(content.isNewer)
        assertEquals(listOf("a", "c"), content.cards.map { it.id })
        assertEquals(1, content.skipped)
        // Formato sconosciuto conservato, immagine con percorso non valido scartata.
        assertEquals("FORMATO_FUTURO", content.cards[1].formatName)
        assertNull(content.cards[1].image)
    }

    @Test(expected = JSONException::class)
    fun otherJsonIsNotABackup() {
        BackupFormat.decode(JSONObject().put("version", 1).toString())
    }

    @Test
    fun imageEntryNamesAreStrict() {
        assertTrue(BackupFormat.isImageEntry("images/0f8b-12.webp"))
        assertFalse(BackupFormat.isImageEntry("images/../cards.webp"))
        assertFalse(BackupFormat.isImageEntry("images/a/b.webp"))
        assertFalse(BackupFormat.isImageEntry("cantacard.json"))
    }

    @Test
    fun addSkipsSameIdAndSameCode() {
        val current = listOf(card("a", number = "111"), card("b", number = "222"))
        val incoming = listOf(
            card("a", number = "999"), // stesso id
            card("x", number = " 222 "), // stesso codice
            card("y", number = "222", format = "QR_CODE"), // stesso numero, altro formato
            card("z", number = "333"),
            card("w", number = "333"), // doppione dentro il backup
        )
        val result = BackupMerge.merge(current, incoming, RestoreMode.ADD)
        assertEquals(listOf("a", "b", "y", "z"), result.cards.map { it.id })
        // Stesso id, nessuna data di modifica (dati vecchi): vince il telefono.
        assertEquals("111", result.cards[0].number)
        assertEquals(2, result.added)
        assertEquals(0, result.updated)
        assertEquals(3, result.duplicates)
    }

    @Test
    fun sameIdNewestVersionWins() {
        val current = listOf(
            card("a", number = "telefono").copy(updatedAt = 100, lastUsed = 900, useCount = 7),
            card("b", number = "telefono").copy(updatedAt = 500),
            card("c", number = "telefono").copy(updatedAt = 300),
        )
        val incoming = listOf(
            card("a", number = "backup").copy(updatedAt = 200, lastUsed = 50, useCount = 2),
            card("b", number = "backup").copy(updatedAt = 400),
            card("c", number = "backup").copy(updatedAt = 300),
        )
        val result = BackupMerge.merge(current, incoming, RestoreMode.ADD)
        // Ordine conservato; "a" più recente nel backup, "b" più recente sul telefono, "c" pari.
        assertEquals(listOf("backup", "telefono", "telefono"), result.cards.map { it.number })
        assertEquals(1, result.updated)
        assertEquals(2, result.duplicates)
        assertEquals(0, result.added)
        // Statistiche d'uso unite: non si perde l'uso fatto sul telefono.
        assertEquals(900L, result.cards[0].lastUsed)
        assertEquals(7, result.cards[0].useCount)
    }

    @Test
    fun updatedAtSurvivesJson() {
        val original = card("a").copy(updatedAt = 1_794_000_000_000L)
        val back = BackupFormat.decode(BackupFormat.encode(listOf(original), 1L) { false }).cards.single()
        assertEquals(original.updatedAt, back.updatedAt)
    }

    @Test
    fun replaceTakesBackupAsIs() {
        val result = BackupMerge.merge(listOf(card("a")), listOf(card("b"), card("c")), RestoreMode.REPLACE)
        assertEquals(listOf("b", "c"), result.cards.map { it.id })
        assertEquals(2, result.added)
        assertEquals(0, result.duplicates)
    }
}
