package dev.corbyoliver.weather.ui.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.corbyoliver.weather.MainActivity
import dev.corbyoliver.weather.WeatherApp
import dev.corbyoliver.weather.data.WeatherSnapshot
import dev.corbyoliver.weather.domain.Condition
import dev.corbyoliver.weather.ui.draw.drawWeatherIcon
import dev.corbyoliver.weather.ui.theme.Tokens
import java.time.Instant

/*
 * The three home-screen widgets from the spec. They show the same cached
 * data as the app and never download anything themselves: the background
 * jobs and the app update the repository, and [WidgetUpdater] redraws them.
 * Tapping any widget opens the app.
 */

private fun c(color: Color) = ColorProvider(color)

/**
 * The three widgets redraw together, so the shared presentation is computed
 * once per data change (and at most once a minute) and reused by all three.
 */
private object WidgetData {
    private var last: Triple<WeatherSnapshot, Long, WidgetsUi>? = null

    @Synchronized
    fun get(snap: WeatherSnapshot, now: Instant): WidgetsUi {
        val minute = now.epochSecond / 60
        last?.let { (s, m, ui) -> if (s === snap && m == minute) return ui }
        return presentWidgets(snap, now).also { last = Triple(snap, minute, it) }
    }
}

private suspend fun loadWidgets(context: Context): WidgetsUi {
    val repository = (context.applicationContext as WeatherApp).repository
    repository.loadCache()
    return WidgetData.get(repository.state.value, Instant.now())
}

/** Draws a weather glyph into a bitmap, since widgets cannot draw on a canvas. */
private fun weatherIconBitmap(condition: Condition, isDay: Boolean, px: Int = 144): Bitmap {
    val image = ImageBitmap(px, px)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(px.toFloat(), px.toFloat())) {
        drawWeatherIcon(condition, isDay)
    }
    return image.asAndroidBitmap()
}

private val openApp get() = GlanceModifier.clickable(actionStartActivity<MainActivity>())

// ---------- Small 2x2: temperature + rain chance ----------

class TempRainWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ui = loadWidgets(context).temp
        val icon = ui.condition?.let { weatherIconBitmap(it, ui.isDay) }
        provideContent { TempRainContent(ui, icon) }
    }
}

@Composable
private fun TempRainContent(ui: TempWidgetUi, icon: Bitmap?) {
    Column(
        modifier = GlanceModifier.fillMaxSize().cornerRadius(28.dp).background(Tokens.Card).padding(14.dp).then(openApp)
            .semantics { contentDescription = "${ui.temperature}, ${ui.conditionText}. ${ui.rainText}. ${ui.updated}" },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
            Text(ui.temperature, style = TextStyle(color = c(Tokens.Text), fontSize = 40.sp, fontWeight = FontWeight.Medium))
            Spacer(GlanceModifier.defaultWeight())
            if (icon != null) Image(ImageProvider(icon), contentDescription = null, modifier = GlanceModifier.size(40.dp))
        }
        Text(ui.conditionText, style = TextStyle(color = c(Tokens.TextMuted), fontSize = 13.sp), maxLines = 1)
        Spacer(GlanceModifier.defaultWeight())
        Text(ui.rainText, style = TextStyle(color = c(Tokens.Accent), fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 2)
        Text(
            ui.updated + if (ui.isStale) " · out of date" else "",
            style = TextStyle(color = c(if (ui.isStale) Tokens.Amber else Tokens.TextMuted), fontSize = 11.sp),
            maxLines = 1,
        )
    }
}

// ---------- Small 2x2: NWS alert ----------

class AlertWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ui = loadWidgets(context).alert
        provideContent { AlertContent(ui) }
    }
}

