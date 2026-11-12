package dev.cantacard.data

import org.junit.Assert.assertEquals
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
}
