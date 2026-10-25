package io.github.shoalapps.swordfish.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Lyrics from LRCLIB (lrclib.net), a free, open, community-run lyrics database. */
object Lyrics {
    data class Line(val atMs: Long, val text: String)
    data class Result(val synced: List<Line>, val plain: String?)

    private val json = Json { ignoreUnknownKeys = true }

    /** An exact match first; then LRCLIB's search, with brackets stripped, for near matches. */
    suspend fun find(http: OkHttpClient, title: String, artist: String, durationSec: Long): Result? =
        exact(http, title, artist, durationSec) ?: search(http, title, artist, durationSec)

    private suspend fun search(http: OkHttpClient, title: String, artist: String, durationSec: Long): Result? = withContext(Dispatchers.IO) {
        val bare = title.replace(Regex("""\s*[(\[][^)\]]*[)\]]"""), "").trim()
        val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder().addQueryParameter("track_name", bare).addQueryParameter("artist_name", artist).build()
        val req = Request.Builder().url(url).header("User-Agent", "Swordfish (https://github.com/ShoalApps/swordfish)").build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val list = json.parseToJsonElement(resp.body!!.string()) as? kotlinx.serialization.json.JsonArray ?: return@use null
            // Closest length wins; songs more than 15 seconds off are probably a different recording.
            val best = list.mapNotNull { it as? JsonObject }
                .filter { durationSec <= 0 || kotlin.math.abs(((it["duration"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0) - durationSec) < 15 }
                .sortedByDescending { (it["syncedLyrics"] as? JsonPrimitive)?.isString == true }
                .firstOrNull() ?: return@use null
            val synced = (best["syncedLyrics"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val plain = (best["plainLyrics"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            Result(synced?.let(::parseLrc).orEmpty(), plain)
        }
    }

    private suspend fun exact(http: OkHttpClient, title: String, artist: String, durationSec: Long): Result? = withContext(Dispatchers.IO) {
        val url = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", clean(title))
            .addQueryParameter("artist_name", artist)
            .apply { if (durationSec > 0) addQueryParameter("duration", durationSec.toString()) }
            .build()
        val req = Request.Builder().url(url).header("User-Agent", "Swordfish (https://github.com/ShoalApps/swordfish)").build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val o = json.parseToJsonElement(resp.body!!.string()) as? JsonObject ?: return@use null
            val synced = (o["syncedLyrics"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val plain = (o["plainLyrics"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            Result(synced?.let(::parseLrc).orEmpty(), plain)
        }
    }

    /** "[01:23.45] words" lines into times and text. */
    fun parseLrc(lrc: String): List<Line> = lrc.lines().mapNotNull { line ->
        val m = Regex("""^\[(\d+):(\d+(?:\.\d+)?)]\s*(.*)$""").find(line.trim()) ?: return@mapNotNull null
        val ms = m.groupValues[1].toLong() * 60_000 + (m.groupValues[2].toDouble() * 1000).toLong()
        Line(ms, m.groupValues[3])
    }

    /** "Song (Official Video)" and "Song [Lyrics]" are just "Song" to a lyrics database. */
    fun clean(title: String) = title.replace(Regex("""\s*[(\[](official|lyric|audio|video|visualizer|music video|hd|4k|remaster)[^)\]]*[)\]]""", RegexOption.IGNORE_CASE), "").trim()
}
