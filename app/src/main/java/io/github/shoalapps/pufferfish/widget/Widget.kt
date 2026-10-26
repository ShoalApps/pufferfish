package io.github.shoalapps.pufferfish.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.layout.ContentScale
import androidx.glance.appwidget.updateAll
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import io.github.shoalapps.pufferfish.R

/** What's playing, with its cover and controls. */
class PufferfishWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val p = context.getSharedPreferences("widget", Context.MODE_PRIVATE)
        val title = p.getString("title", null)
        val artist = p.getString("artist", null)
        val playing = p.getBoolean("playing", false)
        val art = loadBitmap(context, p.getString("art", null))
        val launch = actionStartActivity(Intent(context, io.github.shoalapps.pufferfish.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        provideContent {
            GlanceTheme {
                WidgetSurface {
                    Row(GlanceModifier.fillMaxWidth().defaultWeight().clickable(launch), verticalAlignment = Alignment.CenterVertically) {
                        if (art != null) Image(ImageProvider(art), null, GlanceModifier.size(64.dp).cornerRadius(14.dp), contentScale = ContentScale.Crop)
                        else Box(GlanceModifier.size(64.dp).cornerRadius(14.dp).background(GlanceTheme.colors.primaryContainer), contentAlignment = Alignment.Center) {
                            Image(ImageProvider(R.drawable.ic_launcher_mono), null, GlanceModifier.size(56.dp), colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer))
                        }
                        Column(GlanceModifier.defaultWeight().padding(start = 12.dp)) {
                            Text(title ?: "Pufferfish", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                            Text(artist ?: "Tap to find something to play", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 1)
                        }
                    }
                    Row(GlanceModifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        WidgetButton(R.drawable.ic_w_prev, "Previous", actionRunCallback<MediaAction>(actionParametersOf(MediaAction.KEY to "prev")), GlanceModifier.defaultWeight())
                        WidgetButton(if (playing) R.drawable.ic_w_pause else R.drawable.ic_w_play, if (playing) "Pause" else "Play", actionRunCallback<MediaAction>(actionParametersOf(MediaAction.KEY to "toggle")), GlanceModifier.defaultWeight())
                        WidgetButton(R.drawable.ic_w_next, "Next", actionRunCallback<MediaAction>(actionParametersOf(MediaAction.KEY to "next")), GlanceModifier.defaultWeight())
                    }
                }
            }
        }
    }
}

/** Sends a widget button press to the player. */
class MediaAction : ActionCallback {
    companion object { val KEY = ActionParameters.Key<String>("media") }

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val token = androidx.media3.session.SessionToken(context, android.content.ComponentName(context, io.github.shoalapps.pufferfish.player.PlaybackService::class.java))
        val future = androidx.media3.session.MediaController.Builder(context, token).buildAsync()
        val c = kotlinx.coroutines.suspendCancellableCoroutine<androidx.media3.session.MediaController?> { cont ->
            future.addListener({ cont.resumeWith(Result.success(runCatching { future.get() }.getOrNull())) }, context.mainExecutor)
        } ?: return
        when (parameters[KEY]) {
            "prev" -> c.seekToPrevious()
            "next" -> c.seekToNext()
            else -> if (c.isPlaying) c.pause() else { if (c.mediaItemCount > 0) { c.prepare(); c.play() } }
        }
        androidx.media3.session.MediaController.releaseFuture(future)
    }
}

class PufferfishWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PufferfishWidget()
}

/** Saves what's playing for the widget and refreshes it. */
suspend fun recordNowPlaying(context: Context, title: String?, artist: String?, art: String?, playing: Boolean) {
    context.getSharedPreferences("widget", Context.MODE_PRIVATE).edit()
        .putString("title", title).putString("artist", artist).putString("art", art).putBoolean("playing", playing).apply()
    runCatching { PufferfishWidget().updateAll(context) }
}


/** A rounded tonal button with an icon and a label, like the apps' own. */
@Composable
internal fun WidgetButton(icon: Int, label: String, action: Action, modifier: GlanceModifier = GlanceModifier) {
    Column(modifier.clickable(action), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            GlanceModifier.size(48.dp).cornerRadius(16.dp).background(GlanceTheme.colors.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Image(ImageProvider(icon), label, GlanceModifier.size(24.dp), colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer))
        }
        Text(label, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 1, modifier = GlanceModifier.padding(top = 4.dp))
    }
}

/** The app's fish and name along the top. */
@Composable
internal fun WidgetHeader(name: String, open: Action) {
    Row(GlanceModifier.fillMaxWidth().clickable(open), verticalAlignment = Alignment.CenterVertically) {
        Image(ImageProvider(R.drawable.ic_widget_badge), null, GlanceModifier.size(26.dp), colorFilter = ColorFilter.tint(GlanceTheme.colors.primary))
        Text(name, style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp), modifier = GlanceModifier.padding(start = 6.dp))
    }
}

/** The widget's rounded surface. */
@Composable
internal fun WidgetSurface(content: @Composable ColumnScope.() -> Unit) {
    Column(
        GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(28.dp).padding(14.dp),
    ) { content() }
}

/** A row of buttons spread across the widget. */
@Composable
internal fun ButtonRow(vararg buttons: Triple<Int, String, Action>) {
    Row(GlanceModifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        buttons.forEach { (icon, label, action) -> WidgetButton(icon, label, action, GlanceModifier.defaultWeight()) }
    }
}

internal suspend fun loadBitmap(context: Context, url: String?): android.graphics.Bitmap? {
    if (url == null) return null
    return runCatching {
        val req = coil3.request.ImageRequest.Builder(context).data(url).size(320).build()
        (coil3.SingletonImageLoader.get(context).execute(req).image as? coil3.BitmapImage)?.bitmap
            ?.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
    }.getOrNull()
}

internal fun open(context: Context, uri: String, cls: Class<*>): Action =
    actionStartActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setClass(context, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

/**
 * A widget of buttons: a slim strip (the fish, then the buttons) when short, or the app's
 * name above bigger buttons when stretched taller.
 */
@Composable
internal fun QuickWidget(name: String, launch: Action, vararg buttons: Triple<Int, String, Action>) {
    val short = androidx.glance.LocalSize.current.height < 120.dp
    WidgetSurface {
        if (short) {
            Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Image(ImageProvider(R.drawable.ic_widget_badge), name, GlanceModifier.size(32.dp).clickable(launch), colorFilter = ColorFilter.tint(GlanceTheme.colors.primary))
                buttons.forEach { (icon, label, action) -> WidgetButton(icon, label, action, GlanceModifier.defaultWeight()) }
            }
        } else {
            WidgetHeader(name, launch)
            Spacer(GlanceModifier.defaultWeight())
            ButtonRow(*buttons)
            Spacer(GlanceModifier.defaultWeight())
        }
    }
}
