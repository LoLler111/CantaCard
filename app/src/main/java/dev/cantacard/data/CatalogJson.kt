package dev.cantacard.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/**
 * Lettura del catalogo negozi (`catalog/stores.json`):
 * `{"version": N, "stores": [{"id", "name", "aliases", "domain", "color", "format", "countries", "icon"?}]}`.
 *
 * Tollerante come [CardJson]: campi sconosciuti ignorati, un negozio senza id o nome viene
 * scartato senza bloccare gli altri, id duplicati tengono il primo.
 */
object CatalogJson {

    /** @throws JSONException se il testo non è un oggetto JSON. */
    fun decode(text: String): Catalog {
        val root = JSONObject(text)
        val array = root.optJSONArray("stores") ?: JSONArray()
        val seen = HashSet<String>()
        val stores = ArrayList<Store>(array.length())
        for (i in 0 until array.length()) {
            val store = array.optJSONObject(i)?.let(::storeOrNull) ?: continue
            if (seen.add(store.id)) stores += store
        }
        return Catalog(root.optInt("version", 0), stores)
    }

    /** Come [decode] ma `null` se il testo non è valido o non contiene negozi. */
    fun decodeOrNull(text: String): Catalog? = try {
        decode(text).takeIf { it.stores.isNotEmpty() }
    } catch (e: JSONException) {
        null
    }

    /** La copia da usare: quella con versione più alta; a parità, quella inclusa nell'app. */
    fun newest(bundled: Catalog?, downloaded: Catalog?): Catalog = when {
        bundled == null -> downloaded ?: Catalog.EMPTY
        downloaded == null -> bundled
        downloaded.version > bundled.version -> downloaded
        else -> bundled
    }

    private fun storeOrNull(o: JSONObject): Store? = try {
        val id = o.string("id")
        val name = o.string("name")
        if (id == null || name == null) {
            null
        } else {
            val domain = o.string("domain")
            Store(
                id = id,
                name = name,
                aliases = o.optJSONArray("aliases").strings(),
                domain = domain,
                color = CardColors.parse(o.string("color")),
                formatName = o.string("format"),
                countries = o.optJSONArray("countries").strings(),
                icon = iconOrNull(o.string("icon"), domain),
            )
        }
    } catch (e: JSONException) {
        null
    }

    /**
     * L'indirizzo dell'icona solo se è HTTPS, non SVG e sul sito del negozio ([domain] o un suo
     * sottodominio): mai immagini da servizi di terzi. Altrimenti `null` (si cerca come sempre).
     */
    fun iconOrNull(url: String?, domain: String?): String? {
        if (url == null || domain == null) return null
        val uri = try {
            URI(url)
        } catch (e: URISyntaxException) {
            return null
        }
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        val ownSite = host == domain || host.endsWith(".$domain")
        val svg = uri.path.orEmpty().endsWith(".svg", ignoreCase = true)
        return url.takeIf { uri.scheme.equals("https", ignoreCase = true) && ownSite && !svg }
    }

    private fun JSONArray?.strings(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { i -> optString(i).trim().takeIf { it.isNotEmpty() && !isNull(i) } }
    }

    private fun JSONObject.string(key: String): String? =
        if (isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() }
}
