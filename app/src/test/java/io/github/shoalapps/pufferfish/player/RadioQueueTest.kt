package io.github.shoalapps.pufferfish.player

import io.github.shoalapps.pufferfish.data.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class RadioQueueTest {
    private fun track(id: String, seconds: Long = 180) = Track(id, "https://example.com/$id", id, "artist", null, null, seconds)

    @Test fun discardsResultsWhenSongOrQueueHasChanged() {
        val candidates = listOf(track("next"))
        assertEquals(emptyList<Track>(), RadioQueue.select("old", "new", 1, emptySet(), candidates))
        assertEquals(emptyList<Track>(), RadioQueue.select("old", "old", 3, emptySet(), candidates))
        assertEquals(emptyList<Track>(), RadioQueue.select("old", null, 0, emptySet(), candidates))
    }

    @Test fun keepsUniqueShortTracksNotAlreadyQueuedAndLimitsResults() {
        val candidates = listOf(track("existing"), track("new"), track("new"), track("long", 901), track("unknown", 0)) + (1..15).map { track("n$it") }
        val selected = RadioQueue.select("old", "old", 2, setOf("existing"), candidates)
        assertEquals(listOf("new") + (1..9).map { "n$it" }, selected.map { it.id })
    }
}
