package io.github.shoalapps.pufferfish.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import io.github.shoalapps.pufferfish.Graph
import io.github.shoalapps.pufferfish.data.Track
import io.github.shoalapps.pufferfish.player.Queue
import kotlinx.coroutines.delay

/** What the playback service is doing, kept up to date for the screens. */
class PlayerUi {
    var item by mutableStateOf<MediaItem?>(null)
    var playing by mutableStateOf(false)
    var buffering by mutableStateOf(false)
    var positionMs by mutableLongStateOf(0)
    var durationMs by mutableLongStateOf(0)
    var hasNext by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var shuffle by mutableStateOf(false)
    var repeat by mutableStateOf(Player.REPEAT_MODE_OFF)
    /** Bumped when the queue changes, so the queue sheet refreshes. */
    var queueVersion by mutableStateOf(0)
    val track: Track? get() = item?.mediaId?.let { Queue.tracks[it] }
}

@Composable
fun rememberPlayerUi(): PlayerUi {
    val ui = remember { PlayerUi() }
    val c = Graph.controller
    DisposableEffect(c) {
        if (c == null) return@DisposableEffect onDispose {}
        fun sync() {
            ui.item = c.currentMediaItem
            ui.playing = c.isPlaying
            ui.buffering = c.playbackState == Player.STATE_BUFFERING
            ui.durationMs = c.duration.coerceAtLeast(0)
            ui.hasNext = c.hasNextMediaItem()
            ui.error = c.playerError?.let { "Couldn't play this song. Skipping…" }
            ui.shuffle = c.shuffleModeEnabled
            ui.repeat = c.repeatMode
            ui.queueVersion++
        }
        val l = object : Player.Listener { override fun onEvents(player: Player, events: Player.Events) = sync() }
        c.addListener(l)
        sync()
        onDispose { c.removeListener(l) }
    }
    LaunchedEffect(c) {
        while (c != null) {
            ui.positionMs = c.currentPosition
            delay(250)
        }
    }
    return ui
}

/** Plays a list of songs starting from one of them. */
fun playAll(tracks: List<Track>, start: Int) {
    val c = Graph.controller ?: return
    c.setMediaItems(tracks.map(Queue::item), start, 0)
    c.prepare()
    c.play()
}

fun formatTime(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

/** Plays a list in shuffled order. */
fun playShuffled(tracks: List<Track>) {
    if (tracks.isEmpty()) return
    val c = Graph.controller ?: return
    c.setMediaItems(tracks.shuffled().map(Queue::item), 0, 0)
    c.shuffleModeEnabled = false
    c.prepare()
    c.play()
}

/** Puts a song straight after the one playing. */
fun playNext(t: Track) {
    val c = Graph.controller ?: return
    if (c.mediaItemCount == 0) { playAll(listOf(t), 0); return }
    c.addMediaItem((c.currentMediaItemIndex + 1).coerceAtMost(c.mediaItemCount), Queue.item(t))
}

/** Adds a song to the end of the queue. */
fun addToQueue(t: Track) {
    val c = Graph.controller ?: return
    if (c.mediaItemCount == 0) { playAll(listOf(t), 0); return }
    c.addMediaItem(Queue.item(t))
}

/** Starts a radio from a song: it, then songs like it. */
suspend fun startRadio(t: Track) {
    val more = runCatching { io.github.shoalapps.pufferfish.data.Music.related(t) }.getOrDefault(emptyList())
    playAll(listOf(t) + more.take(25), 0)
}