@Composable
private fun AlertContent(ui: AlertWidgetUi) {
    val bg = if (ui.active) Tokens.Amber else Tokens.Card
    val fg = if (ui.active) Tokens.OnAmber else Tokens.Text
    val muted = if (ui.active) Tokens.OnAmber else Tokens.TextMuted
    Column(
        modifier = GlanceModifier.fillMaxSize().cornerRadius(28.dp).background(bg).padding(14.dp).then(openApp)
            .semantics { contentDescription = "${ui.title}. ${ui.detail}" },
    ) {
        Text(if (ui.active) "⚠ NWS alert" else "NWS alerts", style = TextStyle(color = c(muted), fontSize = 12.sp, fontWeight = FontWeight.Medium))
        Spacer(GlanceModifier.defaultWeight())
        Text(ui.title, style = TextStyle(color = c(fg), fontSize = 18.sp, fontWeight = FontWeight.Bold), maxLines = 3)
        if (ui.detail.isNotEmpty()) {
            Text(ui.detail, style = TextStyle(color = c(muted), fontSize = 13.sp), maxLines = 2)
        }
    }
}

// ---------- Wide 4x2: rain timing + 60-minute strip ----------

class RainTimingWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ui = loadWidgets(context).rain
        provideContent { RainContent(ui) }
    }
}

@Composable
private fun RainContent(ui: RainWidgetUi) {
    Column(
        modifier = GlanceModifier.fillMaxSize().cornerRadius(28.dp).background(Tokens.Card).padding(16.dp).then(openApp)
            .semantics { contentDescription = "${ui.line}. ${ui.updated}" },
    ) {
        Text(ui.line, style = TextStyle(color = c(Tokens.Accent), fontSize = 16.sp, fontWeight = FontWeight.Medium), maxLines = 2)
        Spacer(GlanceModifier.defaultWeight())
        if (ui.strip.isNotEmpty()) {
            RainStrip(ui.strip)
            Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 2.dp)) {
                val label = TextStyle(color = c(Tokens.TextMuted), fontSize = 11.sp)
                Text("Now", style = label, modifier = GlanceModifier.defaultWeight())
                Text("30 min", style = label.copy(textAlign = TextAlign.Center), modifier = GlanceModifier.defaultWeight())
                Text("60 min", style = label.copy(textAlign = TextAlign.End), modifier = GlanceModifier.defaultWeight())
            }
        }
        Text(ui.updated, style = TextStyle(color = c(Tokens.TextMuted), fontSize = 11.sp), modifier = GlanceModifier.padding(top = 4.dp))
    }
}

private const val STRIP_HEIGHT = 30

/**
 * 12 bars. A widget row may hold at most 10 children, so the bars sit in
 * two halves of 6, and the gaps are padding rather than spacer children.
 */
@Composable
private fun RainStrip(values: List<Float?>) {
    Row(modifier = GlanceModifier.fillMaxWidth().height(STRIP_HEIGHT.dp)) {
        values.chunked(6).forEach { half ->
            Row(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                half.forEach { v -> StripBar(v) }
            }
        }
    }
}

@Composable
private fun androidx.glance.layout.RowScope.StripBar(v: Float?) {
    Box(modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 1.5.dp)) {
        Box(
            modifier = GlanceModifier.fillMaxSize().cornerRadius(6.dp).background(Tokens.Tile),
            contentAlignment = Alignment.BottomCenter,
        ) {
            if (v == null) {
                // Unknown, never drawn as dry.
                Box(GlanceModifier.size(4.dp).cornerRadius(2.dp).background(Tokens.TextMuted)) {}
            } else if (v > 0f) {
                val h = (STRIP_HEIGHT * v.coerceIn(0.12f, 1f)).toInt().coerceAtLeast(4)
                Box(GlanceModifier.fillMaxWidth().height(h.dp).cornerRadius(6.dp).background(Tokens.Accent)) {}
            }
        }
    }
}

// ---------- Receivers and updates ----------

class TempRainWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TempRainWidget()
}

class AlertWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AlertWidget()
}

class RainTimingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RainTimingWidget()
}

object WidgetUpdater {
    /** Redraws every placed widget from the cached data. Cheap when none are placed. */
    suspend fun updateAll(context: Context) {
        TempRainWidget().updateAll(context)
        AlertWidget().updateAll(context)
        RainTimingWidget().updateAll(context)
    }
}
