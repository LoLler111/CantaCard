package dev.cantacard.update

import org.json.JSONArray
import org.json.JSONException

/** Una versione di CantaCard pubblicata su GitHub Releases, con il suo APK. */
data class AppRelease(
    val version: String,
    val apkUrl: String,
    /** Dimensione dichiarata da GitHub, in byte; 0 se sconosciuta. */
    val apkSize: Long,
)

/** Lettura della risposta di `GET /repos/{owner}/{repo}/releases` e confronto delle versioni. */
object UpdateJson {

    /**
     * La release più recente con un APK allegato e una versione maggiore di [currentVersion];
     * `null` se non ce n'è o se il testo non è valido. Le bozze sono ignorate, le pre-release no
     * (le 0.x escono tutte come pre-release).
     */
    fun newestApk(json: String, currentVersion: String): AppRelease? {
        val releases = try {
            JSONArray(json)
        } catch (e: JSONException) {
            return null
        }
        var best: AppRelease? = null
        for (i in 0 until releases.length()) {
            val release = releases.optJSONObject(i) ?: continue
            if (release.optBoolean("draft", false)) continue
            val version = release.optString("tag_name").removePrefix("v").removePrefix("V")
            if (compare(version, currentVersion)?.let { it > 0 } != true) continue
            if (best != null && compare(version, best.version)?.let { it > 0 } != true) continue
            val assets = release.optJSONArray("assets") ?: continue
            for (j in 0 until assets.length()) {
                val asset = assets.optJSONObject(j) ?: continue
                val url = asset.optString("browser_download_url")
                if (!asset.optString("name").endsWith(".apk", ignoreCase = true)) continue
                if (!url.startsWith("https://")) continue
                best = AppRelease(version, url, asset.optLong("size", 0L).coerceAtLeast(0L))
                break
            }
        }
        return best
    }

    /**
     * Confronta due versioni `X.Y.Z` (anche con meno o più numeri); un suffisso (`1.0.0-beta`)
     * viene prima della stessa versione senza. `null` se una delle due non è una versione.
     */
    fun compare(a: String, b: String): Int? {
        val (coreA, suffixA) = split(a) ?: return null
        val (coreB, suffixB) = split(b) ?: return null
        for (i in 0 until maxOf(coreA.size, coreB.size)) {
            val diff = coreA.getOrElse(i) { 0 }.compareTo(coreB.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return when {
            suffixA == suffixB -> 0
            suffixA.isEmpty() -> 1
            suffixB.isEmpty() -> -1
            else -> suffixA.compareTo(suffixB)
        }
    }

    private fun split(version: String): Pair<List<Int>, String>? {
        val end = version.indexOfFirst { it == '-' || it == '+' }.let { if (it < 0) version.length else it }
        val numbers = version.substring(0, end).split('.').map { it.toIntOrNull() ?: return null }
        if (numbers.isEmpty() || numbers.any { it < 0 }) return null
        return numbers to version.substring(end).removePrefix("-")
    }
}
