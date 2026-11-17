package dev.corbyoliver.weather.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.corbyoliver.weather.domain.Condition
import dev.corbyoliver.weather.domain.FrogState
import dev.corbyoliver.weather.ui.draw.drawFrogScene
import dev.corbyoliver.weather.ui.draw.drawWeatherIcon
import dev.corbyoliver.weather.ui.draw.drawWindArrow
import dev.corbyoliver.weather.ui.theme.Tokens

const val OPEN_METEO_URL = "https://open-meteo.com/"
const val ATTRIBUTION = "Forecast: Open-Meteo (CC BY 4.0) · Rain timing: Pirate Weather · Alerts: National Weather Service"

@Composable
fun HomeScreen(state: HomeUiState, onSelectDay: (Int) -> Unit) {
    var alertOpen by remember { mutableStateOf(false) }
    val insets = WindowInsets.systemBars.asPaddingValues()
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Tokens.Background),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = insets.calculateTopPadding() + 12.dp,
            bottom = insets.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        state.alert?.let { alert ->
            item("alert") { AlertBanner(alert, onClick = { alertOpen = true }) }
        }
        state.alertsNote?.let { note ->
            item("alerts-note") { StatusNote(note) }
        }
        state.hero?.let { hero -> item("hero") { HeroCard(hero) } }
        if (state.hero == null && state.message != null) {
            item("message") { StatusNote(state.message) }
        } else if (state.message != null) {
            item("message") { StatusNote(state.message) }
        }
        if (state.days.isNotEmpty()) {
            item("days") { DayChips(state.days, state.selectedDay, onSelectDay) }
        }
        state.details?.let { d -> item("details") { DetailRows(d, state.days.getOrNull(state.selectedDay)?.label ?: "") } }
        item("footer") { Footer(state.updated) }
    }
    // Backdrop behind the status bar so scrolled content does not run under the clock.
    Box(Modifier.fillMaxWidth().height(insets.calculateTopPadding()).background(Tokens.Background.copy(alpha = 0.94f)))
    }
    val alert = state.alert
    if (alertOpen && alert != null) {
        AlertDialog(
            onDismissRequest = { alertOpen = false },
            confirmButton = { TextButton(onClick = { alertOpen = false }) { Text("Close", color = Tokens.Accent) } },
            title = { Text(alert.title, style = MaterialTheme.typography.titleLarge) },
            text = {
                LazyColumn { item { Text(alert.body, style = MaterialTheme.typography.bodyMedium, color = Tokens.Text) } }
            },
            containerColor = Tokens.Card,
        )
    }
}

@Composable
private fun StatusNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = Tokens.TextMuted,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
    )
}

@Composable
fun AlertBanner(alert: AlertBannerUi, onClick: () -> Unit) {
    val more = if (alert.moreCount > 0) " +${alert.moreCount} more" else ""
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(50))
            .background(Tokens.Amber)
            .clickable(onClickLabel = "Show alert details", role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(18.dp)) { drawWarningGlyph(Tokens.OnAmber) }
        Spacer(Modifier.width(10.dp))
        Text(
            alert.text + more,
            style = MaterialTheme.typography.labelLarge,
            color = Tokens.OnAmber,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun HeroCard(hero: HeroUi) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(Tokens.Card),
    ) {
        Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 20.dp)) {
            Text(
                buildString { append("Now · "); append(hero.place) },
                style = MaterialTheme.typography.labelMedium,
                color = Tokens.TextMuted,
                modifier = Modifier.semantics { heading() },
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "${hero.temperature}, ${hero.conditionText}, ${hero.feelsLike}"
                },
            ) {
                Text(hero.temperature, style = MaterialTheme.typography.displayLarge)
                Spacer(Modifier.width(12.dp))
                WeatherIcon(hero.condition, hero.isDay, Modifier.size(64.dp))
            }
            Text(hero.conditionText, style = MaterialTheme.typography.titleMedium)
            Text(hero.feelsLike, style = MaterialTheme.typography.bodyMedium, color = Tokens.TextMuted)
            hero.rainLine?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W500), color = Tokens.Accent)
            }
            if (hero.rainStrip.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                RainStrip(hero.rainStrip, Modifier.fillMaxWidth().height(28.dp))
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                hero.hours.forEach { h ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {
                            contentDescription = "${h.label}: ${h.temperature}, ${dev.corbyoliver.weather.ui.home.Format.conditionText(h.condition, h.isDay)}"
                        },
                    ) {
                        Text(h.temperature, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(4.dp))
                        WeatherIcon(h.condition, h.isDay, Modifier.size(28.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(h.label, style = MaterialTheme.typography.labelSmall, color = Tokens.TextMuted, maxLines = 1)
                    }
                }
            }
        }
        FrogScene(
            hero.frog,
            Modifier.fillMaxWidth().height(150.dp),
        )
    }
}

