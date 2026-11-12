package dev.cantacard.data

import org.junit.Assert.assertEquals
import org.junit.Test

class StoreQueryTest {

    private val stores = listOf(
        Store("tigota", "Tigotà"),
        Store("acquaesapone", "Acqua & Sapone", aliases = listOf("Acqua e Sapone")),
        Store("esselunga", "Esselunga", aliases = listOf("Fidaty")),
        Store("hm", "H&M", aliases = listOf("HM")),
        Store("coop", "Coop", aliases = listOf("Ipercoop")),
        Store("iper", "Iper"),
    )

    private fun ids(query: String) = StoreQuery.search(stores, query).map { it.id }

    @Test
    fun emptyQueryIsAlphabetical() {
        assertEquals(listOf("acquaesapone", "coop", "esselunga", "hm", "iper", "tigota"), ids(" "))
    }

    @Test
    fun ignoresAccentsCaseSpacesAndPunctuation() {
        assertEquals(listOf("tigota"), ids("TIGOTA"))
        assertEquals(listOf("acquaesapone"), ids("acqua e sap"))
        assertEquals(listOf("acquaesapone"), ids("acqua&sapone"))
        assertEquals(listOf("hm"), ids("h&m"))
        assertEquals(listOf("hm"), ids("hm"))
    }

    @Test
    fun searchesAliases() {
        assertEquals(listOf("esselunga"), ids("fidaty"))
    }

    @Test
    fun prefixMatchesComeBeforeContains() {
        // "Iper" inizia con "iper", "Coop" lo contiene solo nell'alias "Ipercoop" (che però inizia con iper).
        assertEquals(listOf("coop", "iper"), ids("iper"))
        assertEquals(listOf("coop"), ids("percoop"))
    }
}
