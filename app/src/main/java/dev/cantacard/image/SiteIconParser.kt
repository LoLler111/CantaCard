package dev.cantacard.image

import org.json.JSONException
import org.json.JSONObject
import java.net.MalformedURLException
import java.net.URL
import java.util.Locale

/**
 * Trova le icone dichiarate da un sito (funzioni pure, testate in JVM). Ordine di preferenza
 * deciso nel progetto: manifest PWA → apple-touch-icon → `link rel="icon"` → `/favicon.ico`.
 * Solo indirizzi HTTPS; gli SVG si scartano (non si decodificano senza librerie).
 */
object SiteIconParser {

    enum class Source { MANIFEST, APPLE_TOUCH, LINK_ICON, FAVICON }

    data class Candidate(val url: String, val size: Int, val source: Source)

    data class Page(val manifestUrl: String?, val appleTouch: List<Candidate>, val icons: List<Candidate>)

    private val linkTag = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val attribute = Regex("([a-zA-Z:-]+)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))")
    private val sizeToken = Regex("(\\d+)\\s*[xX]\\s*(\\d+)")
    private val spaces = Regex("\\s+")

    /** Legge i tag `<link>` della pagina. */
    fun parsePage(html: String, base: URL): Page {
        var manifest: String? = null
        val apple = ArrayList<Candidate>()
        val icons = ArrayList<Candidate>()
        for (tag in linkTag.findAll(html)) {
            val attrs = attributes(tag.value)
            val rel = attrs["rel"]?.lowercase(Locale.ROOT)?.split(spaces)?.toSet() ?: continue
            val href = attrs["href"] ?: continue
            if ("manifest" in rel) {
                if (manifest == null) manifest = resolveHttps(base, href)
                continue
            }
            if (isSvg(href, attrs["type"])) continue
            val url = resolveHttps(base, href) ?: continue
            val size = largestSize(attrs["sizes"])
            when {
                "apple-touch-icon" in rel || "apple-touch-icon-precomposed" in rel ->
                    apple += Candidate(url, size.takeIf { it > 0 } ?: 180, Source.APPLE_TOUCH)
                "icon" in rel -> icons += Candidate(url, size, Source.LINK_ICON)
            }
        }
        return Page(manifest, sortBySize(apple), sortBySize(icons))
    }

    /** Icone del manifest PWA (`icons[]`), escluse quelle solo monocromatiche. */
    fun parseManifest(json: String, base: URL): List<Candidate> {
        val icons = try {
            JSONObject(json).optJSONArray("icons")
        } catch (e: JSONException) {
            null
        } ?: return emptyList()
        val result = ArrayList<Candidate>()
        for (i in 0 until icons.length()) {
            val icon = icons.optJSONObject(i) ?: continue
            val src = icon.optString("src").takeIf { it.isNotBlank() } ?: continue
            if (isSvg(src, icon.optString("type"))) continue
            val purposes = icon.optString("purpose").lowercase(Locale.ROOT).split(spaces).filter { it.isNotEmpty() }
            if (purposes.isNotEmpty() && purposes.all { it == "monochrome" }) continue
            val url = resolveHttps(base, src) ?: continue
            result += Candidate(url, largestSize(icon.optString("sizes")), Source.MANIFEST)
        }
        return sortBySize(result)
    }

    /** Tutti i candidati nell'ordine in cui provarli, senza doppioni. */
    fun ordered(page: Page, manifest: List<Candidate>, origin: URL): List<Candidate> {
        val favicon = resolveHttps(origin, "/favicon.ico")?.let { Candidate(it, 0, Source.FAVICON) }
        return (manifest + page.appleTouch + page.icons + listOfNotNull(favicon)).distinctBy { it.url }
    }

    /** URL assoluto se HTTPS, altrimenti `null`. */
    fun resolveHttps(base: URL, href: String): String? = try {
        val url = URL(base, href.trim())
        if (url.protocol.equals("https", ignoreCase = true) && !url.host.isNullOrEmpty()) url.toString() else null
    } catch (e: MalformedURLException) {
        null
    }

    /** Lato maggiore dichiarato in `sizes` ("16x16 192x192" → 192), 0 se assente o "any". */
    fun largestSize(sizes: String?): Int =
        sizeToken.findAll(sizes.orEmpty()).maxOfOrNull { maxOf(it.groupValues[1].toInt(), it.groupValues[2].toInt()) } ?: 0

    /**
     * Prima le icone tra 96 e 512 px (le più grandi in testa), poi quelle enormi (le più piccole
     * in testa), poi quelle senza dimensione, infine le piccole (le più grandi in testa).
     */
    fun sortBySize(list: List<Candidate>): List<Candidate> = list.sortedWith(
        compareBy<Candidate> {
            when {
                it.size in 96..512 -> 0
                it.size > 512 -> 1
                it.size == 0 -> 2
                else -> 3
            }
        }.thenBy { if (it.size > 512) it.size else -it.size },
    )

    private fun isSvg(href: String, type: String?): Boolean =
        type.orEmpty().contains("svg", ignoreCase = true) ||
            href.substringBefore('?').substringBefore('#').endsWith(".svg", ignoreCase = true)

    private fun attributes(tag: String): Map<String, String> =
        attribute.findAll(tag).associate { m ->
            val value = m.groups[3]?.value ?: m.groups[4]?.value ?: m.groups[5]?.value.orEmpty()
            m.groupValues[1].lowercase(Locale.ROOT) to value.htmlUnescape()
        }

    private fun String.htmlUnescape(): String =
        replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&#x2F;", "/")
}
