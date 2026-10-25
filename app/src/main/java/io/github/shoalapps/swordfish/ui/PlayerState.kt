package io.github.shoalapps.swordfish.ui

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
import io.github.shoalapps.swordfish.Graph
import io.github.shoalapps.swordfish.data.Track
import io.github.shoalapps.swordfish.player.Queue
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
