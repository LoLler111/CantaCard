package dev.cantacard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class CatimaCsvTest {

    private var next = 0
    private fun ids(): () -> String = { "id-${next++}" }

    /** Esportazione nel formato 2 di Catima: gruppi, tessere e legami tessera-gruppo. */
    private val v2 = listOf(
        "2",
        "",
        "_id",
        "Spesa",
        "Viaggi",
        "",
        "_id,store,note,validfrom,expiry,balance,balancetype,cardid,barcodeid,barcodetype,barcodeencoding," +
            "headercolor,starstatus,lastused,archive",
        "1,Coop,,,,0,,2500000000017,,EAN_13,,-2096870,1,1794500000,0",
        "2,\"Treni, carta \"\"Viaggio\"\"\",\"riga 1",
        "riga 2\",,1806451200000,0,,AB-123,XYZ987,QR_CODE,,,0,,1",
        "3,Senza codice,,,,0,,123456,,,,,0,,0",
        "4,,,,,0,,,,EAN_13,,,0,,0",
        "",
        "cardId,groupId",
        "1,Spesa",
        "2,Viaggi",
        "2,Spesa",
        "",
    ).joinToString("\n")

    @Test
    fun readsVersion2() {
        val entries = CatimaCsv.parse(v2, ids(), ZoneOffset.UTC)!!
        assertEquals(listOf("1", "2", "3"), entries.map { it.catimaId })

        val coop = entries[0].card
        assertEquals("Coop", coop.name)
        assertEquals("2500000000017", coop.number)
        assertEquals("EAN_13", coop.formatName)
        assertEquals(-2096870, coop.color)
        assertTrue(coop.favorite)
        assertEquals(1794500000000, coop.lastUsed)
        assertEquals(listOf("Spesa"), coop.labels)
        assertFalse(coop.archived)
        assertNull(coop.expiry)

        val treni = entries[1].card
        assertEquals("Treni, carta \"Viaggio\"", treni.name)
        // Il codice contiene barcodeid; il numero mostrato in Catima finisce nella nota.
        assertEquals("XYZ987", treni.number)
        assertEquals("QR_CODE", treni.formatName)
        assertEquals("riga 1\nriga 2\nN. AB-123", treni.note)
        assertEquals("2027-03-31", treni.expiry)
        assertEquals(listOf("Viaggi", "Spesa"), treni.labels)
        assertTrue(treni.archived)

        // Senza formato in Catima: si deduce dal numero come nell'inserimento manuale.
        assertEquals("CODE_128", entries[2].card.formatName)
    }

    @Test
    fun readsOldVersionWithoutGroups() {
        val v1 = "_id,store,note,cardid,barcodetype,headercolor,headertextcolor,starstatus\r\n" +
            "7,Biblioteca,,A-1,CODE_39,,,0\r\n"
        val card = CatimaCsv.parse(v1, ids())!!.single().card
        assertEquals("Biblioteca", card.name)
        assertEquals("CODE_39", card.formatName)
        assertEquals(emptyList<String>(), card.labels)
    }

    @Test
    fun otherCsvIsNotCatima() {
        assertNull(CatimaCsv.parse("nome,numero\nCoop,123\n"))
        assertNull(CatimaCsv.parse(""))
    }

    @Test
    fun csvRecordsHandleQuotes() {
        assertEquals(
            listOf(listOf("a", "b,c", "d\"e"), listOf("")),
            CatimaCsv.records("a,\"b,c\",\"d\"\"e\"\n\n"),
        )
    }
}
