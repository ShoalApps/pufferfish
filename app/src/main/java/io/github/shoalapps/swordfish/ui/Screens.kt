@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.shoalapps.swordfish.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.shoalapps.swordfish.Graph
import io.github.shoalapps.swordfish.data.Album
import io.github.shoalapps.swordfish.data.Artist
import io.github.shoalapps.swordfish.data.Lyrics
import io.github.shoalapps.swordfish.data.Music
import io.github.shoalapps.swordfish.data.Track

/** What's on screen besides the now-playing view. */
sealed interface Page {
    data object Search : Page
    data class AlbumPage(val album: Album) : Page
    data class ArtistPage(val artist: Artist) : Page
}

@Composable
fun MusicRoot() {
    val ui = rememberPlayerUi()
    var page by remember { mutableStateOf<Page>(Page.Search) }
    var nowPlaying by remember { mutableStateOf(false) }
    BackHandler(enabled = nowPlaying || page != Page.Search) { if (nowPlaying) nowPlaying = false else page = Page.Search }
    // Like PixelPlayer: with a song playing, the whole app takes its colours from the album art.
    val settings by Graph.store.settings.collectAsState()
    val dark = MaterialTheme.colorScheme.surface.luminance() < .5f
    val art = rememberArtScheme(if (settings.artColours) ui.track?.art else null, dark)
    MaterialTheme(colorScheme = animateScheme(art ?: MaterialTheme.colorScheme)) {
    // A Surface so text everywhere gets the theme's colour.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) { Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (val p = page) {
                    Page.Search -> SearchScreen(onAlbum = { page = Page.AlbumPage(it) }, onArtist = { page = Page.ArtistPage(it) })
                    is Page.AlbumPage -> CollectionScreen(p.album.title, p.album.artist, p.album.art, onBack = { page = Page.Search }) { Music.albumTracks(p.album.url).second }
                    is Page.ArtistPage -> CollectionScreen(p.artist.name, "Artist", p.artist.art, round = true, onBack = { page = Page.Search }) { Music.artistTracks(p.artist.url).second }
                }
            }
            if (ui.item != null) MiniPlayer(ui) { nowPlaying = true }
        }
        AnimatedVisibility(nowPlaying, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
            NowPlaying(ui, onClose = { nowPlaying = false })
        }
    } }
    }
}

@Composable
private fun SearchScreen(onAlbum: (Album) -> Unit, onArtist: (Artist) -> Unit) {
    val settings by Graph.store.settings.collectAsState()
    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(0) }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text("Swordfish", style = MaterialTheme.typography.headlineSmallEmphasized, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp))
        OutlinedTextField(
            query, { query = it },
            placeholder = { Text("Songs, albums, artists") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) { submitted = query.trim(); Graph.store.rememberSearch(submitted); keyboard?.hide() } }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        if (submitted.isEmpty()) {
            LazyColumn(contentPadding = PaddingValues(vertical = 12.dp)) {
                if (settings.recentSearches.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.MusicNote, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Search YouTube Music", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                        Text("Plays in the background, with lyrics.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(settings.recentSearches) { r ->
                    Row(Modifier.fillMaxWidth().clickable { query = r; submitted = r }.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(r, modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            return
        }
        PrimaryTabRow(selectedTabIndex = tab, modifier = Modifier.padding(top = 8.dp)) {
            listOf("Songs", "Albums", "Artists").forEachIndexed { i, l -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(l) }) }
        }
        when (tab) {
            0 -> Results(submitted, "songs", { Music.songs(it) }) { list, i, t -> SongRow(t) { playAll(list, i) } }
            1 -> Results(submitted, "albums", { Music.albums(it) }) { _, _, a -> CollectionRow(a.title, a.artist, a.art) { onAlbum(a) } }
            else -> Results(submitted, "artists", { Music.artists(it) }) { _, _, a -> CollectionRow(a.name, "Artist", a.art, round = true) { onArtist(a) } }
        }
    }
}

@Composable
private fun <T> Results(query: String, kind: String, load: suspend (String) -> List<T>, row: @Composable (List<T>, Int, T) -> Unit) {
    val state by produceState<Result<List<T>>?>(null, query, kind) { value = runCatching { load(query) } }
    val s = state
    when {
        s == null -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
        s.isFailure -> Text("Couldn't search right now. Check your connection.", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> {
            val list = s.getOrThrow()
            LazyColumn(Modifier.fillMaxSize().wrapContentWidth().widthIn(max = 760.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                if (list.isEmpty()) item { Text("Nothing found", Modifier.padding(24.dp)) }
                itemsIndexed(list) { i, t -> row(list, i, t) }
            }
        }
    }
}

@Composable
private fun SongRow(t: Track, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(t.art, null, Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentScale = ContentScale.Crop)
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(t.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(t.artist.ifBlank { null }, t.durationSec.takeIf { it > 0 }?.let { formatTime(it * 1000) }).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun CollectionRow(title: String, subtitle: String, art: String?, round: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(art, null, Modifier.size(64.dp).clip(if (round) CircleShape else RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentScale = ContentScale.Crop)
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** An album or artist: big art, a Play button, and the songs. */
@Composable
private fun CollectionScreen(title: String, subtitle: String, art: String?, round: Boolean = false, onBack: () -> Unit, load: suspend () -> List<Track>) {
    val state by produceState<Result<List<Track>>?>(null, title) { value = runCatching { load() } }
    Scaffold(topBar = { TopAppBar(title = {}, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } }) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()).wrapContentWidth().widthIn(max = 760.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(art, null, Modifier.size(220.dp).clip(if (round) CircleShape else RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentScale = ContentScale.Crop)
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val tracks = state?.getOrNull()
                    if (!tracks.isNullOrEmpty()) {
                        FilledIconButton(onClick = { playAll(tracks, 0) }, modifier = Modifier.padding(top = 12.dp).size(64.dp)) { Icon(Icons.Rounded.PlayArrow, "Play all", Modifier.size(36.dp)) }
                    }
                }
            }
            val s = state
            when {
                s == null -> item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { LoadingIndicator() } }
                s.isFailure -> item { Text("Couldn't load this right now.", Modifier.padding(24.dp)) }
                else -> { val list = s.getOrThrow(); itemsIndexed(list) { i, t -> SongRow(t) { playAll(list, i) } } }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun MiniPlayer(ui: PlayerUi, onOpen: () -> Unit) {
    val t = ui.track
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(Modifier.navigationBarsPadding()) {
            androidx.compose.material3.LinearProgressIndicator(
                progress = { if (ui.durationMs > 0) (ui.positionMs.toFloat() / ui.durationMs).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
            )
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(t?.art, null, Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(t?.title.orEmpty(), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t?.artist.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                IconButton(onClick = { Graph.controller?.let { if (it.isPlaying) it.pause() else it.play() } }) {
                    Icon(if (ui.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (ui.playing) "Pause" else "Play")
                }
                IconButton(onClick = { Graph.controller?.seekToNext() }, enabled = ui.hasNext) { Icon(Icons.Rounded.SkipNext, "Next") }
            }
        }
    }
}
