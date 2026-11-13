package dev.cantacard.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateJsonTest {

    private fun release(tag: String, vararg assets: String, draft: Boolean = false, prerelease: Boolean = true) =
        """{"tag_name":"$tag","draft":$draft,"prerelease":$prerelease,"assets":[${assets.joinToString(",")}]}"""

    private fun apk(name: String, size: Long = 2_000_000) =
        """{"name":"$name","size":$size,"browser_download_url":"https://github.com/LoLler111/CantaCard/releases/download/x/$name"}"""

    @Test
    fun picksNewestReleaseWithApk() {
        val json = "[" + listOf(
            release("v0.3.0", """{"name":"notes.txt","size":10,"browser_download_url":"https://x/notes.txt"}"""),
            release("v0.2.1", apk("CantaCard-0.2.1.apk")),
            release("v0.2.0", apk("CantaCard-0.2.0.apk")),
            release("v0.1.1", apk("CantaCard-0.1.1.apk")),
        ).joinToString(",") + "]"
        val found = UpdateJson.newestApk(json, "0.1.1")!!
        assertEquals("0.2.1", found.version)
        assertTrue(found.apkUrl.endsWith("CantaCard-0.2.1.apk"))
        assertEquals(2_000_000L, found.apkSize)
    }

    @Test
    fun orderInResponseDoesNotMatter() {
        val json = "[" + release("v0.2.0", apk("a.apk")) + "," + release("v0.10.0", apk("b.apk")) + "]"
        assertEquals("0.10.0", UpdateJson.newestApk(json, "0.1.0")!!.version)
    }

    @Test
    fun nothingWhenUpToDate() {
        val json = "[" + release("v0.1.1", apk("CantaCard-0.1.1.apk")) + "]"
        assertNull(UpdateJson.newestApk(json, "0.1.1"))
        assertNull(UpdateJson.newestApk(json, "0.2.0"))
    }

    @Test
    fun skipsDraftsButNotPrereleases() {
        val json = "[" + release("v0.3.0", apk("a.apk"), draft = true) + "," +
            release("v0.2.0", apk("b.apk"), prerelease = true) + "]"
        assertEquals("0.2.0", UpdateJson.newestApk(json, "0.1.1")!!.version)
    }

    @Test
    fun ignoresInsecureUrlsAndBadInput() {
        val http = """{"name":"a.apk","size":1,"browser_download_url":"http://example.com/a.apk"}"""
        assertNull(UpdateJson.newestApk("[" + release("v1.0.0", http) + "]", "0.1.0"))
        assertNull(UpdateJson.newestApk("[" + release("nightly", apk("a.apk")) + "]", "0.1.0"))
        assertNull(UpdateJson.newestApk("not json", "0.1.0"))
        assertNull(UpdateJson.newestApk("""{"message":"API rate limit exceeded"}""", "0.1.0"))
    }

    @Test
    fun comparesVersions() {
        assertEquals(0, UpdateJson.compare("1.0", "1.0.0"))
        assertTrue(UpdateJson.compare("0.10.0", "0.9.9")!! > 0)
        assertTrue(UpdateJson.compare("1.0.0-beta", "1.0.0")!! < 0)
        assertTrue(UpdateJson.compare("1.0.0", "1.0.0-rc1")!! > 0)
        assertNull(UpdateJson.compare("abc", "1.0"))
        assertNull(UpdateJson.compare("", "1.0"))
    }
}
