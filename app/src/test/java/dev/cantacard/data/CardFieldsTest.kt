package dev.cantacard.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Campi aggiunti nella 0.2.0: retro, scadenza, etichette, archivio, ordine personalizzato. */
class CardFieldsTest {

    private val full = Card(
        id = "a",
        name = "Coop",
        number = "123",
        formatName = "EAN_13",
        color = 0xFFE2001A.toInt(),
        image = "a-1.webp",
        backImage = "a-back-2.webp",
        expiry = "2027-03-31",
        labels = listOf("Spesa", "Famiglia"),
        archived = true,
        rank = 2.5,
    )

    @Test
    fun roundTripKeepsNewFields() {
        assertEquals(full, CardJson.fromJsonOrNull(CardJson.toJson(full)))
    }

    @Test
    fun defaultsAreNotWritten() {
        val json = CardJson.toJson(full.copy(backImage = null, expiry = null, labels = emptyList(), archived = false, rank = null))
        listOf("backImage", "expiry", "labels", "archived", "rank").forEach { assertFalse(it, json.has(it)) }
    }

    @Test
    fun badValuesAreDroppedOneByOne() {
        val json = JSONObject(CardJson.toJson(full).toString())
            .put("expiry", "31/03/2027")
            .put("labels", org.json.JSONArray(listOf(" Spesa ", "spesa", "", "Viaggi  e  hotel")))
            .put("rank", "non un numero")
        val card = CardJson.fromJsonOrNull(json)!!
        assertNull(card.expiry)
        assertEquals(listOf("Spesa", "Viaggi e hotel"), card.labels)
        assertNull(card.rank)
        assertTrue(card.archived)
    }

    @Test
    fun backupPointsBackImageInsideZip() {
        val text = BackupFormat.encode(listOf(full), 1L) { true }
        val card = BackupFormat.decode(text).cards.single()
        assertEquals("images/a.webp", card.image)
        assertEquals("images/a-back.webp", card.backImage)
        assertTrue(BackupFormat.isImageEntry(card.backImage!!))
    }

    @Test
    fun backupWithoutBackFileDropsReference() {
        val text = BackupFormat.encode(listOf(full), 1L) { it == "images/a.webp" }
        val card = BackupFormat.decode(text).cards.single()
        assertEquals("images/a.webp", card.image)
        assertNull(card.backImage)
    }

    @Test
    fun syncIgnoresImageFileNames() {
        val local = CardSnapshot(listOf(full))
        val remote = CardSnapshot(listOf(full.copy(image = "images/a.webp", backImage = "images/a-back.webp")))
        assertTrue(SyncMerge.sameContent(local, remote))
        assertFalse(SyncMerge.sameContent(local, CardSnapshot(listOf(full.copy(backImage = null)))))
    }

    @Test
    fun daysToExpiry() {
        val today = LocalDate.of(2027, 3, 30)
        assertEquals(1L, CardQuery.daysToExpiry(full, today))
        assertEquals(-1L, CardQuery.daysToExpiry(full, LocalDate.of(2027, 4, 1)))
        assertNull(CardQuery.daysToExpiry(full.copy(expiry = null), today))
    }
}
