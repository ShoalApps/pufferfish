@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.shoalapps.pufferfish.ui

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.ktx.themeColorOrNull
import io.github.shoalapps.pufferfish.Graph
import io.github.shoalapps.pufferfish.data.Lyrics
import io.github.shoalapps.pufferfish.data.Track
import io.github.shoalapps.pufferfish.player.Queue

/**
 * A full Material colour scheme from the album art (seed picked with Material's quantiser), the
 * way PixelPlayer themes its player; null until the art loads, or with the setting off.
 */
@Composable
fun rememberArtScheme(art: String?, dark: Boolean): ColorScheme? {
    val context = LocalContext.current
    val scheme by produceState<ColorScheme?>(null, art, dark) {
        if (art == null) { value = null; return@produceState }
        value = runCatching {
            val req = coil3.request.ImageRequest.Builder(context).data(art).size(112).build()
            val img = coil3.SingletonImageLoader.get(context).execute(req).image ?: return@runCatching null
            val bmp = (img as? coil3.BitmapImage)?.bitmap?.copy(android.graphics.Bitmap.Config.ARGB_8888, false) ?: return@runCatching null
            val seed = bmp.asImageBitmap().themeColorOrNull() ?: return@runCatching null
            dynamicColorScheme(seed, dark, style = PaletteStyle.Content)
        }.getOrNull()
    }
    return scheme
}

/** Eases between two schemes, so the app's colours drift into the next song's. */
@Composable
fun animateScheme(target: ColorScheme): ColorScheme {
    @Composable fun a(c: Color) = animateColorAsState(c, tween(700), label = "scheme").value
    return target.copy(
        primary = a(target.primary), onPrimary = a(target.onPrimary),
        primaryContainer = a(target.primaryContainer), onPrimaryContainer = a(target.onPrimaryContainer),
        secondary = a(target.secondary), secondaryContainer = a(target.secondaryContainer), onSecondaryContainer = a(target.onSecondaryContainer),
        tertiary = a(target.tertiary), tertiaryContainer = a(target.tertiaryContainer),
        surface = a(target.surface), onSurface = a(target.onSurface), onSurfaceVariant = a(target.onSurfaceVariant),
        surfaceContainer = a(target.surfaceContainer), surfaceContainerHigh = a(target.surfaceContainerHigh), surfaceContainerHighest = a(target.surfaceContainerHighest),
        background = a(target.background), outline = a(target.outline), outlineVariant = a(target.outlineVariant),
    )
}

/** The full-screen player. */
@Composable
fun NowPlaying(ui: PlayerUi, onClose: () -> Unit, onArtist: (io.github.shoalapps.pufferfish.data.Artist) -> Unit = {}) {
    val t = ui.track
    var showLyrics by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    val c = Graph.controller
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // Meld-style backdrop: the art, blurred, under a scrim in the scheme's colours.
        if (Build.VERSION.SDK_INT >= 31 && t?.art != null) {
            AsyncImage(t.art, null, Modifier.fillMaxSize().blur(90.dp).alpha(.55f), contentScale = ContentScale.Crop)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f), MaterialTheme.colorScheme.surface.copy(alpha = .92f)))))
        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            val wide = maxWidth > maxHeight && maxWidth > 700.dp
            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Close") }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Now playing", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // Sleep timer, lit while it's set.
                    IconButton(onClick = { showSleep = true }) {
                        Icon(androidx.compose.material.icons.Icons.Rounded.Bedtime, "Sleep timer", tint = if (SleepTimer.endsAt != null || SleepTimer.endOfTrack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { showQueue = true }) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Queue") }
                }
                if (wide) {
                    // Landscape and tablets: art (or lyrics) on the left, the song and controls on the right.
                    Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) { ArtOrLyrics(t, ui, showLyrics) }
                        Column(Modifier.weight(1f)) { Details(t, ui, showLyrics, { showLyrics = !showLyrics }, onArtist) }
                    }
                } else {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { ArtOrLyrics(t, ui, showLyrics) }
                    Column(Modifier.wrapContentWidth().widthIn(max = 560.dp)) { Details(t, ui, showLyrics, { showLyrics = !showLyrics }, onArtist) }
                }
            }
        }
    }
    if (showQueue && c != null) QueueSheet(ui) { showQueue = false }
    if (showSleep) SleepSheet { showSleep = false }
}

