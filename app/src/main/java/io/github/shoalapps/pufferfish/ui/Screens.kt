@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.shoalapps.pufferfish.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.shoalapps.pufferfish.Graph
import io.github.shoalapps.pufferfish.data.Album
import io.github.shoalapps.pufferfish.data.Artist
import io.github.shoalapps.pufferfish.data.Music
import io.github.shoalapps.pufferfish.data.Track
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Screens opened on top of a tab. */
sealed interface Page {
    data class AlbumPage(val album: Album) : Page
    data class ArtistPage(val artist: Artist) : Page
    data class PlaylistPage(val id: String) : Page
    data object Liked : Page
    data object Downloaded : Page
    data class Browse(val title: String, val query: String) : Page
    data object Settings : Page
}

/** Navigation shared by every screen. */
class Nav(val open: (Page) -> Unit, val back: () -> Unit, val menu: (Track, String?) -> Unit)

@Composable
fun MusicRoot() {
    val ui = rememberPlayerUi()
    var tab by remember { mutableStateOf(0) }
    val stack = remember { mutableStateListOf<Page>() }
    var nowPlaying by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Pair<Track, String?>?>(null) }
    val nav = remember { Nav(open = { stack.add(it) }, back = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }, menu = { t, pl -> menuFor = t to pl }) }
    BackHandler(enabled = nowPlaying || stack.isNotEmpty() || tab != 0) {
        when { nowPlaying -> nowPlaying = false; stack.isNotEmpty() -> stack.removeAt(stack.lastIndex); else -> tab = 0 }
    }
    // Like PixelPlayer: with a song playing, the whole app takes its colours from the album art.
    val settings by Graph.store.settings.collectAsState()
    val dark = MaterialTheme.colorScheme.surface.luminance() < .5f
    val art = rememberArtScheme(if (settings.artColours) ui.track?.art else null, dark)
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    MaterialTheme(colorScheme = animateScheme(art ?: MaterialTheme.colorScheme)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxSize()) {
                val tabs = listOf(Triple("Home", Icons.Rounded.Home, 0), Triple("Search", Icons.Rounded.Search, 1), Triple("Your Library", Icons.Rounded.LibraryMusic, 2))
                fun select(i: Int) { if (tab == i) stack.clear() else { tab = i; stack.clear() } }
                if (wide) NavigationRail(Modifier.statusBarsPadding()) {
                    Spacer(Modifier.height(12.dp))
                    tabs.forEach { (label, icon, i) -> NavigationRailItem(selected = tab == i && stack.isEmpty(), onClick = { select(i) }, icon = { Icon(icon, null) }, label = { Text(label) }) }
                }
                // Without a bottom bar (tablets), keep the mini player clear of Android's own buttons.
                Column(Modifier.weight(1f).fillMaxSize().then(if (wide) Modifier.navigationBarsPadding() else Modifier)) {
                    Box(Modifier.weight(1f)) {
                        when (val p = stack.lastOrNull()) {
                            null -> when (tab) {
                                0 -> HomeScreen(nav)
                                1 -> SearchScreen(nav)
                                else -> LibraryScreen(nav)
                            }
                            is Page.AlbumPage -> AlbumScreen(p.album, nav)
                            is Page.ArtistPage -> ArtistScreen(p.artist, nav)
                            is Page.PlaylistPage -> PlaylistScreen(p.id, nav)
                            Page.Liked -> LikedScreen(nav)
                            Page.Downloaded -> DownloadedScreen(nav)
                            is Page.Browse -> BrowseScreen(p.title, p.query, nav)
                            Page.Settings -> SettingsScreen(onBack = nav.back)
                        }
                    }
                    if (ui.item != null) MiniPlayer(ui) { nowPlaying = true }
                    if (!wide) NavigationBar {
                        tabs.forEach { (label, icon, i) -> NavigationBarItem(selected = tab == i, onClick = { select(i) }, icon = { Icon(icon, null) }, label = { Text(label) }) }
                    }
                }
            }
            AnimatedVisibility(nowPlaying, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                NowPlaying(ui, onClose = { nowPlaying = false }, onArtist = { a -> nowPlaying = false; nav.open(Page.ArtistPage(a)) })
            }
            menuFor?.let { (t, playlist) -> TrackMenu(t, playlist, nav) { menuFor = null } }
        }
    }
}

