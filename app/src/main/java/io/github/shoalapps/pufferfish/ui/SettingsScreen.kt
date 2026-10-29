@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.shoalapps.pufferfish.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import io.github.shoalapps.pufferfish.LocalisedIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import io.github.shoalapps.pufferfish.LocalisedText as Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.shoalapps.pufferfish.Graph
import io.github.shoalapps.pufferfish.ShoalManager

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val s by Graph.store.settings.collectAsState()
    val context = LocalContext.current
    fun open(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    BackHandler(onBack = onBack)
    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } }) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()).wrapContentWidth().widthIn(max = 720.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
            item(key = "app-language") { io.github.shoalapps.pufferfish.AppLanguage.SettingsRow() }
            item(key = "support") { SupportCard { open("https://liberapay.com/ShoalApps") } }
            item { Header("Look") }
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text("Theme", style = MaterialTheme.typography.bodyLarge)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        listOf("Follow phone", "Light", "Dark").forEachIndexed { i, l ->
                            SegmentedButton(selected = s.theme == i, onClick = { Graph.store.updateSettings { it.copy(theme = i) } }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(l) }
                        }
                    }
                }
            }
            item { Toggle("Colours from the album art", "With a song playing, the whole app takes its colours from the cover", s.artColours) { v -> Graph.store.updateSettings { it.copy(artColours = v) } } }
            item { Toggle("Wallpaper colours", "Otherwise YouTube Music red, softened (Android 12 and later)", s.dynamicColor) { v -> Graph.store.updateSettings { it.copy(dynamicColor = v) } } }
            item { Header("Updates") }
            item { Link(if (ShoalManager.installed(context)) "Check for updates in Shoal" else "Get Shoal for updates", "Shoal installs and updates all the Shoal apps") { ShoalManager.checkForUpdates(context) } }
            item { Header("About") }
            item { Link("What's new", "Changes in this version") { io.github.shoalapps.pufferfish.WhatsNew.open() } }
            if (io.github.shoalapps.pufferfish.Telemetry.available) item { Toggle("Send error reports", "Anonymous crash and error reports help fix bugs. Never your accounts or history.", io.github.shoalapps.pufferfish.Telemetry.enabled) { v -> io.github.shoalapps.pufferfish.Telemetry.setEnabled(context, v) } }
            item { Link("Privacy policy", "What Pufferfish sends, and to whom") { open("https://shoalapps.github.io/privacy.html") } }
            item { Link("Legal", "Licences, trademarks and notices") { open("https://shoalapps.github.io/legal.html") } }
            item { Link("Source code", "github.com/ShoalApps/pufferfish · GPL-3.0") { open("https://github.com/ShoalApps/pufferfish") } }
            item { Link("NewPipeExtractor", "YouTube Music search and streams · GPL-3.0") { open("https://github.com/TeamNewPipe/NewPipeExtractor") } }
            item { Link("Lyrics", "BetterLyrics, LRCLIB and NetEase Cloud Music") { open("https://lrclib.net") } }
            item { Link("MaterialKolor", "Colours from album art · MIT") { open("https://github.com/jordond/materialkolor") } }
            item { Link("PixelPlayer and Meld", "Design ideas for the player · GPL-3.0") { open("https://github.com/PixelPlayerHQ/PixelPlayer") } }
            item {
                Text(
                    "Pufferfish is an independent project, not affiliated with, endorsed by or sponsored by YouTube, Google or any lyrics provider.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
        }
    }
}

@Composable
private fun Header(text: String) = Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 4.dp))

@Composable
private fun Toggle(title: String, body: String, on: Boolean, set: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { set(!on) }.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(on, set)
    }
}

@Composable
private fun Link(title: String, body: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
