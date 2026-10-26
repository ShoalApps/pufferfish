package io.github.shoalapps.pufferfish.player

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import io.github.shoalapps.pufferfish.MainActivity
import io.github.shoalapps.pufferfish.data.Music
import io.github.shoalapps.pufferfish.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap

/** The queue's songs by id, and their resolved stream addresses (which last a few hours). */
object Queue {
    val tracks = ConcurrentHashMap<String, Track>()
    private val streams = ConcurrentHashMap<String, Pair<Long, String>>()
    /** Songs YouTube suggests after each one, for the radio that keeps playing after the queue. */
    val related = ConcurrentHashMap<String, List<Track>>()

    fun item(t: Track): MediaItem {
        tracks[t.id] = t
        return MediaItem.Builder()
            .setMediaId(t.id)
            .setUri("pufferfish://track/${t.id}")
            .setMediaMetadata(
                MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist)
                    .setArtworkUri(t.art?.let(Uri::parse)).setDurationMs(if (t.durationSec > 0) t.durationSec * 1000 else null)
                    .setIsPlayable(true).setIsBrowsable(false).build(),
            )
            .build()
    }

    /** Called on the loading thread when a song is about to play. */
    fun resolve(id: String): String {
        streams[id]?.let { (at, url) -> if (System.currentTimeMillis() - at < 3 * 3600_000L) return url }
        val track = tracks[id] ?: error("Unknown song")
        val p = runBlocking { Music.playable(track) }
        tracks[id] = p.track
        related[id] = p.relatedNext
        streams[id] = System.currentTimeMillis() to p.audioUrl
        return p.audioUrl
    }
}

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
        val resolving = ResolvingDataSource.Factory(http) { spec ->
            if (spec.uri.scheme == "pufferfish") spec.withUri(Uri.parse(Queue.resolve(spec.uri.lastPathSegment!!))) else spec
        }
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolving))
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(object : Player.Listener {
            // Keep the home-screen widget showing what's playing.
            override fun onEvents(p: Player, events: Player.Events) {
                if (!events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_IS_PLAYING_CHANGED)) return
                val t = p.currentMediaItem?.mediaId?.let { Queue.tracks[it] }
                val playing = p.isPlaying
                // Recently played, for Home and the library.
                if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) && t != null) io.github.shoalapps.pufferfish.Graph.library.played(t)
                scope.launch(Dispatchers.IO) { io.github.shoalapps.pufferfish.widget.recordNowPlaying(this@PlaybackService, t?.title, t?.artist, t?.art, playing) }
            }

            // Radio: near the end of the queue, add songs YouTube suggests after the current one.
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                if (player.mediaItemCount - player.currentMediaItemIndex > 2) return
                val id = item?.mediaId ?: return
                scope.launch(Dispatchers.IO) {
                    val next = Queue.related[id] ?: runCatching { Queue.resolve(id); Queue.related[id] }.getOrNull() ?: return@launch
                    val known = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.toSet()
                    val fresh = next.filter { it.id !in known && it.durationSec in 1..900 }.take(10)
                    launch(Dispatchers.Main) { player.addMediaItems(fresh.map(Queue::item)) }
                }
            }
        })
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session = MediaSession.Builder(this, player).setSessionActivity(open).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    // Swiping the app away stops the music only if it's paused, like other music players.
    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player ?: return
        if (!p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}
