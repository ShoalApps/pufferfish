package io.github.shoalapps.pufferfish.player

import io.github.shoalapps.pufferfish.data.Track

/** Select recommendations only while they still belong to the active, nearly finished queue. */
internal object RadioQueue {
    fun select(requestedId: String, currentId: String?, remaining: Int, known: Set<String>, candidates: List<Track>): List<Track> {
        if (currentId != requestedId || remaining !in 1..2) return emptyList()
        return candidates.asSequence().filter { it.id !in known && it.durationSec in 1..900 }
            .distinctBy { it.id }.take(10).toList()
    }
}
