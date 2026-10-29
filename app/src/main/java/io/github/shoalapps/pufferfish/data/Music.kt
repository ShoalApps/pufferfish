package io.github.shoalapps.pufferfish.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

@kotlinx.serialization.Serializable
data class Track(val id: String, val url: String, val title: String, val artist: String, val artistUrl: String?, val art: String?, val durationSec: Long)
@kotlinx.serialization.Serializable
data class Album(val url: String, val title: String, val artist: String, val art: String?)
@kotlinx.serialization.Serializable
data class Artist(val url: String, val name: String, val art: String?)

/** A track ready to play: its audio stream and full details. */
data class Playable(val track: Track, val audioUrl: String, val mime: String, val relatedNext: List<Track>)

/** YouTube Music through NewPipeExtractor (GPL-3.0): search, albums, artists and audio streams. */
object Music {
    private val yt get() = ServiceList.YouTube

    enum class Kind(val filter: String, val label: String) {
        SONGS(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS, "Songs"),
        ALBUMS(YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS, "Albums"),
        ARTISTS(YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS, "Artists"),
        VIDEOS(YoutubeSearchQueryHandlerFactory.MUSIC_VIDEOS, "Videos"),
    }

    suspend fun search(query: String, kind: Kind): List<InfoItem> = withContext(Dispatchers.IO) {
        val handler = yt.searchQHFactory.fromQuery(query, listOf(kind.filter), "")
        SearchInfo.getInfo(yt, handler).relatedItems
    }

    suspend fun songs(query: String) = search(query, Kind.SONGS).filterIsInstance<StreamInfoItem>().map(::track)
    suspend fun albums(query: String) = search(query, Kind.ALBUMS).filterIsInstance<PlaylistInfoItem>().map(::album)
    suspend fun artists(query: String) = search(query, Kind.ARTISTS).filterIsInstance<ChannelInfoItem>().map(::artist)

    /** An album's (or playlist's) tracks. */
    suspend fun albumTracks(url: String): Pair<Album, List<Track>> = withContext(Dispatchers.IO) {
        val prefs = io.github.shoalapps.pufferfish.Graph.app.getSharedPreferences("offline-albums", android.content.Context.MODE_PRIVATE)
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        if (!OfflineMode.available()) {
            val album = io.github.shoalapps.pufferfish.Graph.library.data.value.albums.firstOrNull { it.url == url }
                ?: Album(url, "Downloaded album", "", null)
            val ids = prefs.getStringSet(url, emptySet()).orEmpty()
            return@withContext album to io.github.shoalapps.pufferfish.player.Downloads.tracks().filter { it.id in ids }
        }
        val info = PlaylistInfo.getInfo(yt, url)
        val art = best(info.thumbnails)
        val tracks = info.relatedItems.filterIsInstance<StreamInfoItem>().map { track(it).let { t -> if (t.art == null) t.copy(art = art) else t } }
        prefs.edit().putStringSet(url, tracks.map { it.id }.toSet()).apply()
        Album(url, info.name, info.uploaderName.orEmpty(), art) to tracks
    }

    /** An artist's songs (their main channel tab). */
    suspend fun artistTracks(url: String): Pair<Artist, List<Track>> = withContext(Dispatchers.IO) {
        val info = ChannelInfo.getInfo(yt, url)
        // The first channel tab with songs on it; failing that, the artist's songs from search.
        val fromTabs = info.tabs.asSequence().mapNotNull { tab -> runCatching { ChannelTabInfo.getInfo(yt, tab).relatedItems.filterIsInstance<StreamInfoItem>() }.getOrNull()?.takeIf { it.isNotEmpty() } }
            .firstOrNull().orEmpty().map(::track)
        val name = info.name.removeSuffix(" - Topic")
        val tracks = fromTabs.ifEmpty { songs(name).filter { it.artist.equals(name, true) }.ifEmpty { songs(name) } }
        Artist(url, name, best(info.avatars)) to tracks
    }

    /** Resolves a track to its best audio stream (AAC first, which every phone plays). */
    suspend fun playable(t: Track): Playable = withContext(Dispatchers.IO) {
        val info = StreamInfo.getInfo(yt, t.url)
        val audio = info.audioStreams.filter { it.isUrl }
            .sortedWith(compareByDescending<AudioStream> { it.format?.mimeType == "audio/mp4" }.thenByDescending { it.averageBitrate })
            .firstOrNull() ?: error("No audio for this song")
        val full = t.copy(
            title = info.name.ifBlank { t.title },
            artist = info.uploaderName?.removeSuffix(" - Topic")?.ifBlank { null } ?: t.artist,
            art = best(info.thumbnails) ?: t.art,
            durationSec = if (info.duration > 0) info.duration else t.durationSec,
        )
        Playable(full, audio.content, audio.format?.mimeType ?: "audio/mp4", info.relatedItems.filterIsInstance<StreamInfoItem>().map(::track))
    }

    fun track(i: StreamInfoItem) = Track(
        id = i.url.substringAfter("v=").substringBefore("&"),
        url = i.url,
        title = i.name,
        artist = i.uploaderName?.removeSuffix(" - Topic").orEmpty(),
        artistUrl = i.uploaderUrl,
        art = best(i.thumbnails),
        durationSec = i.duration,
    )

    fun album(i: PlaylistInfoItem) = Album(i.url, i.name, i.uploaderName.orEmpty(), best(i.thumbnails))
    fun artist(i: ChannelInfoItem) = Artist(i.url, i.name, best(i.thumbnails))

    /** YouTube's music chart for your country (NewPipe's trending-music kiosk). */
    suspend fun charts(): List<Track> = withContext(Dispatchers.IO) {
        val list = yt.kioskList
        val id = list.availableKiosks.firstOrNull { it.contains("music", true) } ?: return@withContext emptyList()
        val url = list.getListLinkHandlerFactoryByType(id).fromId(id).url
        org.schabi.newpipe.extractor.kiosk.KioskInfo.getInfo(yt, url).relatedItems.filterIsInstance<StreamInfoItem>().map(::track)
    }

    /** Playlists for a mood or genre ("Chill", "Workout"…), for Search's Browse cards. */
    suspend fun playlists(query: String) = search(query, Kind.ALBUMS).filterIsInstance<PlaylistInfoItem>().map(::album) +
        runCatching { SearchInfo.getInfo(yt, yt.searchQHFactory.fromQuery(query, listOf(YoutubeSearchQueryHandlerFactory.MUSIC_PLAYLISTS), "")).relatedItems }
            .getOrDefault(emptyList()).filterIsInstance<PlaylistInfoItem>().map(::album)

    /** Songs YouTube suggests after this one ("More like…" and song radio). */
    suspend fun related(t: Track): List<Track> = withContext(Dispatchers.IO) {
        StreamInfo.getInfo(yt, t.url).relatedItems.filterIsInstance<StreamInfoItem>().map(::track).filter { it.durationSec in 1..900 }
    }

    /** The largest image; YouTube Music art comes square at many sizes. */
    fun best(images: List<Image>?): String? = images?.maxByOrNull { it.height * it.width }?.url?.let { u ->
        // Ask googleusercontent art for a large square.
        if (u.contains("googleusercontent.com") && u.contains("=w")) u.substringBefore("=w") + "=w800-h800-l90-rj" else u
    }
}
