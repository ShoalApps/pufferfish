package io.github.shoalapps.pufferfish

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.shoalapps.pufferfish.ui.MusicRoot
import io.github.shoalapps.pufferfish.ui.MusicTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(base: android.content.Context) { super.attachBaseContext(io.github.shoalapps.pufferfish.AppLanguage.wrap(base)) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Telemetry.askOnce(this)
        enableEdgeToEdge()
        // The playback notification needs this on Android 13+.
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        Graph.connect()
        setContent {
            val settings by Graph.store.settings.collectAsState()
            MusicTheme(settings) { WhatsNew.Host(); MusicRoot() }
        }
    }
}
