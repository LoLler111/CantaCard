package dev.cantacard.data

import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardJsonTest {

    private val full = Card(
        id = "a1",
        name = "Négozio \"Prova\"",
        number = "8001234567897",
        formatName = "EAN_13",
        color = 0xFF00695C.toInt(),
        storeId = "esempio",
        image = "a1.webp",
        favorite = true,
        lastUsed = 1_700_000_000_000,
        useCount = 3,
        createdAt = 1_600_000_000_000,
        note = "riga 1\nriga 2",
    )

    @Test
    fun roundTripKeepsEveryField() {
        val decoded = CardJson.decode(CardJson.encode(listOf(full, full.copy(id = "a2", note = null))))
        assertEquals(listOf(full, full.copy(id = "a2", note = null)), decoded.cards)
        assertEquals(0, decoded.skipped)
        assertEquals(CardJson.VERSION, decoded.version)
    }

    @Test
    fun missingOptionalFieldsGetDefaults() {
        val card = CardJson.decode(
            """{"version":1,"cards":[{"id":"x","number":"123","format":"QR_CODE"}]}""",
        ).cards.single()
        assertEquals("", card.name)
        assertEquals(CardColors.forName(""), card.color)
        assertNull(card.storeId)
        assertNull(card.image)
        assertFalse(card.favorite)
        assertEquals(0L, card.lastUsed)
        assertEquals(0, card.useCount)
    }

    @Test
    fun invalidRecordsAreSkippedWithoutLosingTheOthers() {
        val decoded = CardJson.decode(
            """
            {"cards":[
              {"id":"ok1","number":"1","format":"EAN_13"},
              {"id":"no-number","format":"EAN_13"},
              {"number":"2","format":"EAN_13"},
              {"id":"null-format","number":"3","format":null},
              "non un oggetto",
              {"id":"ok1","number":"dup","format":"EAN_13"},
              {"id":"ok2","number":"4","format":"CODE_128","color":"non-un-colore"}
            ]}
            """.trimIndent(),
        )
        assertEquals(listOf("ok1", "ok2"), decoded.cards.map { it.id })
        assertEquals("1", decoded.cards[0].number)
        assertEquals(5, decoded.skipped)
    }

    @Test
    fun futureVersionWithUnknownFieldsIsReadable() {
        val decoded = CardJson.decode(
            """
            {"version":99,"nuovoCampo":{"a":1},"cards":[
              {"id":"f","number":"9","format":"FORMATO_FUTURO","nuovo":[1,2],"favorite":true}
            ]}
            """.trimIndent(),
        )
        assertEquals(99, decoded.version)
        val card = decoded.cards.single()
        assertEquals("FORMATO_FUTURO", card.formatName)
        assertNull(card.format)
        assertTrue(card.favorite)
        // Il formato sconosciuto sopravvive a una nuova scrittura.
        assertEquals("FORMATO_FUTURO", CardJson.decode(CardJson.encode(listOf(card))).cards.single().formatName)
    }

    @Test
    fun missingCardsArrayMeansNoCards() {
        assertTrue(CardJson.decode("""{"version":1}""").cards.isEmpty())
    }

    @Test(expected = JSONException::class)
    fun notJsonThrows() {
        CardJson.decode("non è json")
    }
}