fun frogDescription(state: FrogState): String = when (state) {
    FrogState.RAIN -> "The frog sits under a leaf umbrella in the rain"
    FrogState.WINDY -> "The frog squints as a leaf blows away in the wind"
    FrogState.COLD_SNAP -> "The frog wears a purple beanie as snow falls"
    FrogState.CLEAR_NIGHT -> "The frog sleeps under a crescent moon and stars"
    FrogState.CLEAR_DAY -> "The frog wears sunglasses in bright sun"
    FrogState.CLOUDY -> "The frog sits calmly under clouds"
}

@Composable
fun FrogScene(state: FrogState, modifier: Modifier = Modifier) {
    val desc = frogDescription(state)
    Canvas(modifier.clearAndSetSemantics { contentDescription = desc }) { drawFrogScene(state) }
}

@Composable
fun WeatherIcon(condition: Condition, isDay: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) { drawWeatherIcon(condition, isDay) }
}

/** Next 60 minutes of rain intensity (0..1); null is unknown and drawn as a gap. */
@Composable
fun RainStrip(values: List<Float?>, modifier: Modifier = Modifier) {
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val n = values.size
            if (n == 0) return@Canvas
            val gap = 2.dp.toPx()
            val bw = (size.width - gap * (n - 1)) / n
            values.forEachIndexed { i, v ->
                val x = i * (bw + gap)
                drawRoundRect(Tokens.Tile, Offset(x, 0f), Size(bw, size.height), CornerRadius(bw / 2))
                if (v != null && v > 0f) {
                    val bh = size.height * v.coerceIn(0.12f, 1f)
                    drawRoundRect(Tokens.Accent, Offset(x, size.height - bh), Size(bw, bh), CornerRadius(bw / 2))
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text("Now", style = MaterialTheme.typography.labelSmall, color = Tokens.TextMuted, modifier = Modifier.weight(1f))
            Text("30 min", style = MaterialTheme.typography.labelSmall, color = Tokens.TextMuted, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text("60 min", style = MaterialTheme.typography.labelSmall, color = Tokens.TextMuted, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
    }
}

@Composable
fun DayChips(days: List<DayChipUi>, selected: Int, onSelect: (Int) -> Unit) {
    val listState = rememberLazyListState()
    LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        itemsIndexed(days) { i, d ->
            val isSel = i == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(76.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (isSel) Tokens.Tile else Tokens.Card)
                    .then(if (isSel) Modifier.border(BorderStroke(1.5.dp, Tokens.Text.copy(alpha = 0.85f)), RoundedCornerShape(22.dp)) else Modifier)
                    .selectable(selected = isSel, role = Role.Tab, onClick = { onSelect(i) })
                    .semantics(mergeDescendants = true) {
                        contentDescription = d.description
                        stateDescription = if (isSel) "Selected" else "Not selected"
                    }
                    .padding(vertical = 12.dp),
            ) {
                Text(d.label, style = MaterialTheme.typography.labelMedium, color = if (isSel) Tokens.Text else Tokens.TextMuted)
                Spacer(Modifier.height(6.dp))
                WeatherIcon(d.condition, true, Modifier.size(30.dp))
                Spacer(Modifier.height(6.dp))
                Row {
                    Text(d.high, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(6.dp))
                    Text(d.low, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W400), color = Tokens.TextMuted)
                }
            }
        }
    }
}

private enum class RowIcon { DROP, WIND, HUMIDITY, AIR }

