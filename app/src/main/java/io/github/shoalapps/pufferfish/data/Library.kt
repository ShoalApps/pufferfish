package io.github.shoalapps.pufferfish.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Playlist(val id: String, val name: String, val tracks: List<Track> = emptyList(), val created: Long = System.currentTimeMillis())

@Serializable
data class Played(val track: Track, val at: Long)

@Serializable
data class LibraryData(
    val liked: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    /** Newest first, capped. */
    val history: List<Played> = emptyList(),
)

/** Your library, kept on the phone: liked songs, saved albums and artists, playlists and what you've played. */
class Library(context: Context) {
    private val file = java.io.File(context.filesDir, "library.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _data = MutableStateFlow(runCatching { json.decodeFromString<LibraryData>(file.readText()) }.getOrNull() ?: LibraryData())
    val data: StateFlow<LibraryData> = _data

    private fun change(f: (LibraryData) -> LibraryData) {
        _data.update(f)
        val text = json.encodeToString(LibraryData.serializer(), _data.value)
        runCatching { val tmp = java.io.File(file.path + ".tmp"); tmp.writeText(text); tmp.renameTo(file) }
    }

    fun isLiked(id: String) = _data.value.liked.any { it.id == id }
    fun toggleLike(t: Track) = change { d -> if (d.liked.any { it.id == t.id }) d.copy(liked = d.liked.filterNot { it.id == t.id }) else d.copy(liked = listOf(t) + d.liked) }

    fun isSaved(a: Album) = _data.value.albums.any { it.url == a.url }
    fun toggleAlbum(a: Album) = change { d -> if (d.albums.any { it.url == a.url }) d.copy(albums = d.albums.filterNot { it.url == a.url }) else d.copy(albums = listOf(a) + d.albums) }

    fun isFollowing(a: Artist) = _data.value.artists.any { it.url == a.url }
    fun toggleArtist(a: Artist) = change { d -> if (d.artists.any { it.url == a.url }) d.copy(artists = d.artists.filterNot { it.url == a.url }) else d.copy(artists = listOf(a) + d.artists) }

    fun createPlaylist(name: String, first: Track? = null): Playlist {
        val p = Playlist(java.util.UUID.randomUUID().toString(), name.trim().ifBlank { "My playlist #${_data.value.playlists.size + 1}" }, listOfNotNull(first))
        change { it.copy(playlists = listOf(p) + it.playlists) }
        return p
    }
    fun addTo(playlistId: String, t: Track) = change { d -> d.copy(playlists = d.playlists.map { if (it.id == playlistId && it.tracks.none { x -> x.id == t.id }) it.copy(tracks = it.tracks + t) else it }) }
    fun removeFrom(playlistId: String, trackId: String) = change { d -> d.copy(playlists = d.playlists.map { if (it.id == playlistId) it.copy(tracks = it.tracks.filterNot { x -> x.id == trackId }) else it }) }
    fun rename(playlistId: String, name: String) = change { d -> d.copy(playlists = d.playlists.map { if (it.id == playlistId) it.copy(name = name.trim()) else it }) }
    fun delete(playlistId: String) = change { d -> d.copy(playlists = d.playlists.filterNot { it.id == playlistId }) }

    fun played(t: Track) = change { d -> d.copy(history = (listOf(Played(t, System.currentTimeMillis())) + d.history.filterNot { it.track.id == t.id }).take(300)) }
    fun clearHistory() = change { it.copy(history = emptyList()) }

    /** Artists you play most, for Home. */
    fun topArtists(limit: Int = 8): List<Pair<String, String?>> = _data.value.history
        .groupBy { it.track.artist }.filterKeys { it.isNotBlank() }
        .entries.sortedByDescending { it.value.size }.take(limit)
        .map { (name, plays) -> name to plays.first().track.artistUrl }
}
