package dev.cantacard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardQueryTest {

    private fun card(name: String, number: String = "0", favorite: Boolean = false, lastUsed: Long = 0) =
        Card(
            id = name,
            name = name,
            number = number,
            formatName = "EAN_13",
            color = 0,
            favorite = favorite,
            lastUsed = lastUsed,
        )

    @Test
    fun favoritesFirstThenMostRecentThenName() {
        val cards = listOf(
            card("zeta"),
            card("Alfa"),
            card("recente", lastUsed = 200),
            card("vecchia", lastUsed = 100),
            card("pref-vecchia", favorite = true, lastUsed = 1),
            card("pref-recente", favorite = true, lastUsed = 300),
        )
        assertEquals(
            listOf("pref-recente", "pref-vecchia", "recente", "vecchia", "Alfa", "zeta"),
            CardQuery.sorted(cards).map { it.name },
        )
    }

    @Test
    fun searchIgnoresCaseAndAccents() {
        val cards = listOf(card("Caffè Città"), card("Bar Sport"))
        assertEquals(listOf("Caffè Città"), CardQuery.filter(cards, "CAFFE citta").map { it.name })
        assertEquals(listOf("Caffè Città"), CardQuery.filter(cards, "città").map { it.name })
    }

    @Test
    fun searchMatchesNumberIgnoringSpaces() {
        val cards = listOf(card("A", number = "8001 2345 6789 7"), card("B", number = "999"))
        assertEquals(listOf("A"), CardQuery.filter(cards, "12345").map { it.name })
    }

    @Test
    fun blankQueryReturnsEverything() {
        val cards = listOf(card("A"), card("B"))
        assertEquals(cards, CardQuery.filter(cards, "   "))
    }

    private fun stored(id: String, name: String, number: String, storeId: String?) =
        card(name, number).copy(id = id, storeId = storeId)

    @Test
    fun duplicateNeedsSameNumberAndSameStore() {
        val cards = listOf(
            stored("1", "CTS Supermercati", "1234 5678", "cts"),
            stored("2", "Bar Sport", "999", null),
        )
        assertEquals("1", CardQuery.findDuplicate(cards, "12345678", "cts", "CTS")?.id)
        assertNull(CardQuery.findDuplicate(cards, "12345678", "pim", "PIM"))
        assertNull(CardQuery.findDuplicate(cards, "12345679", "cts", "CTS"))
        assertNull(CardQuery.findDuplicate(cards, "", "cts", "CTS"))
    }

    @Test
    fun duplicateWithoutStoreComparesTheName() {
        val cards = listOf(stored("2", "Bar Sport", "AB 99", null), stored("3", "Altro", "AB99", "cts"))
        assertEquals("2", CardQuery.findDuplicate(cards, "ab99", null, "  bar sport ")?.id)
        assertNull(CardQuery.findDuplicate(cards, "ab99", null, "Bar Centrale"))
    }

    @Test
    fun cardBeingEditedIsNotItsOwnDuplicate() {
        val cards = listOf(stored("1", "CTS", "123", "cts"))
        assertNull(CardQuery.findDuplicate(cards, "123", "cts", "CTS", excludeId = "1"))
    }

    @Test
    fun nameOrderKeepsFavoritesFirst() {
        val cards = listOf(card("zeta", lastUsed = 9), card("Beta", favorite = true), card("alfa"), card("Àrea"))
        assertEquals(listOf("Beta", "alfa", "Àrea", "zeta"), CardQuery.sorted(cards, SortOrder.NAME).map { it.name })
    }

    @Test
    fun manualOrderPutsNeverMovedLast() {
        val cards = listOf(
            card("nuova").copy(createdAt = 5),
            card("seconda").copy(rank = 2.0),
            card("prima").copy(rank = 1.0),
            card("vecchia").copy(createdAt = 1),
        )
        assertEquals(
            listOf("prima", "seconda", "vecchia", "nuova"),
            CardQuery.sorted(cards, SortOrder.MANUAL).map { it.name },
        )
    }

    @Test
    fun favoritesAndOthersHaveTheirOwnOrder() {
        val cards = listOf(
            card("pref-b", favorite = true, lastUsed = 900).copy(rank = 2.0),
            card("pref-a", favorite = true, lastUsed = 1).copy(rank = 1.0),
            card("pref-nuova", favorite = true, lastUsed = 999),
            card("Zeta", lastUsed = 10).copy(rank = 0.5),
            card("alfa", lastUsed = 50),
        )
        // Preferite personalizzate (senza posizione in fondo), altre per uso recente.
        assertEquals(
            listOf("pref-a", "pref-b", "pref-nuova", "alfa", "Zeta"),
            CardQuery.sorted(cards, SortOrder.RECENT, favoritesOrder = SortOrder.MANUAL).map { it.name },
        )
        // Preferite per uso recente, altre personalizzate.
        assertEquals(
            listOf("pref-nuova", "pref-b", "pref-a", "Zeta", "alfa"),
            CardQuery.sorted(cards, SortOrder.MANUAL, favoritesOrder = SortOrder.RECENT).map { it.name },
        )
        // Senza un ordine per le preferite vale quello della lista.
        assertEquals(
            listOf("pref-a", "pref-b", "pref-nuova", "alfa", "Zeta"),
            CardQuery.sorted(cards, SortOrder.NAME).map { it.name },
        )
    }

    @Test
    fun quickAccessFollowsTheFavoritesOrderAndSkipsArchived() {
        val cards = listOf(
            card("pref-b", favorite = true, lastUsed = 900).copy(rank = 2.0),
            card("pref-a", favorite = true, lastUsed = 1).copy(rank = 1.0),
            card("archiviata", favorite = true).copy(rank = 0.0, archived = true),
            card("vecchia", lastUsed = 5).copy(rank = 1.0),
            card("recente", lastUsed = 50).copy(rank = 2.0),
        )
        assertEquals(
            listOf("pref-a", "pref-b", "recente", "vecchia"),
            CardQuery.quickAccess(cards, SortOrder.MANUAL).map { it.name },
        )
        assertEquals(listOf("pref-b", "pref-a", "recente", "vecchia"), CardQuery.quickAccess(cards).map { it.name })
    }

    @Test
    fun freezeOrderKeepsTheVisibleOrderOnlyIfNoneHasARank() {
        val visible = listOf(card("c"), card("a"), card("b"))
        assertEquals(mapOf("c" to 1.0, "a" to 2.0, "b" to 3.0), CardQuery.freezeOrder(visible))
        val partly = listOf(card("c"), card("a").copy(rank = 4.0))
        assertEquals(emptyMap<String, Double>(), CardQuery.freezeOrder(partly))
        assertEquals(emptyMap<String, Double>(), CardQuery.freezeOrder(emptyList()))
    }

    @Test
    fun reorderMovesOnlyTheCardWhenAllHaveRanks() {
        val cards = listOf(card("a").copy(rank = 1.0), card("b").copy(rank = 2.0), card("c").copy(rank = 3.0))
        assertEquals(mapOf("c" to 1.5), CardQuery.reorder(cards, from = 2, to = 1))
        assertEquals(mapOf("c" to 0.0), CardQuery.reorder(cards, from = 2, to = 0))
        assertEquals(mapOf("a" to 4.0), CardQuery.reorder(cards, from = 0, to = 2))
        assertEquals(emptyMap<String, Double>(), CardQuery.reorder(cards, from = 1, to = 1))
    }

    @Test
    fun reorderRenumbersWhenSomeHaveNoRank() {
        val cards = listOf(card("a").copy(rank = 1.0), card("b"), card("c"))
        // Nuovo ordine a, c, b: "a" ha già 1.0 e non cambia.
        assertEquals(mapOf("c" to 2.0, "b" to 3.0), CardQuery.reorder(cards, from = 2, to = 1))
    }

    @Test
    fun reorderRenumbersWhenThereIsNoRoom() {
        val cards = listOf(card("a").copy(rank = 1.0), card("b").copy(rank = Math.nextUp(1.0)), card("c").copy(rank = 3.0))
        val result = CardQuery.reorder(cards, from = 2, to = 1)
        assertEquals(mapOf("c" to 2.0, "b" to 3.0), result)
    }

    @Test
    fun searchAndFilterByLabel() {
        val cards = listOf(card("A").copy(labels = listOf("Spesa")), card("B").copy(labels = listOf("Viaggi")), card("C"))
        assertEquals(listOf("A"), CardQuery.filter(cards, "spes").map { it.name })
        assertEquals(listOf("B"), CardQuery.withLabel(cards, "VIAGGI").map { it.name })
        assertEquals(listOf("Spesa", "Viaggi"), CardQuery.labels(cards + card("D").copy(labels = listOf("spesa"))))
    }

    @Test
    fun quickAccessSkipsArchived() {
        val cards = listOf(card("A").copy(archived = true), card("B"))
        assertEquals(listOf("B"), CardQuery.quickAccess(cards).map { it.name })
    }
}
