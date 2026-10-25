package io.github.shoalapps.swordfish

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.shoalapps.swordfish.ui.MusicRoot
import io.github.shoalapps.swordfish.ui.MusicTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The playback notification needs this on Android 13+.
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        Graph.connect()
        setContent {
            val settings by Graph.store.settings.collectAsState()
            MusicTheme(settings) { MusicRoot() }
        }
    }
}