// ---------------- Home ----------------

private fun songs(n: Int) = if (n == 1) "1 song" else "$n songs"

private fun greeting(): String {
    val h = java.time.LocalTime.now().hour
    return when (h) { in 5..11 -> "Good morning"; in 12..17 -> "Good afternoon"; else -> "Good evening" }
}

@Composable
private fun HomeScreen(nav: Nav) {
    val lib by Graph.library.data.collectAsState()
    val scope = rememberCoroutineScope()
    val recent = remember(lib.history) { lib.history.map { it.track }.distinctBy { it.id } }
    val lastTrack = recent.firstOrNull()
    val charts by produceState<List<Track>?>(null) { value = runCatching { Music.charts() }.getOrDefault(emptyList()) }
    val moreLike by produceState<List<Track>?>(null, lastTrack?.id) { value = lastTrack?.let { runCatching { Music.related(it) }.getOrNull() } }
    val perRow = if (LocalConfiguration.current.screenWidthDp >= 840) 4 else 2
    val mixSeeds = remember(lib.history, lib.liked) {
        Graph.library.topArtists().take(4).mapNotNull { (name, _) -> recent.firstOrNull { it.artist == name }?.let { "$name Mix" to it } } +
            listOfNotNull(lib.liked.randomOrNull()?.let { "Liked Songs Mix" to it })
    }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(greeting(), style = MaterialTheme.typography.headlineSmallEmphasized, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { nav.open(Page.Settings) }) { Icon(Icons.Rounded.Settings, "Settings") }
            }
        }
        if (recent.isEmpty()) item {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Icon(Icons.Rounded.MusicNote, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Welcome to Pufferfish", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    Text("Search for a song, album or artist to start. What you play shows up here, with more like it.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else item {
            // Spotify's shortcut grid: your eight most recent, two to a row.
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recent.take(8).chunked(perRow).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { t ->
                            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(10.dp)).clickable { playAll(listOf(t) + recent.filter { it.id != t.id }.take(20), 0) }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AsyncImage(t.art, null, Modifier.size(56.dp), contentScale = ContentScale.Crop)
                                    Text(t.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 10.dp))
                                }
                            }
                        }
                        repeat(perRow - pair.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (recent.size > 2) shelf("Jump back in") { items(recent.take(15), key = { "r" + it.id }) { t -> TrackCard(t) { playAll(listOf(t), 0) } } }
        val top = Graph.library.topArtists()
        if (top.isNotEmpty()) shelf("Your top artists") {
            items(top) { (name, url) ->
                val pic = lib.artists.firstOrNull { it.name.equals(name, true) }?.art ?: recent.firstOrNull { it.artist == name }?.art
                Column(Modifier.width(120.dp).clickable(enabled = url != null) { nav.open(Page.ArtistPage(Artist(url!!, name, pic))) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(pic, null, Modifier.size(112.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentScale = ContentScale.Crop)
                    Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        // Made for you: a mix per favourite artist, and one from your Liked Songs (each starts a radio).
        if (mixSeeds.isNotEmpty()) shelf("Made for you") {
            items(mixSeeds, key = { "mix" + it.first }) { (title, seed) ->
                MixCard(title, seed) { scope.launch { startRadio(seed) } }
            }
        }
        moreLike?.takeIf { it.isNotEmpty() }?.let { list -> shelf("More like ${lastTrack?.title}") { items(list.take(15), key = { "m" + it.id }) { t -> TrackCard(t) { playAll(list, list.indexOf(t)) } } } }
        when (val c = charts) {
            null -> item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { LoadingIndicator() } }
            else -> if (c.isNotEmpty()) shelf("Top songs") { items(c.take(20), key = { "c" + it.id }) { t -> TrackCard(t) { playAll(c, c.indexOf(t)) } } }
        }
        if (lib.playlists.isNotEmpty()) shelf("Your playlists") {
            items(lib.playlists, key = { it.id }) { p -> CoverCard(p.name, songs(p.tracks.size), p.tracks.firstOrNull()?.art) { nav.open(Page.PlaylistPage(p.id)) } }
        }
    }
}

private fun LazyListScope.shelf(title: String, content: LazyListScope.() -> Unit) {
    item(key = "shelf-$title") {
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 10.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) { content() }
        }
    }
}

/** A mix's cover: the seed song's art under a band of colour with the mix's name, like Spotify's daily mixes. */
@Composable
private fun MixCard(title: String, seed: Track, onClick: () -> Unit) {
    Column(Modifier.width(144.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)) {
        Box(Modifier.size(144.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            AsyncImage(seed.art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(MaterialTheme.colorScheme.primary.copy(alpha = .88f)).padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(title, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text("With ${seed.artist} and more", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun TrackCard(t: Track, onClick: () -> Unit) {
    Column(Modifier.width(144.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)) {
        AsyncImage(t.art, null, Modifier.size(144.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentScale = ContentScale.Crop)
        Text(t.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(t.artist, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CoverCard(title: String, subtitle: String, art: String?, round: Boolean = false, onClick: () -> Unit) {
    Column(Modifier.width(144.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick), horizontalAlignment = if (round) Alignment.CenterHorizontally else Alignment.Start) {
        Box(Modifier.size(144.dp).clip(if (round) CircleShape else RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            if (art != null) AsyncImage(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Rounded.MusicNote, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------------- Search ----------------

/** Spotify's Browse cards: a genre or mood, each in its own colour. */
private val browse = listOf(
    "Pop" to 0xFFE13300, "Hip-hop" to 0xFFBC5900, "Rock" to 0xFF006450, "Dance" to 0xFF8400E7, "Indie" to 0xFF608108,
    "Chill" to 0xFF1E3264, "Workout" to 0xFF777777, "Focus" to 0xFF503750, "Party" to 0xFFAF2896, "Sleep" to 0xFF1E3264,
    "R&B" to 0xFFDC148C, "K-pop" to 0xFF148A08, "Country" to 0xFFB49BC8, "Jazz" to 0xFF8D67AB, "Classical" to 0xFF7D4B32, "Eurovision" to 0xFF0D73EC,
)

@Composable
private fun SearchScreen(nav: Nav) {
    val settings by Graph.store.settings.collectAsState()
    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val widthDp = LocalConfiguration.current.screenWidthDp
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text("Search", style = MaterialTheme.typography.headlineSmallEmphasized, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 10.dp))
        TextField(
            query, { query = it; if (it.isBlank()) submitted = "" },
            placeholder = { Text("What do you want to listen to?") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = ""; submitted = "" }) { Icon(Icons.Rounded.Close, "Clear") } },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) { submitted = query.trim(); Graph.store.rememberSearch(submitted); keyboard?.hide() } }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        if (submitted.isEmpty()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 24.dp)) {
                if (settings.recentSearches.isNotEmpty()) {
                    item { Text("Recent searches", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp)) }
                    items(settings.recentSearches.take(5)) { r ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { query = r; submitted = r }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(r, modifier = Modifier.padding(start = 14.dp))
                        }
                    }
                }
                item { Text("Browse all", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 10.dp)) }
                val cols = if (widthDp >= 840) 4 else if (widthDp >= 600) 3 else 2
                items(browse.chunked(cols)) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                        row.forEach { (name, colour) ->
                            // Softened like the rest of Material You: the colour, eased towards the surface.
                            val c = androidx.compose.ui.graphics.lerp(Color(colour), MaterialTheme.colorScheme.surface, .25f)
                            Box(Modifier.weight(1f).height(96.dp).clip(RoundedCornerShape(16.dp)).background(c).clickable { nav.open(Page.Browse(name, "$name music")) }.padding(14.dp)) {
                                Text(name, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            return
        }
        SearchResults(submitted, nav)
    }
}

private data class Results(val songs: List<Track>, val artists: List<Artist>, val albums: List<Album>, val playlists: List<Album>)

@Composable
private fun SearchResults(q: String, nav: Nav) {
    val r by produceState<Result<Results>?>(null, q) {
        value = runCatching {
            coroutineScope {
                val s = async { runCatching { Music.songs(q) }.getOrDefault(emptyList()) }
                val ar = async { runCatching { Music.artists(q) }.getOrDefault(emptyList()) }
                val al = async { runCatching { Music.albums(q) }.getOrDefault(emptyList()) }
                val pl = async { runCatching { Music.playlists(q) }.getOrDefault(emptyList()) }
                Results(s.await(), ar.await(), al.await(), pl.await())
            }
        }
    }
    val res = r?.getOrNull()
    when {
        r == null -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
        res == null || (res.songs.isEmpty() && res.artists.isEmpty() && res.albums.isEmpty()) ->
            Text("Couldn't find anything for \"$q\". Check your connection or try other words.", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> LazyColumn(Modifier.fillMaxSize().wrapContentWidth().widthIn(max = 900.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            // Like Spotify: searching an artist's name puts the artist on top.
            val topArtist = res.artists.firstOrNull()?.takeIf { it.name.equals(q.trim(), true) }
            if (topArtist != null) item {
                Text("Top result", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp))
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { nav.open(Page.ArtistPage(topArtist)) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(topArtist.art, null, Modifier.size(92.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                        Column(Modifier.padding(start = 16.dp).weight(1f)) {
                            Text(topArtist.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text("Artist", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        PlayButton(playing = false) { playAll(res.songs.filter { it.artist.equals(topArtist.name, true) }.ifEmpty { res.songs }, 0) }
                    }
                }
            } else res.songs.firstOrNull()?.let { top ->
                item {
                    Text("Top result", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp))
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { playAll(res.songs, 0) }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(top.art, null, Modifier.size(92.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                                Text(top.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("Song · ${top.artist}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            PlayButton(playing = false) { playAll(res.songs, 0) }
                        }
                    }
                }
            }
            if (res.songs.size > 1) {
                item { SectionTitle("Songs") }
                itemsIndexed(res.songs.drop(1).take(5)) { i, t -> TrackRow(t, nav) { playAll(res.songs, i + 1) } }
            }
            if (res.artists.isNotEmpty()) shelf("Artists") { items(res.artists.take(12)) { a -> CoverCard(a.name, "Artist", a.art, round = true) { nav.open(Page.ArtistPage(a)) } } }
            if (res.albums.isNotEmpty()) shelf("Albums") { items(res.albums.take(12)) { a -> CoverCard(a.title, a.artist, a.art) { nav.open(Page.AlbumPage(a)) } } }
            if (res.playlists.isNotEmpty()) shelf("Playlists") { items(res.playlists.take(12)) { a -> CoverCard(a.title, a.artist, a.art) { nav.open(Page.AlbumPage(a)) } } }
        }
    }
}

@Composable
private fun SectionTitle(t: String) = Text(t, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp))

@Composable
private fun BrowseScreen(title: String, query: String, nav: Nav) {
    val playlists by produceState<List<Album>?>(null, query) { value = runCatching { Music.playlists(query) }.getOrDefault(emptyList()) }
    val songs by produceState<List<Track>?>(null, query) { value = runCatching { Music.songs(query) }.getOrDefault(emptyList()) }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { BackTitle(title, nav) }
        playlists?.takeIf { it.isNotEmpty() }?.let { list -> shelf("Playlists") { items(list.take(15)) { a -> CoverCard(a.title, a.artist, a.art) { nav.open(Page.AlbumPage(a)) } } } }
        when (val s = songs) {
            null -> item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { LoadingIndicator() } }
            else -> { item { SectionTitle("Songs") }; itemsIndexed(s) { i, t -> TrackRow(t, nav) { playAll(s, i) } } }
        }
    }
}

@Composable
private fun BackTitle(title: String, nav: Nav) {
    Row(Modifier.padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = nav.back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        Text(title, style = MaterialTheme.typography.headlineSmallEmphasized, fontWeight = FontWeight.Bold)
    }
}

// ---------------- Your Library ----------------

@Composable
private fun LibraryScreen(nav: Nav) {
    val lib by Graph.library.data.collectAsState()
    var filter by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Your Library", style = MaterialTheme.typography.headlineSmallEmphasized, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { creating = true }) { Icon(Icons.Rounded.Add, "Create playlist") }
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Playlists", "Artists", "Albums", "Downloaded").forEach { f ->
                    FilterChip(selected = filter == f, onClick = { filter = if (filter == f) null else f }, label = { Text(f) }, shape = RoundedCornerShape(50))
                }
            }
        }
        val downloadedCount = io.github.shoalapps.pufferfish.player.Downloads.done.value.size
        if (filter == "Downloaded" || (filter == null && downloadedCount > 0)) item {
            LibraryRow("Downloaded", "On this phone · ${songs(downloadedCount)}", null, downloaded = true) { nav.open(Page.Downloaded) }
        }
        if (filter == null || filter == "Playlists") {
            item { LibraryRow("Liked Songs", "Playlist · ${songs(lib.liked.size)}", null, liked = true) { nav.open(Page.Liked) } }
            items(lib.playlists, key = { it.id }) { p -> LibraryRow(p.name, "Playlist · ${songs(p.tracks.size)}", p.tracks.firstOrNull()?.art) { nav.open(Page.PlaylistPage(p.id)) } }
        }
        if (filter == null || filter == "Artists") items(lib.artists, key = { it.url }) { a -> LibraryRow(a.name, "Artist", a.art, round = true) { nav.open(Page.ArtistPage(a)) } }
        if (filter == null || filter == "Albums") items(lib.albums, key = { it.url }) { a -> LibraryRow(a.title, "Album · ${a.artist}", a.art) { nav.open(Page.AlbumPage(a)) } }
        if (lib.playlists.isEmpty() && lib.artists.isEmpty() && lib.albums.isEmpty()) item {
            Text("Like songs, save albums and follow artists and they'll live here. Tap + to make a playlist.", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (creating) NameDialog("Give your playlist a name", "", onDismiss = { creating = false }) { name -> Graph.library.createPlaylist(name); creating = false }
}

@Composable
private fun LibraryRow(title: String, subtitle: String, art: String?, round: Boolean = false, liked: Boolean = false, downloaded: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        val shape = if (round) CircleShape else RoundedCornerShape(10.dp)
        Box(Modifier.size(64.dp).clip(shape).background(
            if (liked || downloaded) Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)) else Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.surfaceContainerHigh)),
        ), contentAlignment = Alignment.Center) {
            when {
                liked -> Icon(Icons.Rounded.Favorite, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp))
                downloaded -> Icon(Icons.Rounded.DownloadDone, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp))
                art != null -> AsyncImage(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else -> Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------------- Albums, artists, playlists ----------------

/** The header every collection shares: colour wash, cover, title, then actions with a big Play. */
@Composable
private fun CollectionHeader(
    title: String, subtitle: String, art: String?, round: Boolean, nav: Nav, tracks: List<Track>,
    saveIcon: ImageVector? = null, saved: Boolean = false, onSave: (() -> Unit)? = null, extra: (@Composable () -> Unit)? = null,
    cover: (@Composable () -> Unit)? = null, downloadable: Boolean = false,
) {
    val c = Graph.controller
    val ui = rememberPlayerUi()
    val playingThis = tracks.isNotEmpty() && tracks.any { it.id == ui.item?.mediaId } && ui.playing
    Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface)))) {
        Column(Modifier.statusBarsPadding().padding(bottom = 8.dp)) {
            IconButton(onClick = nav.back, modifier = Modifier.padding(4.dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (cover != null) cover() else AsyncImage(art, null, Modifier.size(220.dp).clip(if (round) CircleShape else RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentScale = ContentScale.Crop)
            }
            Text(title, style = MaterialTheme.typography.headlineMediumEmphasized, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onSave != null && saveIcon != null) IconButton(onClick = onSave) {
                    Icon(saveIcon, if (saved) "Saved" else "Save", tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                extra?.invoke()
                if (downloadable && tracks.isNotEmpty()) {
                    // Spotify's download switch for a whole list.
                    val done by io.github.shoalapps.pufferfish.player.Downloads.done.collectAsState()
                    val pending by io.github.shoalapps.pufferfish.player.Downloads.pending.collectAsState()
                    val all = tracks.all { it.id in done }
                    val some = tracks.any { it.id in pending }
                    IconButton(onClick = { if (all) tracks.forEach { io.github.shoalapps.pufferfish.player.Downloads.remove(it.id) } else io.github.shoalapps.pufferfish.player.Downloads.addAll(tracks) }) {
                        if (some && !all) LoadingIndicator(Modifier.size(24.dp))
                        else Icon(if (all) Icons.Rounded.DownloadDone else Icons.Rounded.Download, if (all) "Remove downloads" else "Download", tint = if (all) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { playShuffled(tracks) }, enabled = tracks.isNotEmpty()) { Icon(Icons.Rounded.Shuffle, "Shuffle play") }
                PlayButton(playing = playingThis, big = true) {
                    if (playingThis) c?.pause() else if (tracks.any { it.id == ui.item?.mediaId }) c?.play() else playAll(tracks, 0)
                }
            }
        }
    }
}

@Composable
private fun PlayButton(playing: Boolean, big: Boolean = false, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(if (big) 60.dp else 48.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(if (big) 34.dp else 28.dp))
        }
    }
}

private fun LoadingOr(state: Result<List<Track>>?, content: LazyListScope.(List<Track>) -> Unit): LazyListScope.() -> Unit = {
    when {
        state == null -> item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { LoadingIndicator() } }
        state.isFailure -> item { Text("Couldn't load this right now.", Modifier.padding(24.dp)) }
        else -> content(state.getOrThrow())
    }
}

@Composable
private fun AlbumScreen(album: Album, nav: Nav) {
    val lib by Graph.library.data.collectAsState()
    val state by produceState<Result<List<Track>>?>(null, album.url) { value = runCatching { Music.albumTracks(album.url).second } }
    val tracks = state?.getOrNull().orEmpty()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { CollectionHeader(album.title, album.artist, album.art, false, nav, tracks, Icons.Rounded.Add, lib.albums.any { it.url == album.url }, { Graph.library.toggleAlbum(album) }, downloadable = true) }
        LoadingOr(state) { list -> itemsIndexed(list) { i, t -> TrackRow(t, nav, number = i + 1) { playAll(list, i) } } }()
    }
}

@Composable
private fun ArtistScreen(artist: Artist, nav: Nav) {
    val lib by Graph.library.data.collectAsState()
    val state by produceState<Result<List<Track>>?>(null, artist.url) { value = runCatching { Music.artistTracks(artist.url).second } }
    val tracks = state?.getOrNull().orEmpty()
    val following = lib.artists.any { it.url == artist.url }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            CollectionHeader(artist.name, "Artist", artist.art, true, nav, tracks, extra = {
                androidx.compose.material3.OutlinedButton(onClick = { Graph.library.toggleArtist(artist) }, modifier = Modifier.padding(start = 12.dp)) { Text(if (following) "Following" else "Follow") }
            })
        }
        item { SectionTitle("Popular") }
        LoadingOr(state) { list -> itemsIndexed(list) { i, t -> TrackRow(t, nav, number = i + 1) { playAll(list, i) } } }()
    }
}

@Composable
private fun LikedScreen(nav: Nav) {
    val lib by Graph.library.data.collectAsState()
    val tracks = lib.liked
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            CollectionHeader("Liked Songs", songs(tracks.size), null, false, nav, tracks, downloadable = true, cover = {
                Box(Modifier.size(220.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Favorite, null, Modifier.size(88.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
            })
        }
        if (tracks.isEmpty()) item { Text("Songs you like appear here. Tap the heart on any song.", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        itemsIndexed(tracks, key = { _, t -> t.id }) { i, t -> TrackRow(t, nav) { playAll(tracks, i) } }
    }
}

/** Songs on this phone: they play with no connection. */
@Composable
private fun DownloadedScreen(nav: Nav) {
    val done by io.github.shoalapps.pufferfish.player.Downloads.done.collectAsState()
    val tracks = remember(done) { io.github.shoalapps.pufferfish.player.Downloads.tracks() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            CollectionHeader("Downloaded", "${songs(tracks.size)} · play with no connection", null, false, nav, tracks, cover = {
                Box(Modifier.size(220.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.DownloadDone, null, Modifier.size(88.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
            })
        }
        if (tracks.isEmpty()) item { Text("Download songs, albums or playlists from their menus and they'll play here without a connection.", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        itemsIndexed(tracks, key = { _, t -> t.id }) { i, t -> TrackRow(t, nav) { playAll(tracks, i) } }
    }
}

@Composable
private fun PlaylistScreen(id: String, nav: Nav) {
    val lib by Graph.library.data.collectAsState()
    val p = lib.playlists.firstOrNull { it.id == id } ?: run { LaunchedEffect(Unit) { nav.back() }; return }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            CollectionHeader(p.name, songs(p.tracks.size), p.tracks.firstOrNull()?.art, false, nav, p.tracks, downloadable = true, extra = {
                IconButton(onClick = { renaming = true }) { Icon(Icons.Rounded.Edit, "Rename") }
                IconButton(onClick = { deleting = true }) { Icon(Icons.Rounded.Delete, "Delete playlist") }
            })
        }
        if (p.tracks.isEmpty()) item { Text("Add songs from any song's ⋮ menu.", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        itemsIndexed(p.tracks, key = { _, t -> t.id }) { i, t -> TrackRow(t, nav, playlistId = p.id) { playAll(p.tracks, i) } }
    }
    if (renaming) NameDialog("Rename playlist", p.name, onDismiss = { renaming = false }) { Graph.library.rename(p.id, it); renaming = false }
    if (deleting) AlertDialog(
        onDismissRequest = { deleting = false },
        title = { Text("Delete ${p.name}?") },
        text = { Text("This removes the playlist from your library. The songs stay on YouTube Music.") },
        confirmButton = { TextButton(onClick = { deleting = false; Graph.library.delete(p.id) }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } },
    )
}

// ---------------- Songs ----------------

@Composable
fun TrackRow(t: Track, nav: Nav, number: Int? = null, playlistId: String? = null, onClick: () -> Unit) {
    val lib by Graph.library.data.collectAsState()
    val current = rememberPlayerUi().item?.mediaId == t.id
    val liked = lib.liked.any { it.id == t.id }
    val downloads by io.github.shoalapps.pufferfish.player.Downloads.done.collectAsState()
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (number != null) Text("$number", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(28.dp))
        else AsyncImage(t.art, null, Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentScale = ContentScale.Crop)
        Column(Modifier.padding(start = if (number != null) 4.dp else 12.dp).weight(1f)) {
            Text(t.title, style = MaterialTheme.typography.bodyLarge, color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (liked) Icon(Icons.Rounded.Favorite, "Liked", Modifier.size(13.dp).padding(end = 3.dp), tint = MaterialTheme.colorScheme.primary)
                if (t.id in downloads) Icon(Icons.Rounded.DownloadDone, "Downloaded", Modifier.size(14.dp).padding(end = 3.dp), tint = MaterialTheme.colorScheme.primary)
                Text(listOfNotNull(t.artist.ifBlank { null }, t.durationSec.takeIf { it > 0 }?.let { formatTime(it * 1000) }).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton(onClick = { nav.menu(t, playlistId) }) { Icon(Icons.Rounded.MoreVert, "More") }
    }
}

/** Spotify's song menu. */
@Composable
private fun TrackMenu(t: Track, playlistId: String?, nav: Nav, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lib by Graph.library.data.collectAsState()
    var addTo by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(false) }
    val liked = lib.liked.any { it.id == t.id }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(t.art, null, Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
            Column(Modifier.padding(start = 14.dp)) {
                Text(t.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 8.dp))
        if (!addTo) Column(Modifier.padding(bottom = 24.dp)) {
            MenuItem(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (liked) "Remove from Liked Songs" else "Add to Liked Songs") { Graph.library.toggleLike(t); onDismiss() }
            MenuItem(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist") { addTo = true }
            val downloaded by io.github.shoalapps.pufferfish.player.Downloads.done.collectAsState()
            if (t.id in downloaded) MenuItem(Icons.Rounded.DownloadDone, "Remove download") { io.github.shoalapps.pufferfish.player.Downloads.remove(t.id); onDismiss() }
            else MenuItem(Icons.Rounded.Download, "Download") { io.github.shoalapps.pufferfish.player.Downloads.add(t); onDismiss() }
            if (playlistId != null) MenuItem(Icons.Rounded.RemoveCircleOutline, "Remove from this playlist") { Graph.library.removeFrom(playlistId, t.id); onDismiss() }
            MenuItem(Icons.Rounded.SkipNext, "Play next") { playNext(t); onDismiss() }
            MenuItem(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue") { addToQueue(t); onDismiss() }
            if (t.artistUrl != null) MenuItem(Icons.Rounded.Person, "Go to artist") { onDismiss(); nav.open(Page.ArtistPage(Artist(t.artistUrl, t.artist, null))) }
            MenuItem(Icons.Rounded.Radio, "Go to song radio") { onDismiss(); scope.launch { startRadio(t) } }
            MenuItem(Icons.Rounded.Share, "Share") {
                runCatching { context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, "${t.title} · ${t.artist}\n${t.url.replace("www.youtube.com", "music.youtube.com")}"), "Share")) }
                onDismiss()
            }
        } else Column(Modifier.padding(bottom = 24.dp)) {
            MenuItem(Icons.Rounded.Add, "New playlist") { newName = true }
            lib.playlists.forEach { p -> MenuItem(Icons.Rounded.MusicNote, p.name) { Graph.library.addTo(p.id, t); onDismiss() } }
        }
    }
    if (newName) NameDialog("Give your playlist a name", "", onDismiss = { newName = false }) { Graph.library.createPlaylist(it, t); newName = false; onDismiss() }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 20.dp))
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text("My playlist") }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onDone(name) }) { Text(if (initial.isEmpty()) "Create" else "Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The floating player above the tabs, in the song's colours, with a heart. */
@Composable
private fun MiniPlayer(ui: PlayerUi, onOpen: () -> Unit) {
    val t = ui.track
    val lib by Graph.library.data.collectAsState()
    val liked = t != null && lib.liked.any { it.id == t.id }
    Surface(
        shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpen),
    ) {
        Column {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(t?.art, null, Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(t?.title.orEmpty(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t?.artist.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .75f), maxLines = 1)
                }
                IconButton(onClick = { t?.let { Graph.library.toggleLike(it) } }) {
                    Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (liked) "Unlike" else "Like", tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer)
                }
                IconButton(onClick = { Graph.controller?.let { if (it.isPlaying) it.pause() else it.play() } }) {
                    Icon(if (ui.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (ui.playing) "Pause" else "Play", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            LinearProgressIndicator(
                progress = { if (ui.durationMs > 0) (ui.positionMs.toFloat() / ui.durationMs).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().height(2.dp).padding(horizontal = 8.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer, trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .2f),
            )
        }
    }
}