@Composable
private fun ArtOrLyrics(t: Track?, ui: PlayerUi, showLyrics: Boolean) {
    if (showLyrics && t != null) {
        LyricsView(t, ui.positionMs)
        return
    }
    // The art eases a little smaller while paused, like PixelPlayer's.
    val scale by animateFloatAsState(if (ui.playing) 1f else .9f, label = "art")
    AsyncImage(
        t?.art, null,
        Modifier.padding(vertical = 12.dp).aspectRatio(1f, matchHeightConstraintsFirst = true)
            .graphicsScale(scale).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentScale = ContentScale.Crop,
    )
}

private fun Modifier.graphicsScale(s: Float) = this.then(Modifier.graphicsLayer(scaleX = s, scaleY = s))

@Composable
private fun Details(t: Track?, ui: PlayerUi, showLyrics: Boolean, toggleLyrics: () -> Unit, onArtist: (io.github.shoalapps.pufferfish.data.Artist) -> Unit) {
    val c = Graph.controller
    // Title and artist with Spotify's heart beside them.
    val lib by Graph.library.data.collectAsState()
    val liked = t != null && lib.liked.any { it.id == t.id }
    Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(t?.title.orEmpty(), style = MaterialTheme.typography.headlineSmallEmphasized, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                t?.artist.orEmpty(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                modifier = Modifier.clickable(enabled = t?.artistUrl != null) { t?.let { onArtist(io.github.shoalapps.pufferfish.data.Artist(it.artistUrl!!, it.artist, null)) } },
            )
        }
        IconButton(onClick = { t?.let { Graph.library.toggleLike(it) } }) {
            Icon(if (liked) androidx.compose.material.icons.Icons.Rounded.Favorite else androidx.compose.material.icons.Icons.Rounded.FavoriteBorder, if (liked) "Remove from Liked Songs" else "Add to Liked Songs",
                tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(30.dp))
        }
    }
    SeekBar(ui)
    // Main controls: rounded tonal skip buttons around a pill play button that widens while playing.
    val playWidth by animateDpAsState(if (ui.playing) 132.dp else 96.dp, label = "play")
    val playCorner by animateDpAsState(if (ui.playing) 28.dp else 48.dp, label = "corner")
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        Surface(onClick = { c?.seekToPrevious() }, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(width = 72.dp, height = 72.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.SkipPrevious, "Previous", Modifier.size(34.dp)) }
        }
        Surface(onClick = { c?.let { if (it.isPlaying) it.pause() else it.play() } }, shape = RoundedCornerShape(playCorner), color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(width = playWidth, height = 96.dp)) {
            Box(contentAlignment = Alignment.Center) {
                if (ui.buffering) LoadingIndicator(Modifier.size(48.dp), color = MaterialTheme.colorScheme.onPrimary)
                else Icon(if (ui.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (ui.playing) "Pause" else "Play", Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
        Surface(onClick = { c?.seekToNext() }, enabled = ui.hasNext, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(width = 72.dp, height = 72.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.SkipNext, "Next", Modifier.size(34.dp)) }
        }
    }
    // Secondary row: shuffle, repeat, lyrics.
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        Toggle(Icons.Rounded.Shuffle, "Shuffle", ui.shuffle) { c?.shuffleModeEnabled = !ui.shuffle }
        Toggle(if (ui.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Repeat", ui.repeat != Player.REPEAT_MODE_OFF) {
            c?.repeatMode = when (ui.repeat) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF }
        }
        Toggle(Icons.Rounded.Lyrics, "Lyrics", showLyrics, toggleLyrics)
    }
}

@Composable
private fun Toggle(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = CircleShape,
        color = if (on) MaterialTheme.colorScheme.tertiaryContainer else Color.Transparent,
        modifier = Modifier.size(52.dp),
    ) { Box(contentAlignment = Alignment.Center) { Icon(icon, label, tint = if (on) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) } }
}