@Composable
fun DetailRows(d: DayDetailsUi, dayLabel: String) {
    var precipOpen by rememberSaveable { mutableStateOf(true) }
    var windOpen by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Tokens.Card).animateContentSize(),
    ) {
        DetailRow(RowIcon.DROP, "Precipitation", d.precipValue, expanded = precipOpen, onToggle = { precipOpen = !precipOpen }, dayLabel = dayLabel)
        if (precipOpen) PrecipBars(d.precipBars, d.precipDescription)
        HorizontalDivider(color = Tokens.Divider, modifier = Modifier.padding(horizontal = 16.dp))
        DetailRow(RowIcon.WIND, "Wind", d.windValue, expanded = windOpen, onToggle = { windOpen = !windOpen }, dayLabel = dayLabel)
        if (windOpen) WindHours(d.windHours)
        HorizontalDivider(color = Tokens.Divider, modifier = Modifier.padding(horizontal = 16.dp))
        DetailRow(RowIcon.HUMIDITY, "Humidity", d.humidityValue, expanded = null, onToggle = null, dayLabel = dayLabel)
        HorizontalDivider(color = Tokens.Divider, modifier = Modifier.padding(horizontal = 16.dp))
        DetailRow(RowIcon.AIR, "Air quality", d.airQualityValue, expanded = null, onToggle = null, dayLabel = dayLabel)
    }
}

@Composable
private fun DetailRow(icon: RowIcon, title: String, value: String, expanded: Boolean?, onToggle: (() -> Unit)?, dayLabel: String) {
    val base = Modifier.fillMaxWidth().heightIn(min = 64.dp)
    val clickable = if (onToggle != null && expanded != null) {
        base.clickable(role = Role.Button, onClickLabel = if (expanded) "Collapse $title" else "Expand $title", onClick = onToggle)
            .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
    } else base
    Row(
        modifier = clickable
            .semantics(mergeDescendants = true) { contentDescription = "$title, $dayLabel: $value" }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Tokens.Tile),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(22.dp)) { drawRowIcon(icon) }
        }
        Spacer(Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Tokens.TextMuted, textAlign = TextAlign.End, modifier = Modifier.padding(start = 8.dp))
        if (expanded != null) {
            Spacer(Modifier.width(8.dp))
            Canvas(Modifier.size(20.dp).rotate(if (expanded) 180f else 0f)) { drawChevron(Tokens.TextMuted) }
        }
    }
}

@Composable
private fun PrecipBars(bars: List<BarUi>, description: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Row(Modifier.fillMaxWidth().height(72.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            bars.forEach { b ->
                Canvas(Modifier.weight(1f).fillMaxHeight()) {
                    val r = CornerRadius(size.width / 2)
                    drawRoundRect(Tokens.Tile, Offset.Zero, size, r)
                    val pct = b.chancePct
                    if (pct != null && pct > 0) {
                        val bh = size.height * pct / 100f
                        val color = if (b.isPast) Tokens.Accent.copy(alpha = 0.35f) else Tokens.Accent
                        drawRoundRect(color, Offset(0f, size.height - bh), Size(size.width, bh), r)
                    } else if (pct == null) {
                        drawCircle(Tokens.TextMuted, size.width * 0.18f, Offset(size.width / 2, size.height - size.width / 2))
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        // Labels sit under the first bar of each labelled group of hours.
        val groups = mutableListOf<Pair<String, Int>>()
        bars.forEach { b -> if (b.showLabel || groups.isEmpty()) groups += (if (b.showLabel) b.label else "") to 1 else groups[groups.lastIndex] = groups.last().first to groups.last().second + 1 }
        Row(Modifier.fillMaxWidth()) {
            groups.forEach { (label, n) ->
                Text(label, style = MaterialTheme.typography.labelSmall, color = Tokens.TextMuted, maxLines = 1, softWrap = false, modifier = Modifier.weight(n.toFloat()))
            }
        }
        Spacer(Modifier.height(2.dp))
        Text("Chance of rain by hour", style = MaterialTheme.typography.labelSmall, color = Tokens.TextMuted)
    }
}

@Composable
private fun WindHours(hours: List<WindHourUi>) {
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 14.dp)) {
        hours.forEach { h ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).clearAndSetSemantics { contentDescription = h.description },
            ) {
                Text(h.speed, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Box(Modifier.size(22.dp).clip(CircleShape).background(Tokens.Tile), contentAlignment = Alignment.Center) {
                    val deg = h.fromDeg
                    if (deg != null) Canvas(Modifier.size(14.dp)) { drawWindArrow(deg, Tokens.Accent) }
                }
                Spacer(Modifier.height(4.dp))
                Text(h.label, style = MaterialTheme.typography.labelSmall, color = Tokens.TextMuted)
            }
        }
    }
}

@Composable
fun Footer(updated: UpdatedUi?) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
        updated?.let {
            Text(
                it.text,
                style = MaterialTheme.typography.labelMedium,
                color = if (it.isStale) Tokens.Amber else Tokens.TextMuted,
            )
            Spacer(Modifier.height(6.dp))
        }
        // Open-Meteo data is CC BY 4.0: the credit links to open-meteo.com.
        val credit = buildAnnotatedString {
            append("Forecast: ")
            withLink(LinkAnnotation.Url(OPEN_METEO_URL, TextLinkStyles(SpanStyle(color = Tokens.Accent, textDecoration = TextDecoration.Underline)))) {
                append("Open-Meteo")
            }
            append(ATTRIBUTION.removePrefix("Forecast: Open-Meteo"))
        }
        Text(credit, style = MaterialTheme.typography.labelSmall, color = Tokens.TextMuted)
    }
}

