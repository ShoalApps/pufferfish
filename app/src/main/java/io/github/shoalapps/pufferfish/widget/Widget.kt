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

/**
 * The player, pared right down: the seek bar's water and the three controls. While a song plays
 * the water ripples with a fish mid-leap; paused, it's a calm line. Tap the water to open the app.
 */
class PufferfishWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val p = context.getSharedPreferences("widget", Context.MODE_PRIVATE)
        val title = p.getString("title", null)
        val artist = p.getString("artist", null)
        val playing = p.getBoolean("playing", false)
        val launch = actionStartActivity(Intent(context, io.github.shoalapps.pufferfish.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        provideContent {
            GlanceTheme {
                val size = androidx.glance.LocalSize.current
                val density = context.resources.displayMetrics.density
                val w = ((size.width.value - 28) * density).toInt().coerceAtLeast(64)
                val h = (40 * density).toInt()
                val (wave, fish) = water(w, h, density, playing)
                WidgetSurface {
                    Spacer(GlanceModifier.defaultWeight())
                    // The water, tinted with the wallpaper's colours. Read out as what's playing.
                    Box(
                        GlanceModifier.fillMaxWidth().height(40.dp).clickable(launch),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(ImageProvider(wave), title?.let { "$it by ${artist.orEmpty()}" + if (playing) ", playing" else ", paused" } ?: "Pufferfish, nothing playing", GlanceModifier.fillMaxWidth().height(40.dp), colorFilter = ColorFilter.tint(GlanceTheme.colors.primary))
                        if (fish != null) Image(ImageProvider(fish), null, GlanceModifier.fillMaxWidth().height(40.dp), colorFilter = ColorFilter.tint(GlanceTheme.colors.tertiary))
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        PlayerButton(R.drawable.ic_w_prev, "Previous", actionRunCallback<MediaAction>(actionParametersOf(MediaAction.KEY to "prev")), big = false, GlanceModifier.defaultWeight())
                        PlayerButton(if (playing) R.drawable.ic_w_pause else R.drawable.ic_w_play, if (playing) "Pause" else "Play", actionRunCallback<MediaAction>(actionParametersOf(MediaAction.KEY to "toggle")), big = true, GlanceModifier.defaultWeight())
                        PlayerButton(R.drawable.ic_w_next, "Next", actionRunCallback<MediaAction>(actionParametersOf(MediaAction.KEY to "next")), big = false, GlanceModifier.defaultWeight())
                    }
                    Spacer(GlanceModifier.defaultWeight())
                }
            }
        }
    }
}

/** A control: round, the play button bigger and filled with the main colour. */
@Composable
private fun PlayerButton(icon: Int, label: String, action: Action, big: Boolean, modifier: GlanceModifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            GlanceModifier.size(if (big) 60.dp else 52.dp).cornerRadius(if (big) 30.dp else 26.dp)
                .background(if (big) GlanceTheme.colors.primary else GlanceTheme.colors.secondaryContainer).clickable(action),
            contentAlignment = Alignment.Center,
        ) {
            Image(ImageProvider(icon), label, GlanceModifier.size(28.dp), colorFilter = ColorFilter.tint(if (big) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSecondaryContainer))
        }
    }
}

/**
 * The seek bar's water as two white masks (tinted by the widget): the wave, and a fish in
 * mid-leap with a splash below it. Paused, a flat line and no fish.
 */
private fun water(w: Int, h: Int, density: Float, playing: Boolean): Pair<android.graphics.Bitmap, android.graphics.Bitmap?> {
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE; style = android.graphics.Paint.Style.STROKE
        strokeWidth = 4 * density; strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND
    }
    val mid = h * 0.62f
    val amp = if (playing) 3.5f * density else 0f
    val wavelength = 34 * density
    fun y(x: Float) = mid + amp * kotlin.math.sin(x / wavelength * 2 * Math.PI).toFloat()
    val wave = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ALPHA_8)
    android.graphics.Canvas(wave).apply {
        val path = android.graphics.Path()
        val pad = paint.strokeWidth
        path.moveTo(pad, y(pad))
        var x = pad
        while (x < w - pad) { x = (x + 2 * density).coerceAtMost(w - pad); path.lineTo(x, y(x)) }
        drawPath(path, paint)
    }
    if (!playing) return wave to null
    val fish = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ALPHA_8)
    android.graphics.Canvas(fish).apply {
        val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
        val r = 6.5f * density
        // Rising out of the water, a third of the way along, nose tipped up.
        val cx = w * 0.36f
        val cy = mid - 13 * density
        save()
        rotate(-22f, cx, cy)
        val body = android.graphics.Path().apply {
            moveTo(cx - r * 0.75f, cy)
            quadTo(cx - r * 1.3f, cy - r * 0.2f, cx - r * 1.65f, cy - r * 0.7f)
            quadTo(cx - r * 1.4f, cy, cx - r * 1.65f, cy + r * 0.7f)
            quadTo(cx - r * 1.3f, cy + r * 0.2f, cx - r * 0.75f, cy)
            close()
            moveTo(cx - r * 0.35f, cy - r * 0.55f)
            quadTo(cx - r * 0.1f, cy - r * 1.05f, cx + r * 0.35f, cy - r * 0.6f)
            close()
            addOval(cx - r, cy - r * 0.72f, cx + r, cy + r * 0.72f, android.graphics.Path.Direction.CW)
        }
        drawPath(body, fill)
        // The eye, cut out.
        fill.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
        drawCircle(cx + r * 0.5f, cy - r * 0.18f, r * 0.2f, fill)
        fill.xfermode = null
        restore()
        // Droplets where it left the water.
        val sx = cx - 14 * density
        for (d in -1..1) drawCircle(sx + d * 4 * density, y(sx) - (if (d == 0) 7f else 4.5f) * density, 1.7f * density, fill)
    }
    return wave to fish
}

/** Sends a widget button press to the player. */
class MediaAction : ActionCallback {
    companion object { val KEY = ActionParameters.Key<String>("media") }

    override suspend fun onAction(receiverContext: Context, glanceId: GlanceId, parameters: ActionParameters) {
        // Widget actions arrive in a broadcast receiver, which Android doesn't let bind to the
        // player's service; the app's own context can.
        val context = receiverContext.applicationContext
        val token = androidx.media3.session.SessionToken(context, android.content.ComponentName(context, io.github.shoalapps.pufferfish.player.PlaybackService::class.java))
        val future = androidx.media3.session.MediaController.Builder(context, token).buildAsync()
        val c = kotlinx.coroutines.suspendCancellableCoroutine<androidx.media3.session.MediaController?> { cont ->
            future.addListener({ cont.resumeWith(Result.success(runCatching { future.get() }.getOrNull())) }, context.mainExecutor)
        } ?: return
        // The player only takes commands on the main thread.
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            when (parameters[KEY]) {
                "prev" -> c.seekToPrevious()
                "next" -> c.seekToNext()
                else -> if (c.isPlaying) c.pause() else { if (c.mediaItemCount > 0) { c.prepare(); c.play() } }
            }
            androidx.media3.session.MediaController.releaseFuture(future)
        }
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
