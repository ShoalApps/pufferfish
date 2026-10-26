package io.github.shoalapps.pufferfish.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale

/** Something on a YouTube Music home shelf. */
sealed interface HomeItem {
    data class Song(val track: Track) : HomeItem
    /** An album or a playlist; both open as a list of songs. */
    data class Collection(val album: Album, val subtitle: String, val isPlaylist: Boolean) : HomeItem
    data class Person(val artist: Artist) : HomeItem
}

data class HomeShelf(val title: String, val items: List<HomeItem>)

/**
 * YouTube Music's own home page (Quick picks, hits, new releases and so on), read from the same
 * public page music.youtube.com shows a visitor, in the phone's language and country.
 */
object YtmHome {
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var cached: Pair<Long, List<HomeShelf>>? = null

    /** The shelves, kept for half an hour so moving around the app doesn't refetch them. */
    suspend fun shelves(http: OkHttpClient): List<HomeShelf> = withContext(Dispatchers.IO) {
        cached?.let { (at, list) -> if (System.currentTimeMillis() - at < 30 * 60_000) return@withContext list }
        val locale = Locale.getDefault()
        val body = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", "1.20251020.01.00")
                    put("hl", locale.toLanguageTag())
                    put("gl", locale.country.ifEmpty { "US" })
                }
            }
            put("browseId", "FEmusic_home")
        }.toString()
        val req = Request.Builder().url("https://music.youtube.com/youtubei/v1/browse?prettyPrint=false")
            .header("Origin", "https://music.youtube.com")
            .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36")
            .post(body.toRequestBody("application/json".toMediaType())).build()
        val text = http.newCall(req).execute().use { r -> if (!r.isSuccessful) error("YouTube Music said ${r.code}"); r.body?.string().orEmpty() }
        val list = parse(json.parseToJsonElement(text).jsonObj)
        cached = System.currentTimeMillis() to list
        list
    }

    internal fun parse(root: JsonObject?): List<HomeShelf> {
        val sections = root?.path("contents", "singleColumnBrowseResultsRenderer")?.get("tabs")?.arr?.firstOrNull()?.jsonObj
            ?.path("tabRenderer", "content", "sectionListRenderer")?.get("contents")?.arr.orEmpty()
        return sections.mapNotNull { s ->
            val shelf = s.jsonObj?.get("musicCarouselShelfRenderer")?.jsonObj ?: return@mapNotNull null
            val title = runs(shelf.path("header", "musicCarouselShelfBasicHeaderRenderer", "title"))
            val items = shelf["contents"]?.arr.orEmpty().mapNotNull { item(it.jsonObj) }
            if (title.isBlank() || items.isEmpty()) null else HomeShelf(title, items)
        }
    }

    private fun item(o: JsonObject?): HomeItem? {
        o ?: return null
        o["musicResponsiveListItemRenderer"]?.jsonObj?.let { r ->
            val cols = r["flexColumns"]?.arr.orEmpty().map { it.jsonObj?.path("musicResponsiveListItemFlexColumnRenderer", "text") }
            val titleRun = cols.getOrNull(0)?.get("runs")?.arr?.firstOrNull()?.jsonObj
            val id = r.path("playlistItemData")?.str("videoId") ?: titleRun?.path("navigationEndpoint", "watchEndpoint")?.str("videoId") ?: return null
            val (artist, artistUrl) = artistOf(cols.getOrNull(1))
            return HomeItem.Song(Track(id, "https://www.youtube.com/watch?v=$id", titleRun?.str("text").orEmpty(), artist, artistUrl, art(r.path("thumbnail", "musicThumbnailRenderer")), 0))
        }
        val r = o["musicTwoRowItemRenderer"]?.jsonObj ?: return null
        val title = runs(r["title"]?.jsonObj)
        val subtitle = runs(r["subtitle"]?.jsonObj)
        val pic = art(r.path("thumbnailRenderer", "musicThumbnailRenderer"))
        val nav = r["navigationEndpoint"]?.jsonObj
        nav?.path("watchEndpoint")?.str("videoId")?.let { id ->
            val (artist, artistUrl) = artistOf(r["subtitle"]?.jsonObj)
            return HomeItem.Song(Track(id, "https://www.youtube.com/watch?v=$id", title, artist, artistUrl, pic, 0))
        }
        val browse = nav?.path("browseEndpoint") ?: return null
        val browseId = browse.str("browseId") ?: return null
        return when (browse.path("browseEndpointContextSupportedConfigs", "browseEndpointContextMusicConfig")?.str("pageType")) {
            "MUSIC_PAGE_TYPE_ARTIST", "MUSIC_PAGE_TYPE_USER_CHANNEL" -> HomeItem.Person(Artist("https://www.youtube.com/channel/$browseId", title, pic))
            "MUSIC_PAGE_TYPE_ALBUM" -> {
                // Albums are opened by their playlist, which the play button carries.
                val list = r.path("thumbnailOverlay", "musicItemThumbnailOverlayRenderer", "content", "musicPlayButtonRenderer", "playNavigationEndpoint", "watchPlaylistEndpoint")?.str("playlistId") ?: return null
                HomeItem.Collection(Album("https://www.youtube.com/playlist?list=$list", title, subtitle, pic), subtitle, isPlaylist = false)
            }
            "MUSIC_PAGE_TYPE_PLAYLIST" -> HomeItem.Collection(Album("https://www.youtube.com/playlist?list=${browseId.removePrefix("VL")}", title, subtitle, pic), subtitle, isPlaylist = true)
            else -> null
        }
    }

    /** The first artist in a line of runs ("Song • Artist • Album"), with their channel. */
    private fun artistOf(text: JsonObject?): Pair<String, String?> {
        val runs = text?.get("runs")?.arr.orEmpty().mapNotNull { it.jsonObj }
        val linked = runs.firstOrNull { it.path("navigationEndpoint", "browseEndpoint")?.str("browseId")?.startsWith("UC") == true }
        val name = linked?.str("text") ?: runs.firstOrNull { it.str("text")?.trim() !in setOf("•", "", "Song", "Video", "EP", "Album", "Single") }?.str("text")
        return name.orEmpty() to linked?.path("navigationEndpoint", "browseEndpoint")?.str("browseId")?.let { "https://www.youtube.com/channel/$it" }
    }

    /** The largest picture, asked for at a size that stays sharp on big cards. */
    private fun art(thumb: JsonObject?): String? =
        thumb?.path("thumbnail")?.get("thumbnails")?.arr?.lastOrNull()?.jsonObj?.str("url")
            ?.replace(Regex("=w\\d+-h\\d+"), "=w544-h544")

    private fun runs(o: JsonObject?) = o?.get("runs")?.arr.orEmpty().joinToString("") { it.jsonObj?.str("text").orEmpty() }

    private val JsonElement.jsonObj get() = this as? JsonObject
    private val JsonElement.arr get() = this as? JsonArray
    private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.path(vararg keys: String): JsonObject? = keys.fold(this as JsonObject?) { o, k -> o?.get(k) as? JsonObject }
}