private fun DrawScope.drawChevron(color: Color) {
    val w = size.width
    val h = size.height
    drawLine(color, Offset(w * 0.22f, h * 0.38f), Offset(w * 0.5f, h * 0.64f), w * 0.1f, StrokeCap.Round)
    drawLine(color, Offset(w * 0.5f, h * 0.64f), Offset(w * 0.78f, h * 0.38f), w * 0.1f, StrokeCap.Round)
}

private fun DrawScope.drawWarningGlyph(color: Color) {
    val w = size.width
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(w * 0.5f, w * 0.06f); lineTo(w * 0.96f, w * 0.9f); lineTo(w * 0.04f, w * 0.9f); close()
    }
    drawPath(path, color, style = Stroke(w * 0.1f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    drawLine(color, Offset(w * 0.5f, w * 0.38f), Offset(w * 0.5f, w * 0.6f), w * 0.1f, StrokeCap.Round)
    drawCircle(color, w * 0.06f, Offset(w * 0.5f, w * 0.75f))
}

private fun DrawScope.drawRowIcon(icon: RowIcon) {
    val w = size.width
    val c = Tokens.Accent
    when (icon) {
        RowIcon.DROP -> {
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.5f, w * 0.08f)
                cubicTo(w * 0.5f, w * 0.08f, w * 0.15f, w * 0.5f, w * 0.15f, w * 0.64f)
                cubicTo(w * 0.15f, w * 0.84f, w * 0.31f, w * 0.96f, w * 0.5f, w * 0.96f)
                cubicTo(w * 0.69f, w * 0.96f, w * 0.85f, w * 0.84f, w * 0.85f, w * 0.64f)
                cubicTo(w * 0.85f, w * 0.5f, w * 0.5f, w * 0.08f, w * 0.5f, w * 0.08f)
                close()
            }
            drawPath(p, c)
        }
        RowIcon.WIND -> {
            for ((i, y) in listOf(0.3f, 0.52f, 0.74f).withIndex()) {
                val end = if (i == 1) 0.92f else 0.74f
                drawLine(c, Offset(w * 0.08f, w * y), Offset(w * end, w * y), w * 0.1f, StrokeCap.Round)
            }
        }
        RowIcon.HUMIDITY -> {
            drawCircle(c, w * 0.14f, Offset(w * 0.32f, w * 0.62f))
            drawCircle(c, w * 0.2f, Offset(w * 0.64f, w * 0.42f))
            drawCircle(c, w * 0.1f, Offset(w * 0.7f, w * 0.82f))
        }
        RowIcon.AIR -> {
            drawCircle(c, w * 0.4f, center, style = Stroke(w * 0.1f))
            drawLine(c, Offset(w * 0.3f, w * 0.5f), Offset(w * 0.7f, w * 0.5f), w * 0.1f, StrokeCap.Round)
            drawLine(c, Offset(w * 0.5f, w * 0.3f), Offset(w * 0.5f, w * 0.7f), w * 0.1f, StrokeCap.Round)
        }
    }
}
