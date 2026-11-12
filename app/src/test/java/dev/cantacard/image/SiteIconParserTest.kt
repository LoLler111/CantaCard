package dev.cantacard.image

import dev.cantacard.image.SiteIconParser.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URL

class SiteIconParserTest {

    private val base = URL("https://www.negozio.it/it/home.html")

    @Test
    fun readsLinkTagsResolvingRelativeUrlsAndSkippingSvgAndHttp() {
        val html = """
            <html><head>
            <link rel="stylesheet" href="/a.css">
            <LINK REL="icon" type="image/svg+xml" href="/logo.svg">
            <link rel='shortcut icon' href='favicon-32.png' sizes='32x32'>
            <link rel="icon" href="/img/icon-192.png" sizes="16x16 192x192">
            <link rel="icon" href="http://insicuro.it/i.png" sizes="512x512">
            <link rel=apple-touch-icon href=/apple.png>
            <link rel="manifest" href="/site.webmanifest">
            </head></html>
        """.trimIndent()
        val page = SiteIconParser.parsePage(html, base)
        assertEquals("https://www.negozio.it/site.webmanifest", page.manifestUrl)
        assertEquals(listOf("https://www.negozio.it/apple.png"), page.appleTouch.map { it.url })
        assertEquals(180, page.appleTouch.single().size)
        // La 192 prima della 32; SVG e http scartati.
        assertEquals(
            listOf("https://www.negozio.it/img/icon-192.png", "https://www.negozio.it/it/favicon-32.png"),
            page.icons.map { it.url },
        )
    }

    @Test
    fun manifestIconsSkipSvgAndMonochrome() {
        val json = """
            {"name": "x", "icons": [
              {"src": "/i/48.png", "sizes": "48x48"},
              {"src": "/i/512.png", "sizes": "512x512", "purpose": "any maskable"},
              {"src": "/i/mono.png", "sizes": "192x192", "purpose": "monochrome"},
              {"src": "/i/v.svg", "sizes": "any", "type": "image/svg+xml"},
              {"src": "icons/192.png", "sizes": "192x192"}
            ]}
        """.trimIndent()
        val icons = SiteIconParser.parseManifest(json, URL("https://www.negozio.it/static/site.webmanifest"))
        assertEquals(
            listOf(
                "https://www.negozio.it/i/512.png",
                "https://www.negozio.it/static/icons/192.png",
                "https://www.negozio.it/i/48.png",
            ),
            icons.map { it.url },
        )
        assertTrue(icons.all { it.source == Source.MANIFEST })
    }

    @Test
    fun brokenManifestGivesNothing() {
        assertTrue(SiteIconParser.parseManifest("<html>", base).isEmpty())
        assertTrue(SiteIconParser.parseManifest("""{"icons": "no"}""", base).isEmpty())
    }

    @Test
    fun orderFollowsProjectPreferenceAndEndsWithFavicon() {
        val page = SiteIconParser.Page(
            manifestUrl = null,
            appleTouch = listOf(SiteIconParser.Candidate("https://a.it/apple.png", 180, Source.APPLE_TOUCH)),
            icons = listOf(
                SiteIconParser.Candidate("https://a.it/i.png", 32, Source.LINK_ICON),
                SiteIconParser.Candidate("https://a.it/apple.png", 180, Source.LINK_ICON),
            ),
        )
        val manifest = listOf(SiteIconParser.Candidate("https://a.it/m.png", 192, Source.MANIFEST))
        val ordered = SiteIconParser.ordered(page, manifest, URL("https://a.it/pagina"))
        assertEquals(
            listOf("https://a.it/m.png", "https://a.it/apple.png", "https://a.it/i.png", "https://a.it/favicon.ico"),
            ordered.map { it.url },
        )
    }

    @Test
    fun sizesAndHttpsResolution() {
        assertEquals(192, SiteIconParser.largestSize("16x16 32X32 192x192"))
        assertEquals(0, SiteIconParser.largestSize("any"))
        assertEquals(0, SiteIconParser.largestSize(null))
        assertNull(SiteIconParser.resolveHttps(base, "http://x.it/a.png"))
        assertNull(SiteIconParser.resolveHttps(base, "data:image/png;base64,AAAA"))
        assertEquals("https://cdn.it/a.png", SiteIconParser.resolveHttps(base, "//cdn.it/a.png"))
    }

    @Test
    fun sortPrefersMediumThenHugeThenUnknownThenSmall() {
        fun c(size: Int) = SiteIconParser.Candidate("https://a.it/$size.png", size, Source.LINK_ICON)
        val sorted = SiteIconParser.sortBySize(listOf(c(16), c(0), c(1024), c(96), c(512), c(2048), c(64)))
        assertEquals(listOf(512, 96, 1024, 2048, 0, 64, 16), sorted.map { it.size })
    }

    @Test
    fun photoAspect() {
        assertTrue(ImageStore.isPhoto(512, 323))
        assertFalse(ImageStore.isPhoto(256, 256))
        assertFalse(ImageStore.isPhoto(180, 0))
    }
}
