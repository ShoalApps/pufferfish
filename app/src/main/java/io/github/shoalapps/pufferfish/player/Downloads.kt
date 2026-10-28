package io.github.shoalapps.pufferfish.player

import android.app.Notification
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import io.github.shoalapps.pufferfish.data.Music
import io.github.shoalapps.pufferfish.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.Executors

/**
 * Songs saved for offline listening. Each is stored under its song ID, and playback reads the
 * store first, so a downloaded song plays with no connection. Ordinary listening isn't stored.
 */
@OptIn(UnstableApi::class)
object Downloads {
    private const val CHANNEL = "downloads"
    private lateinit var app: Context
    lateinit var cache: SimpleCache
        private set
    lateinit var manager: DownloadManager
        private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** IDs of songs fully downloaded, and those on their way. */
    private val state = DownloadState()
    val done: StateFlow<Set<String>> = state.done
    val pending: StateFlow<Set<String>> = state.pending

    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        val db = StandaloneDatabaseProvider(app)
        cache = SimpleCache(File(app.filesDir, "downloads"), NoOpCacheEvictor(), db)
        val http = DefaultHttpDataSource.Factory().setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36").setAllowCrossProtocolRedirects(true)
        manager = DownloadManager(app, db, cache, http, Executors.newFixedThreadPool(2)).apply {
            maxParallelDownloads = 2
            requirements = Requirements(Requirements.NETWORK)
            addListener(object : DownloadManager.Listener {
                override fun onDownloadChanged(m: DownloadManager, d: Download, e: Exception?) = refresh()
                override fun onDownloadRemoved(m: DownloadManager, d: Download) = refresh()
                override fun onInitialized(m: DownloadManager) = refresh()
            })
        }
        refresh()
    }

    private fun refresh() {
        val done = mutableSetOf<String>(); val pending = mutableSetOf<String>()
        runCatching {
            manager.downloadIndex.getDownloads().use { c ->
                while (c.moveToNext()) when (c.download.state) {
                    Download.STATE_COMPLETED -> done += c.download.request.id
                    Download.STATE_QUEUED, Download.STATE_DOWNLOADING, Download.STATE_RESTARTING -> pending += c.download.request.id
                }
            }
        }.onSuccess { state.reconcile(done, pending) }
    }

    /** Playback's data source: the download store first, then the network (nothing written). */
    fun playbackFactory(upstream: androidx.media3.datasource.DataSource.Factory): CacheDataSource.Factory =
        CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(upstream).setCacheWriteDataSinkFactory(null)
            .setCacheKeyFactory { spec -> spec.uri.takeIf { it.scheme == Queue.SCHEME }?.lastPathSegment ?: spec.key ?: spec.uri.toString() }

    /** Saves a song: finds its audio address, then hands it to the download service. */
    fun add(t: Track) {
        val token = state.reserve(t.id) ?: return
        scope.launch {
            runCatching {
                Queue.tracks[t.id] = t
                val url = Queue.resolve(t.id)
                val req = DownloadRequest.Builder(t.id, android.net.Uri.parse(url)).setCustomCacheKey(t.id)
                    .setData(kotlinx.serialization.json.Json.encodeToString(Track.serializer(), t).toByteArray()).build()
                DownloadService.sendAddDownload(app, PufferDownloadService::class.java, req, false)
            }.onFailure { state.release(t.id, token) }.onSuccess {
                // If the service never acknowledges the request, allow a retry.
                delay(60_000)
                state.release(t.id, token)
            }
        }
    }

    fun addAll(tracks: List<Track>) = tracks.forEach(::add)

    fun remove(id: String) = DownloadService.sendRemoveDownload(app, PufferDownloadService::class.java, id, false)

    /** The downloaded songs, with their details, for the library. */
    fun tracks(): List<Track> = runCatching {
        val out = mutableListOf<Track>()
        manager.downloadIndex.getDownloads(Download.STATE_COMPLETED).use { c ->
            while (c.moveToNext()) runCatching { out += kotlinx.serialization.json.Json.decodeFromString(Track.serializer(), String(c.download.request.data)) }
        }
        out
    }.getOrDefault(emptyList())

    fun notification(context: Context, downloads: List<Download>): Notification =
        DownloadNotificationHelper(context, CHANNEL).buildProgressNotification(context, android.R.drawable.stat_sys_download, null,
            if (downloads.size == 1) "Downloading 1 song" else "Downloading ${downloads.size} songs", downloads, 0)

    const val CHANNEL_ID = CHANNEL
}

/** Runs downloads in the background with a progress notification. */
@OptIn(UnstableApi::class)
class PufferDownloadService : DownloadService(1001, DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL, Downloads.CHANNEL_ID, io.github.shoalapps.pufferfish.R.string.downloads_channel, 0) {
    override fun getDownloadManager(): DownloadManager { Downloads.init(this); return Downloads.manager }
    override fun getScheduler() = null
    override fun getForegroundNotification(downloads: MutableList<Download>, notMetRequirements: Int) = Downloads.notification(this, downloads)
}
