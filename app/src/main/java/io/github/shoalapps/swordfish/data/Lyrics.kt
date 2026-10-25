package io.github.shoalapps.swordfish.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Lyrics from several open sources, best first:
 * 1. BetterLyrics (lyrics-api.boidu.dev): word-by-word timing, for karaoke-style highlighting.
 * 2. LRCLIB (lrclib.net): a free, community-run database of line-synced lyrics.
 * 3. NetEase Cloud Music: a huge catalogue of line-synced lyrics.
 * The order follows what Meld and Metrolist (GPL-3.0) found works best.
 */
object Lyrics {
    data class Word(val startMs: Long, val endMs: Long, val text: String)
    data class Line(val atMs: Long, val text: String, val words: List<Word> = emptyList())
    data class Result(val synced: List<Line>, val plain: String?, val source: String = "") {
        val wordSynced get() = synced.any { it.words.isNotEmpty() }
    }

    private val json = Json { ignoreUnknownKeys = true }
    private const val AGENT = "Swordfish (https://github.com/ShoalApps/swordfish)"
    private val cache = ConcurrentHashMap<String, Result>()
    private val none = Result(emptyList(), null)

    suspend fun find(http: OkHttpClient, title: String, artist: String, durationSec: Long): Result? = withContext(Dispatchers.IO) {
        val key = "$title|$artist|$durationSec"
        cache[key]?.let { return@withContext it.takeIf { r -> r !== none } }
        val sources: List<() -> Result?> = listOf(
            { betterLyrics(http, title, artist, durationSec) },
            { lrclibExact(http, title, artist, durationSec) },
            { lrclibSearch(http, title, artist, durationSec) },
            { netease(http, title, artist, durationSec) },
        )
        val found = sources.firstNotNullOfOrNull { runCatching(it).getOrNull()?.takeIf { r -> r.synced.isNotEmpty() || !r.plain.isNullOrBlank() } }
        cache[key] = found ?: none
        found
    }

    // ---- Sources ----

    private fun betterLyrics(http: OkHttpClient, title: String, artist: String, durationSec: Long): Result? {
        val url = "https://lyrics-api.boidu.dev/getLyrics".toHttpUrl().newBuilder()
            .addQueryParameter("s", clean(title)).addQueryParameter("a", artist)
            .apply { if (durationSec > 0) addQueryParameter("d", durationSec.toString()) }.build()
        val o = getJson(http, url) as? JsonObject ?: return null
        val ttml = o.str("ttml") ?: return null
        return parseTtml(ttml).takeIf { it.isNotEmpty() }?.let { Result(it, null, "BetterLyrics") }
    }

    private fun lrclibExact(http: OkHttpClient, title: String, artist: String, durationSec: Long): Result? {
        val url = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", clean(title)).addQueryParameter("artist_name", artist)
            .apply { if (durationSec > 0) addQueryParameter("duration", durationSec.toString()) }.build()
        return lrclibResult(getJson(http, url) as? JsonObject ?: return null)
    }

