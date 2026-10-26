package io.github.shoalapps.pufferfish

import io.github.shoalapps.pufferfish.data.HomeItem
import io.github.shoalapps.pufferfish.data.YtmHome
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reads a real YouTube Music home page (saved October 2026). */
class YtmHomeTest {
    private val shelves = YtmHome.parse(Json.parseToJsonElement(javaClass.getResource("/ytm-home.json")!!.readText()) as JsonObject)

    @Test fun readsTheShelves() {
        assertEquals(listOf("Quick picks", "All hits", "New releases"), shelves.map { it.title })
    }

    @Test fun quickPicksAreSongsWithArtists() {
        val songs = shelves[0].items.filterIsInstance<HomeItem.Song>()
        assertEquals(shelves[0].items.size, songs.size)
        assertTrue(songs.all { it.track.id.length == 11 && it.track.title.isNotBlank() && it.track.artist.isNotBlank() })
        assertTrue(songs.all { it.track.art?.contains("w544-h544") == true || it.track.art?.contains("i.ytimg.com") == true })
    }

    @Test fun albumsOpenByPlaylist() {
        val albums = shelves[2].items.filterIsInstance<HomeItem.Collection>()
        assertTrue(albums.isNotEmpty())
        assertTrue(albums.all { it.album.url.startsWith("https://www.youtube.com/playlist?list=") })
    }
}