/** Material 3 Expressive's wavy track, rippling while playing and flat when paused, with a draggable thumb. */
@Composable
private fun SeekBar(ui: PlayerUi) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val frac = (dragging ?: if (ui.durationMs > 0) ui.positionMs.toFloat() / ui.durationMs else 0f).coerceIn(0f, 1f)
    val amplitude by animateFloatAsState(if (ui.playing && dragging == null) 1f else 0f, label = "wave")
    Box(Modifier.fillMaxWidth().padding(top = 16.dp).height(36.dp), contentAlignment = Alignment.Center) {
        LinearWavyProgressIndicator(
            progress = { frac },
            amplitude = { amplitude },
            modifier = Modifier.fillMaxWidth().height(12.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = .22f),
        )
        Slider(
            value = frac,
            onValueChange = { dragging = it },
            onValueChangeFinished = { dragging?.let { Graph.controller?.seekTo((it * ui.durationMs).toLong()) }; dragging = null },
            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Row(Modifier.fillMaxWidth()) {
        Text(formatTime((frac * ui.durationMs).toLong()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text("-" + formatTime((ui.durationMs - frac * ui.durationMs).toLong().coerceAtLeast(0)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Lyrics, following the song. Word-timed lyrics light up word by word (karaoke style); line-timed
 * ones a line at a time. Tap a line to jump there.
 */
@Composable
private fun LyricsView(t: Track, positionMs: Long) {
    val lyrics by produceState<Lyrics.Result?>(null, t.id) {
        value = runCatching { Lyrics.find(Graph.http, t.title, t.artist, t.durationSec) }.getOrNull() ?: Lyrics.Result(emptyList(), null)
    }
    val l = lyrics
    val on = MaterialTheme.colorScheme.onSurface
    val dim = on.copy(alpha = .3f)
    when {
        l == null -> LoadingIndicator()
        l.synced.isNotEmpty() -> {
            val current = l.synced.indexOfLast { it.atMs <= positionMs + 250 }
            val list = rememberLazyListState()
            LaunchedEffect(current) { if (current >= 0) list.animateScrollToItem((current - 1).coerceAtLeast(0)) }
            LazyColumn(state = list, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(vertical = 32.dp)) {
                itemsIndexed(l.synced) { i, line ->
                    val active = i == current
                    val text = if (line.words.isNotEmpty() && active) buildAnnotatedString {
                        line.words.forEach { w ->
                            val p = ((positionMs - w.startMs).toFloat() / (w.endMs - w.startMs).coerceAtLeast(1)).coerceIn(0f, 1f)
                            // The current line stands out; each word fills to full strength as it's sung.
                            withStyle(SpanStyle(color = androidx.compose.ui.graphics.lerp(on.copy(alpha = .55f), on, p))) { append(w.text) }
                        }
                    } else buildAnnotatedString { append(line.text.ifBlank { "♪" }) }
                    val size by animateFloatAsState(if (active) 30f else 24f, label = "line")
                    Text(
                        text, fontSize = size.sp, lineHeight = (size + 8).sp, fontWeight = FontWeight.ExtraBold,
                        color = if (active) on else dim,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { Graph.controller?.seekTo(line.atMs) }.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
                item { Text("Lyrics from ${l.source}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        l.plain != null -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 24.dp)) {
            item { Text(l.plain, fontSize = 22.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold) }
            item { Text("Lyrics from ${l.source}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp)) }
        }
        else -> Text("No lyrics found for this song", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** What's playing next; tap to jump. */
@Composable
private fun QueueSheet(ui: PlayerUi, onDismiss: () -> Unit) {
    val c = Graph.controller ?: return
    val items = remember(ui.item, ui.queueVersion) { (0 until c.mediaItemCount).map { c.getMediaItemAt(it) } }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("Up next", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 32.dp), state = rememberLazyListState((c.currentMediaItemIndex).coerceAtLeast(0))) {
            itemsIndexed(items) { i, item ->
                val tr = Queue.tracks[item.mediaId]
                val current = i == c.currentMediaItemIndex
                Row(
                    Modifier.fillMaxWidth().clickable { c.seekTo(i, 0); c.play() }
                        .background(if (current) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(tr?.art, null, Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(tr?.title ?: item.mediaMetadata.title?.toString().orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (current) FontWeight.Bold else FontWeight.Normal)
                        Text(tr?.artist.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun SleepSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("Sleep timer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 24.dp, bottom = 8.dp))
        val ends = SleepTimer.endsAt
        if (ends != null || SleepTimer.endOfTrack) {
            val left = ends?.let { ((it - System.currentTimeMillis()) / 60_000).coerceAtLeast(0) + 1 }
            Text(if (left != null) "Stopping in about $left min" else "Stopping at the end of this song", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 24.dp, bottom = 8.dp))
        }
        Column(Modifier.padding(bottom = 24.dp)) {
            listOf(5, 10, 15, 30, 45, 60).forEach { m ->
                Text("$m minutes", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().clickable { SleepTimer.set(m); onDismiss() }.padding(horizontal = 24.dp, vertical = 14.dp))
            }
            Text("End of song", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().clickable { SleepTimer.atEndOfTrack(); onDismiss() }.padding(horizontal = 24.dp, vertical = 14.dp))
            if (ends != null || SleepTimer.endOfTrack) Text("Turn off timer", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().clickable { SleepTimer.cancel(); onDismiss() }.padding(horizontal = 24.dp, vertical = 14.dp))
        }
    }
}
