package dev.cantacard.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteIconCacheTest {

    @Test
    fun fileNameKeepsOnlyDomainCharacters() {
        assertEquals("esselunga.it.webp", SiteIconCache.fileName("Esselunga.IT "))
        assertEquals("mondo-convenienza.it.webp", SiteIconCache.fileName("mondo-convenienza.it"))
        assertNull(SiteIconCache.fileName(""))
        assertNull(SiteIconCache.fileName("../files/cards.json"))
        assertNull(SiteIconCache.fileName("a/b.it"))
        assertNull(SiteIconCache.fileName(".."))
    }

    @Test
    fun fetcherAlsoTriesWwwHost() {
        assertEquals(listOf("despar.it", "www.despar.it"), SiteIconFetcher.hosts("despar.it"))
        assertEquals(listOf("www.q8.it"), SiteIconFetcher.hosts("www.q8.it"))
    }

    @Test
    fun iconIsFreshForThirtyDays() {
        val now = 1_000_000_000_000L
        assertTrue(SiteIconCache.isFresh(now, now))
        assertTrue(SiteIconCache.isFresh(now - SiteIconCache.MAX_AGE_MS, now))
        assertFalse(SiteIconCache.isFresh(now - SiteIconCache.MAX_AGE_MS - 1, now))
        // Data nel futuro (orologio cambiato): si riscarica.
        assertFalse(SiteIconCache.isFresh(now + 60_000, now))
    }
}
