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
        // Un'icona scritta nel catalogo ma scartata dalla validazione sarebbe un errore silenzioso.
        for (i in 0 until raw.length()) {
            val o = raw.getJSONObject(i)
            if (o.has("icon")) {
                val store = catalog.find(o.getString("id"))!!
                assertTrue("${store.id}: icona valida (HTTPS, sito del negozio, non SVG)", store.icon != null)
            }
        }
        assertTrue("CTS ha l'icona", catalog.find("cts")?.icon != null)
        // Colori precedenti: tutti validi e mai uguali a quello attuale.
        for (i in 0 until raw.length()) {
            val o = raw.getJSONObject(i)
            val former = o.optJSONArray("formerColors") ?: continue
            val store = catalog.find(o.getString("id"))!!
            assertEquals("${store.id}: colori precedenti validi", former.length(), store.formerColors.size)
            assertTrue("${store.id}: colore precedente diverso dall'attuale", store.color !in store.formerColors)
        }
    }

    @Test
    fun formerColorsAreReadAndToleratedWhenWrong() {
        val catalog = CatalogJson.decode(
            """{"version": 1, "stores": [
                {"id": "a", "name": "A", "color": "#FF602E", "formerColors": ["#DF1B7C", "non un colore"]},
                {"id": "b", "name": "B", "color": "#111111"}
            ]}""",
        )
        assertEquals(listOf(CardColors.parse("#DF1B7C")), catalog.find("a")?.formerColors)
        assertEquals(emptyList<Int>(), catalog.find("b")?.formerColors)
    }

    @Test
    fun iconOnlyFromTheStoreSiteOverHttps() {
        val ok = "https://www.ctssupermercati.it/wp-content/uploads/logo.png"
        assertEquals(ok, CatalogJson.iconOrNull(ok, "ctssupermercati.it"))
        assertEquals("https://ctssupermercati.it/l.png", CatalogJson.iconOrNull("https://ctssupermercati.it/l.png", "ctssupermercati.it"))
        assertNull(CatalogJson.iconOrNull("http://www.ctssupermercati.it/logo.png", "ctssupermercati.it"))
        assertNull(CatalogJson.iconOrNull("https://www.google.com/s2/favicons?domain=ctssupermercati.it", "ctssupermercati.it"))
        assertNull(CatalogJson.iconOrNull("https://evilctssupermercati.it/logo.png", "ctssupermercati.it"))
        assertNull(CatalogJson.iconOrNull("https://www.ctssupermercati.it/logo.svg", "ctssupermercati.it"))
        assertNull(CatalogJson.iconOrNull(ok, null))
        assertNull(CatalogJson.iconOrNull("non un indirizzo", "ctssupermercati.it"))
    }
}
