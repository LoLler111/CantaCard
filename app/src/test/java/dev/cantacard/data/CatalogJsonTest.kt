package dev.cantacard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CatalogJsonTest {

    @Test
    fun readsStoresIgnoringUnknownFieldsAndEmptyValues() {
        val catalog = CatalogJson.decode(
            """{"version": 3, "extra": true, "stores": [
                {"id": "a", "name": "Alfa", "aliases": ["A1", "", null], "domain": "alfa.it",
                 "color": "#112233", "format": "EAN_13", "countries": ["IT"], "future": 1},
                {"id": "b", "name": "Beta", "domain": "", "format": "", "color": "rosso"}
            ]}""",
        )
        assertEquals(3, catalog.version)
        val a = catalog.find("a")!!
        assertEquals(listOf("A1"), a.aliases)
        assertEquals("alfa.it", a.domain)
        assertEquals(0xFF112233.toInt(), a.color)
        assertEquals(CodeFormat.EAN_13, a.format)
        val b = catalog.find("b")!!
        assertNull(b.domain)
        assertNull(b.formatName)
        assertNull(b.color)
    }

    @Test
    fun skipsInvalidRecordsAndDuplicates() {
        val catalog = CatalogJson.decode(
            """{"version": 1, "stores": [
                {"id": "a", "name": "Primo"}, {"name": "Senza id"}, {"id": "c"}, 42,
                {"id": "a", "name": "Doppione"}
            ]}""",
        )
        assertEquals(listOf("Primo"), catalog.stores.map { it.name })
    }

    @Test
    fun decodeOrNullRejectsGarbageAndEmptyCatalogs() {
        assertNull(CatalogJson.decodeOrNull("<html>404</html>"))
        assertNull(CatalogJson.decodeOrNull("""{"version": 9, "stores": []}"""))
    }

    @Test
    fun newestPrefersHigherVersionAndBundledOnTie() {
        val bundled = Catalog(2, listOf(Store("a", "A")))
        val older = Catalog(1, listOf(Store("b", "B")))
        val same = Catalog(2, listOf(Store("c", "C")))
        val newer = Catalog(3, listOf(Store("d", "D")))
        assertSame(bundled, CatalogJson.newest(bundled, null))
        assertSame(bundled, CatalogJson.newest(bundled, older))
        assertSame(bundled, CatalogJson.newest(bundled, same))
        assertSame(newer, CatalogJson.newest(bundled, newer))
        assertSame(newer, CatalogJson.newest(null, newer))
    }

    /** Il catalogo reale (incluso nell'APK e servito da GitHub) deve essere valido e pulito. */
    @Test
    fun bundledCatalogIsValid() {
        val file = File("../catalog/stores.json")
        val text = file.readText()
        val catalog = CatalogJson.decode(text)
        assertTrue("version >= 1", catalog.version >= 1)
        assertTrue("almeno 40 negozi", catalog.stores.size >= 40)
        val raw = org.json.JSONObject(text).getJSONArray("stores")
        assertEquals("nessun record scartato o duplicato", raw.length(), catalog.stores.size)
        catalog.stores.forEach { store ->
            assertTrue("${store.id}: id in minuscolo", store.id.matches(Regex("[a-z0-9]+")))
            assertTrue("${store.id}: colore", store.color != null)
            store.formatName?.let { assertTrue("${store.id}: formato $it", CodeFormat.fromName(it) != null) }
            store.domain?.let {
                assertTrue("${store.id}: dominio senza schema/percorso", it.matches(Regex("[a-z0-9.-]+[.][a-z]{2,}")))
            }
        }
    }
}
