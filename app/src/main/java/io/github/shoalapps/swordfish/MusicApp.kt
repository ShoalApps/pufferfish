package io.github.shoalapps.swordfish

import android.app.Application
import android.content.ComponentName
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import io.github.shoalapps.swordfish.data.NewPipeDownloader
import io.github.shoalapps.swordfish.data.Store
import io.github.shoalapps.swordfish.player.PlaybackService
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.Locale
import java.util.concurrent.TimeUnit

class MusicApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.app = this
        val locale = Locale.getDefault()
        NewPipe.init(NewPipeDownloader(Graph.http), Localization.fromLocale(locale), ContentCountry(locale.country.ifBlank { "GB" }))
    }
}

object Graph {
    lateinit var app: Application
    val http: OkHttpClient by lazy {
        OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    }
    val store by lazy { Store(app) }

    /** The connection to the playback service, once it's ready. */
    var controller by mutableStateOf<MediaController?>(null)
        private set

    fun connect() {
        if (controller != null) return
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({ controller = runCatching { future.get() }.getOrNull() }, MoreExecutors.directExecutor())
    }
}
