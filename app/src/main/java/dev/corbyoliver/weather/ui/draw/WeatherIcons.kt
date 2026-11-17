package dev.corbyoliver.weather.ui.draw

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import dev.corbyoliver.weather.domain.Condition
import dev.corbyoliver.weather.ui.theme.Tokens
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Our own weather glyphs, drawn on a canvas so they scale and can also be
 * rendered into bitmaps for the home-screen widgets. Fits a square.
 */
fun DrawScope.drawWeatherIcon(condition: Condition, isDay: Boolean) {
    val s = min(size.width, size.height)
    val ox = (size.width - s) / 2f
    val oy = (size.height - s) / 2f
    fun p(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)
    when (condition) {
        Condition.CLEAR -> if (isDay) sun(p(0.5f, 0.5f), s * 0.22f) else moon(p(0.5f, 0.5f), s * 0.3f)
        Condition.MOSTLY_CLEAR, Condition.PARTLY_CLOUDY -> {
            if (isDay) sun(p(0.38f, 0.38f), s * 0.17f) else moon(p(0.38f, 0.36f), s * 0.22f)
            cloud(p(0.56f, 0.64f), s * 0.62f, Tokens.Cloud)
        }
        Condition.OVERCAST -> {
            cloud(p(0.4f, 0.44f), s * 0.5f, Tokens.CloudDark)
            cloud(p(0.55f, 0.6f), s * 0.7f, Tokens.Cloud)
        }
        Condition.FOG -> {
            cloud(p(0.5f, 0.42f), s * 0.66f, Tokens.Cloud)
            for (i in 0..2) {
                val y = 0.66f + i * 0.11f
                drawLine(Tokens.CloudDark, p(0.18f + i * 0.05f, y), p(0.82f - i * 0.05f, y), s * 0.06f, StrokeCap.Round)
            }
        }
        Condition.DRIZZLE -> { cloud(p(0.5f, 0.42f), s * 0.7f, Tokens.Cloud); drops(s, ox, oy, count = 2, long = false) }
        Condition.RAIN -> { cloud(p(0.5f, 0.42f), s * 0.7f, Tokens.Cloud); drops(s, ox, oy, count = 3, long = true) }
        Condition.HEAVY_RAIN -> { cloud(p(0.5f, 0.42f), s * 0.7f, Tokens.CloudDark); drops(s, ox, oy, count = 4, long = true) }
        Condition.FREEZING_RAIN -> {
            cloud(p(0.5f, 0.42f), s * 0.7f, Tokens.Cloud)
            drops(s, ox, oy, count = 2, long = true)
            flake(p(0.5f, 0.82f), s * 0.07f)
        }
        Condition.SNOW -> {
            cloud(p(0.5f, 0.42f), s * 0.7f, Tokens.Cloud)
            flake(p(0.32f, 0.78f), s * 0.07f); flake(p(0.52f, 0.86f), s * 0.07f); flake(p(0.7f, 0.76f), s * 0.07f)
        }
        Condition.THUNDERSTORM -> {
            cloud(p(0.5f, 0.4f), s * 0.7f, Tokens.CloudDark)
            val bolt = Path().apply {
                moveTo(ox + 0.54f * s, oy + 0.56f * s); lineTo(ox + 0.4f * s, oy + 0.76f * s)
                lineTo(ox + 0.51f * s, oy + 0.76f * s); lineTo(ox + 0.44f * s, oy + 0.95f * s)
                lineTo(ox + 0.64f * s, oy + 0.7f * s); lineTo(ox + 0.53f * s, oy + 0.7f * s)
                lineTo(ox + 0.6f * s, oy + 0.56f * s); close()
            }
            drawPath(bolt, Tokens.Bolt)
        }
        Condition.UNKNOWN -> drawCircle(Tokens.CloudDark, s * 0.1f, p(0.5f, 0.5f), style = Stroke(s * 0.05f))
    }
}

internal fun DrawScope.sun(c: Offset, r: Float, color: Color = Tokens.Sun) {
    drawCircle(color, r, c)
    for (i in 0 until 8) {
        val a = Math.toRadians(i * 45.0)
        val from = Offset(c.x + cos(a).toFloat() * r * 1.35f, c.y + sin(a).toFloat() * r * 1.35f)
        val to = Offset(c.x + cos(a).toFloat() * r * 1.75f, c.y + sin(a).toFloat() * r * 1.75f)
        drawLine(color, from, to, r * 0.22f, StrokeCap.Round)
    }
}

/** Crescent moon: a disc with a second disc cut out. */
internal fun DrawScope.moon(c: Offset, r: Float, color: Color = Tokens.Moon) {
    val disc = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) }
    val cut = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(c.x + r * 0.55f, c.y - r * 0.35f), r * 0.9f)) }
    val crescent = Path().apply { op(disc, cut, androidx.compose.ui.graphics.PathOperation.Difference) }
    drawPath(crescent, color)
}

/** A cloud centred on [c], [w] wide. */
internal fun DrawScope.cloud(c: Offset, w: Float, color: Color) {
    val h = w * 0.5f
    drawCircle(color, h * 0.42f, Offset(c.x - w * 0.24f, c.y + h * 0.1f))
    drawCircle(color, h * 0.58f, Offset(c.x - w * 0.02f, c.y - h * 0.12f))
    drawCircle(color, h * 0.44f, Offset(c.x + w * 0.24f, c.y + h * 0.06f))
    drawRoundRect(
        color,
        topLeft = Offset(c.x - w * 0.42f, c.y + h * 0.02f),
        size = Size(w * 0.84f, h * 0.46f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.23f),
    )
}

private fun DrawScope.drops(s: Float, ox: Float, oy: Float, count: Int, long: Boolean) {
    val len = if (long) 0.16f else 0.08f
    val xs = when (count) { 2 -> listOf(0.4f, 0.6f); 3 -> listOf(0.32f, 0.5f, 0.68f); else -> listOf(0.26f, 0.42f, 0.58f, 0.74f) }
    xs.forEachIndexed { i, x ->
        val y0 = 0.7f + (i % 2) * 0.06f
        drawLine(Tokens.Accent, Offset(ox + x * s, oy + y0 * s), Offset(ox + (x - 0.04f) * s, oy + (y0 + len) * s), s * 0.055f, StrokeCap.Round)
    }
}

internal fun DrawScope.flake(c: Offset, r: Float, color: Color = Tokens.Snow) {
    for (i in 0 until 3) {
        rotate(i * 60f, c) { drawLine(color, Offset(c.x - r, c.y), Offset(c.x + r, c.y), r * 0.35f, StrokeCap.Round) }
    }
}

/** An arrow pointing the way the wind blows TO, given where it comes FROM. */
fun DrawScope.drawWindArrow(fromDeg: Int, color: Color) {
    val s = min(size.width, size.height)
    val c = center
    rotate(fromDeg + 180f, c) {
        val shaft = s * 0.36f
        drawLine(color, Offset(c.x, c.y + shaft), Offset(c.x, c.y - shaft), s * 0.1f, StrokeCap.Round)
        val head = Path().apply {
            moveTo(c.x, c.y - shaft - s * 0.08f)
            lineTo(c.x - s * 0.22f, c.y - shaft + s * 0.2f)
            lineTo(c.x + s * 0.22f, c.y - shaft + s * 0.2f)
            close()
        }
        drawPath(head, color)
    }
}
