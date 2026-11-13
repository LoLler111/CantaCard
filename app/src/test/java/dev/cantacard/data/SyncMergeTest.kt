package dev.cantacard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeTest {

    private fun card(id: String, updatedAt: Long = 100, number: String = "N$id", createdAt: Long = 1) =
        Card(
            id = id, name = "Tessera $id", number = number, formatName = "EAN_13", color = 0,
            updatedAt = updatedAt, createdAt = createdAt,
        )

    private fun merge(local: CardSnapshot, remote: CardSnapshot) = SyncMerge.merge(local, remote, now = 10_000)

    @Test
    fun newCardsArriveFromBothSides() {
        val result = merge(CardSnapshot(listOf(card("a"))), CardSnapshot(listOf(card("b"))))
        assertEquals(setOf("a", "b"), result.snapshot.cards.map { it.id }.toSet())
        assertEquals(setOf("b"), result.fromRemote)
    }

    @Test
    fun newestEditWinsAndStatsAreMerged() {
        val local = CardSnapshot(listOf(card("a", updatedAt = 100).copy(name = "telefono", useCount = 9), card("b", updatedAt = 300)))
        val remote = CardSnapshot(listOf(card("a", updatedAt = 200).copy(name = "file", useCount = 2), card("b", updatedAt = 300).copy(name = "file")))
        val result = merge(local, remote)
        val byId = result.snapshot.cards.associateBy { it.id }
        assertEquals("file", byId.getValue("a").name)
        assertEquals(9, byId.getValue("a").useCount)
        // Pari: vince il telefono.
        assertEquals("Tessera b", byId.getValue("b").name)
        assertEquals(setOf("a"), result.fromRemote)
    }

    @Test
    fun deletionOnOtherPhoneRemovesCardHere() {
        val local = CardSnapshot(listOf(card("a", updatedAt = 100), card("b")))
        val remote = CardSnapshot(listOf(card("b")), deleted = mapOf("a" to 500L))
        val result = merge(local, remote)
        assertEquals(listOf("b"), result.snapshot.cards.map { it.id })
        assertEquals(mapOf("a" to 500L), result.snapshot.deleted)
    }

    @Test
    fun deletedHereIsNotBroughtBackByTheFile() {
        val local = CardSnapshot(emptyList(), deleted = mapOf("a" to 500L))
        val remote = CardSnapshot(listOf(card("a", updatedAt = 100)))
        val result = merge(local, remote)
        assertTrue(result.snapshot.cards.isEmpty())
        assertTrue(result.fromRemote.isEmpty())
    }

    @Test
    fun editAfterDeletionKeepsTheCard() {
        val local = CardSnapshot(emptyList(), deleted = mapOf("a" to 500L))
        val remote = CardSnapshot(listOf(card("a", updatedAt = 900)))
        val result = merge(local, remote)
        assertEquals(listOf("a"), result.snapshot.cards.map { it.id })
        assertFalse("a" in result.snapshot.deleted)
    }

    @Test
    fun sameCardAddedOnBothPhonesKeepsTheOlder() {
        val local = CardSnapshot(listOf(card("x", number = "123", createdAt = 50)))
        val remote = CardSnapshot(listOf(card("y", number = "123", createdAt = 20)))
        val result = merge(local, remote)
        assertEquals(listOf("y"), result.snapshot.cards.map { it.id })
        assertEquals(10_000L, result.snapshot.deleted["x"])
        // L'altro telefono, alla sua sincronizzazione, arriva allo stesso risultato.
        val other = merge(CardSnapshot(listOf(card("y", number = "123", createdAt = 20))), result.snapshot)
        assertEquals(listOf("y"), other.snapshot.cards.map { it.id })
    }

    @Test
    fun intentionalDuplicatesAlreadySyncedStay() {
        val both = listOf(card("x", number = "123"), card("y", number = "123"))
        val result = merge(CardSnapshot(both), CardSnapshot(both))
        assertEquals(2, result.snapshot.cards.size)
    }

    @Test
    fun sameContentIgnoresOrderAndImageNames() {
        val a = CardSnapshot(listOf(card("a").copy(image = "a-123.webp"), card("b")))
        val b = CardSnapshot(listOf(card("b"), card("a").copy(image = "images/a.webp")))
        assertTrue(SyncMerge.sameContent(a, b))
        assertFalse(SyncMerge.sameContent(a, b.copy(deleted = mapOf("z" to 1L))))
        assertFalse(SyncMerge.sameContent(a, CardSnapshot(listOf(card("a"), card("b")))))
    }

    @Test
    fun convergesAfterTwoSyncs() {
        val phoneA = CardSnapshot(listOf(card("a", updatedAt = 300), card("c")), deleted = mapOf("d" to 400L))
        val phoneB = CardSnapshot(listOf(card("a", updatedAt = 200), card("b"), card("d", updatedAt = 100)))
        // A scrive il file, B lo legge e lo riscrive, A lo rilegge.
        val file1 = merge(phoneA, CardSnapshot(emptyList())).snapshot
        val b = merge(phoneB, file1).snapshot
        val a = merge(file1, b).snapshot
        assertTrue(SyncMerge.sameContent(a, b))
        assertEquals(setOf("a", "b", "c"), a.cards.map { it.id }.toSet())
    }
}
