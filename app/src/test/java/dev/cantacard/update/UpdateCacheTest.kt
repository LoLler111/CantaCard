package dev.cantacard.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UpdateCacheTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun emptyCacheHasNothing() {
        assertNull(UpdateCache(File(temp.root, "update")).read())
    }

    @Test
    fun keepsBodyWithItsEtag() {
        val cache = UpdateCache(File(temp.root, "update"))
        cache.write("W/\"abc123\"", "[{\"tag_name\":\"v0.2.0\"}]")
        val entry = cache.read()!!
        assertEquals("W/\"abc123\"", entry.etag)
        assertEquals("[{\"tag_name\":\"v0.2.0\"}]", entry.body)
    }

    @Test
    fun responseWithoutEtagClearsCache() {
        val cache = UpdateCache(File(temp.root, "update"))
        cache.write("\"x\"", "[]")
        cache.write(null, "[1]")
        assertNull(cache.read())
    }

    @Test
    fun bodyWithoutEtagIsNotUsed() {
        val dir = File(temp.root, "update").apply { mkdirs() }
        File(dir, "releases.json").writeText("[]")
        assertNull(UpdateCache(dir).read())
    }
}