    private fun lrclibSearch(http: OkHttpClient, title: String, artist: String, durationSec: Long): Result? {
        val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", bare(title)).addQueryParameter("artist_name", artist).build()
        val list = (getJson(http, url) as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        // Closest length wins; more than 15 seconds off is probably a different recording.
        val best = list.filter { durationSec <= 0 || abs((it.str("duration")?.toDoubleOrNull() ?: 0.0) - durationSec) < 15 }
            .sortedByDescending { it.str("syncedLyrics") != null }.firstOrNull() ?: return null
        return lrclibResult(best)
    }

    private fun lrclibResult(o: JsonObject): Result? {
        val synced = o.str("syncedLyrics")?.let(::parseLrc).orEmpty()
        val plain = o.str("plainLyrics")
        if (synced.isEmpty() && plain.isNullOrBlank()) return null
        return Result(synced, plain, "LRCLIB")
    }

    private fun netease(http: OkHttpClient, title: String, artist: String, durationSec: Long): Result? {
        val search = "https://music.163.com/api/search/get".toHttpUrl().newBuilder()
            .addQueryParameter("s", "${bare(title)} $artist").addQueryParameter("type", "1").addQueryParameter("limit", "10").build()
        val songs = ((getJson(http, search) as? JsonObject)?.get("result") as? JsonObject)?.get("songs") as? JsonArray ?: return null
        val wanted = bare(title).lowercase().filter { it.isLetterOrDigit() }
        val song = songs.mapNotNull { it as? JsonObject }.firstOrNull { s ->
            val name = s.str("name").orEmpty().lowercase().filter { it.isLetterOrDigit() }
            val ms = s.str("duration")?.toLongOrNull() ?: 0
            name == wanted && (durationSec <= 0 || abs(ms / 1000 - durationSec) <= 4)
        } ?: return null
        val lyric = "https://music.163.com/api/song/lyric".toHttpUrl().newBuilder()
            .addQueryParameter("id", song.str("id")).addQueryParameter("lv", "1").build()
        val lrc = ((getJson(http, lyric) as? JsonObject)?.get("lrc") as? JsonObject)?.str("lyric") ?: return null
        // NetEase adds credit lines ("作词 : …", "作曲 : …") at the top.
        val lines = parseLrc(lrc).filterNot { Regex("""^\s*\S{1,6}\s*[:：]""").containsMatchIn(it.text) && it.atMs < 30_000 }
        return lines.takeIf { it.isNotEmpty() }?.let { Result(it, null, "NetEase Cloud Music") }
    }

    private fun getJson(http: OkHttpClient, url: HttpUrl) =
        http.newCall(Request.Builder().url(url).header("User-Agent", AGENT).build()).execute().use { r ->
            if (!r.isSuccessful) null else runCatching { json.parseToJsonElement(r.body!!.string()) }.getOrNull()
        }

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString || it.content != "null" }?.content?.takeIf { it.isNotBlank() }

    // ---- Formats ----

    /** "[01:23.45] words" lines into times and text. */
    fun parseLrc(lrc: String): List<Line> = lrc.lines().mapNotNull { line ->
        val m = Regex("""^\[(\d+):(\d+(?:\.\d+)?)]\s*(.*)$""").find(line.trim()) ?: return@mapNotNull null
        Line(m.groupValues[1].toLong() * 60_000 + (m.groupValues[2].toDouble() * 1000).toLong(), m.groupValues[3])
    }

    /** Apple-style TTML: <p begin end> lines holding <span begin end> words. */
    fun parseTtml(ttml: String): List<Line> = Regex("""<p\b([^>]*)>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL).findAll(ttml).mapNotNull { p ->
        val begin = time(Regex("""begin="([^"]+)"""").find(p.groupValues[1])?.groupValues?.get(1)) ?: return@mapNotNull null
        val inner = p.groupValues[2]
        val words = Regex("""<span\b[^>]*begin="([^"]+)"[^>]*end="([^"]+)"[^>]*>([^<]*)</span>(\s?)""").findAll(inner).mapNotNull { w ->
            val s = time(w.groupValues[1]) ?: return@mapNotNull null
            val e = time(w.groupValues[2]) ?: s
            Word(s, e, unescape(w.groupValues[3]) + w.groupValues[4])
        }.toList()
        val text = if (words.isNotEmpty()) words.joinToString("") { it.text }.trim() else unescape(inner.replace(Regex("<[^>]+>"), "")).trim()
        Line(begin, text, words)
    }.toList()

    /** "12.345", "1:02.345" or "00:01:02.345" into milliseconds. */
    private fun time(t: String?): Long? {
        if (t == null) return null
        val parts = t.removeSuffix("s").split(":").map { it.toDoubleOrNull() ?: return null }
        return (parts.fold(0.0) { acc, v -> acc * 60 + v } * 1000).toLong()
    }

    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&#39;", "'")

    /** "Song (Official Video)" and "Song [Lyrics]" are just "Song" to a lyrics database. */
    fun clean(title: String) = title.replace(Regex("""\s*[(\[](official|lyric|audio|video|visualizer|music video|hd|4k|remaster)[^)\]]*[)\]]""", RegexOption.IGNORE_CASE), "").trim()

    private fun bare(title: String) = title.replace(Regex("""\s*[(\[][^)\]]*[)\]]"""), "").trim()
}
