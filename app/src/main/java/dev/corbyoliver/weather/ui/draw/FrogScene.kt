package dev.corbyoliver.weather.ui.draw

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import dev.corbyoliver.weather.domain.FrogState
import dev.corbyoliver.weather.ui.theme.Tokens

/*
 * The frog: an original illustration of a small Pacific tree frog, drawn in
 * code so the same drawing scales on the hero card and renders into widget
 * bitmaps. All sizes are in units of u = height / 140.
 */

private val MouthColor = Color(0xFF3E7F3A)
private val StripeColor = Color(0xFF3B5A35)
private val Cheek = Color(0x55F29C9C)
private val Shades = Color(0xFF15171D)
private val LeafVein = Color(0xFF4FB07A)
private val Pompom = Color(0xFFE9E3F7)

/** Fixed pseudo-random points so the scene looks the same on every frame. */
private val Scatter: List<Pair<Float, Float>> = run {
    var seed = 7L
    fun next(): Float { seed = (seed * 1103515245L + 12345L) and 0x7fffffffL; return (seed % 10000L) / 10000f }
    List(40) { next() to next() }
}

/**
 * Draws the whole scene: sky details for [state], the ground, and the frog.
 * [frogX] is the frog's centre as a fraction of the width.
 * [showGround] is off for small widget renders that want only the frog.
 */
fun DrawScope.drawFrogScene(state: FrogState, frogX: Float = 0.72f, showGround: Boolean = true) {
    val w = size.width
    val h = size.height
    val u = h / 140f
    val cx = w * frogX
    val by = h - 10f * u

    drawSky(state, w, h, u, cx, by)
    if (showGround) {
        drawOval(Tokens.Ground, topLeft = Offset(-0.15f * w, h - 22f * u), size = Size(1.3f * w, 60f * u))
        drawOval(Tokens.GroundLight, topLeft = Offset(cx - 44f * u, by - 8f * u), size = Size(88f * u, 18f * u))
    }
    drawFrog(state, cx, by, u)
}

private fun DrawScope.drawSky(state: FrogState, w: Float, h: Float, u: Float, cx: Float, by: Float) {
    when (state) {
        FrogState.RAIN -> Scatter.forEach { (fx, fy) ->
            val x = fx * w
            val y = fy * (h - 20f * u)
            val underLeaf = x > cx - 50f * u && x < cx + 50f * u && y > by - 112f * u
            if (!underLeaf) {
                drawLine(Tokens.Accent.copy(alpha = 0.55f), Offset(x, y), Offset(x - 3f * u, y + 10f * u), 1.6f * u, StrokeCap.Round)
            }
        }
        FrogState.WINDY -> {
            for (i in 0..3) {
                val y = (22f + i * 18f) * u
                val x0 = (0.04f + 0.09f * (i % 2)) * w
                val path = Path().apply {
                    moveTo(x0, y)
                    cubicTo(x0 + 0.15f * w, y - 6f * u, x0 + 0.3f * w, y + 6f * u, x0 + 0.42f * w, y)
                }
                drawPath(path, Tokens.TextMuted.copy(alpha = 0.45f), style = Stroke(2f * u, cap = StrokeCap.Round))
            }
        }
        FrogState.COLD_SNAP -> Scatter.forEachIndexed { i, (fx, fy) ->
            val c = Offset(fx * w, fy * (h - 16f * u))
            if (i % 3 == 0) flake(c, 3.2f * u) else drawCircle(Tokens.Snow.copy(alpha = 0.85f), 1.6f * u, c)
        }
        FrogState.CLEAR_NIGHT -> {
            moon(Offset(0.18f * w, 36f * u), 16f * u)
            Scatter.take(18).forEach { (fx, fy) ->
                val c = Offset(fx * w, fy * 70f * u + 6f * u)
                if (c.x > 0.3f * w || c.y > 60f * u) drawCircle(Tokens.Snow.copy(alpha = 0.4f + 0.5f * fx), (0.8f + fy) * u, c)
            }
        }
        FrogState.CLEAR_DAY -> sun(Offset(0.2f * w, 40f * u), 13f * u)
        FrogState.CLOUDY -> {
            cloud(Offset(0.16f * w, 42f * u), 70f * u, Color(0xFF5E6372))
            cloud(Offset(0.42f * w, 26f * u), 56f * u, Color(0xFF7C8090))
        }
    }
}

private fun DrawScope.drawFrog(state: FrogState, cx: Float, by: Float, u: Float) {
    fun o(dx: Float, dy: Float) = Offset(cx + dx * u, by + dy * u)
    fun oval(color: Color, x: Float, y: Float, ww: Float, hh: Float) =
        drawOval(color, topLeft = o(x - ww / 2, y - hh / 2), size = Size(ww * u, hh * u))

    if (state == FrogState.RAIN) drawLeafUmbrella(cx, by, u)

    // Back legs, body, belly, feet
    oval(Tokens.FrogShade, -24f, -10f, 26f, 16f)
    oval(Tokens.FrogShade, 24f, -10f, 26f, 16f)
    oval(Tokens.FrogBody, 0f, -24f, 50f, 42f)
    oval(Tokens.FrogBelly, 0f, -19f, 30f, 28f)
    oval(Tokens.FrogShade, -12f, -3f, 14f, 6f)
    oval(Tokens.FrogShade, 12f, -3f, 14f, 6f)
    if (state == FrogState.RAIN) oval(Tokens.FrogShade, 23f, -27f, 9f, 9f) // hand on the stem

    // Head
    oval(Tokens.FrogBody, 0f, -48f, 60f, 36f)
    if (state == FrogState.COLD_SNAP) drawBeanie(cx, by, u)
    drawCircle(Tokens.FrogBody, 10f * u, o(-15f, -62f))
    drawCircle(Tokens.FrogBody, 10f * u, o(15f, -62f))
    // The tree frog's dark stripe behind each eye
    drawLine(StripeColor, o(-23f, -59f), o(-30f, -47f), 2.4f * u, StrokeCap.Round)
    drawLine(StripeColor, o(23f, -59f), o(30f, -47f), 2.4f * u, StrokeCap.Round)
    drawCircle(Cheek, 3.6f * u, o(-20f, -45f))
    drawCircle(Cheek, 3.6f * u, o(20f, -45f))
    drawCircle(StripeColor, 0.9f * u, o(-4f, -55f))
    drawCircle(StripeColor, 0.9f * u, o(4f, -55f))

    // Mouth: a calm smile
    drawArc(
        MouthColor, startAngle = 20f, sweepAngle = 140f, useCenter = false,
        topLeft = o(-13f, -55f), size = Size(26f * u, 10f * u),
        style = Stroke(2f * u, cap = StrokeCap.Round),
    )

    when (state) {
        FrogState.CLEAR_NIGHT -> {
            for (sx in listOf(-15f, 15f)) {
                drawArc(Tokens.Ink, 10f, 160f, false, topLeft = o(sx - 6f, -66f), size = Size(12f * u, 8f * u), style = Stroke(2f * u, cap = StrokeCap.Round))
            }
            drawZ(o(26f, -82f), 5f * u, u)
            drawZ(o(36f, -94f), 7f * u, u)
        }
        FrogState.WINDY -> {
            // Squinting: > <
            for (sx in listOf(-15f, 15f)) {
                val dir = if (sx < 0) 1f else -1f
                drawLine(Tokens.Ink, o(sx - 5f * dir, -66f), o(sx + 4f * dir, -62f), 2.2f * u, StrokeCap.Round)
                drawLine(Tokens.Ink, o(sx + 4f * dir, -62f), o(sx - 5f * dir, -58f), 2.2f * u, StrokeCap.Round)
            }
            drawBlowingLeaf(o(-62f, -70f), u)
        }
        FrogState.CLEAR_DAY -> {
            for (sx in listOf(-15f, 15f)) {
                drawRoundRect(Shades, topLeft = o(sx - 9f, -68f), size = Size(18f * u, 12f * u), cornerRadius = CornerRadius(5f * u))
                drawLine(Color.White.copy(alpha = 0.35f), o(sx - 5f, -65f), o(sx - 1f, -65f), 1.4f * u, StrokeCap.Round)
            }
            drawLine(Shades, o(-6f, -64f), o(6f, -64f), 2f * u)
        }
        else -> {
            for (sx in listOf(-15f, 15f)) {
                drawCircle(Tokens.Ink, 6.5f * u, o(sx, -62f))
                drawCircle(Color.White.copy(alpha = 0.9f), 2f * u, o(sx - 2f, -64.5f))
            }
        }
    }
}

private fun DrawScope.drawLeafUmbrella(cx: Float, by: Float, u: Float) {
    fun o(dx: Float, dy: Float) = Offset(cx + dx * u, by + dy * u)
    val stem = Path().apply {
        moveTo(o(24f, -24f).x, o(24f, -24f).y)
        quadraticTo(o(30f, -64f).x, o(30f, -64f).y, o(2f, -96f).x, o(2f, -96f).y)
    }
    drawPath(stem, Tokens.Leaf, style = Stroke(2.4f * u, cap = StrokeCap.Round))
    val canopy = Path().apply {
        moveTo(o(-48f, -92f).x, o(-48f, -92f).y)
        quadraticTo(o(0f, -128f).x, o(0f, -128f).y, o(48f, -92f).x, o(48f, -92f).y)
        quadraticTo(o(36f, -98f).x, o(36f, -98f).y, o(24f, -93f).x, o(24f, -93f).y)
        quadraticTo(o(12f, -99f).x, o(12f, -99f).y, o(0f, -94f).x, o(0f, -94f).y)
        quadraticTo(o(-12f, -99f).x, o(-12f, -99f).y, o(-24f, -93f).x, o(-24f, -93f).y)
        quadraticTo(o(-36f, -98f).x, o(-36f, -98f).y, o(-48f, -92f).x, o(-48f, -92f).y)
        close()
    }
    drawPath(canopy, Tokens.Leaf)
    drawLine(LeafVein, o(0f, -95f), o(0f, -109f), 1.4f * u, StrokeCap.Round)
    for (sx in listOf(-1f, 1f)) {
        drawLine(LeafVein, o(0f, -103f), o(22f * sx, -101f), 1.2f * u, StrokeCap.Round)
        drawLine(LeafVein, o(0f, -99f), o(36f * sx, -95f), 1.2f * u, StrokeCap.Round)
    }
}

private fun DrawScope.drawBeanie(cx: Float, by: Float, u: Float) {
    fun o(dx: Float, dy: Float) = Offset(cx + dx * u, by + dy * u)
    drawArc(Tokens.Beanie, 180f, 180f, true, topLeft = o(-25f, -96f), size = Size(50f * u, 40f * u))
    for (i in -2..2) drawLine(Tokens.BeanieDark, o(i * 8f, -77f), o(i * 6f, -92f + kotlin.math.abs(i) * 2f), 1.6f * u, StrokeCap.Round)
    drawRoundRect(Tokens.BeanieDark, topLeft = o(-28f, -80f), size = Size(56f * u, 9f * u), cornerRadius = CornerRadius(4.5f * u))
    drawCircle(Pompom, 6f * u, o(0f, -97f))
}

private fun DrawScope.drawBlowingLeaf(c: Offset, u: Float) {
    rotate(-35f, c) {
        val leaf = Path().apply {
            moveTo(c.x - 12f * u, c.y)
            quadraticTo(c.x, c.y - 9f * u, c.x + 12f * u, c.y)
            quadraticTo(c.x, c.y + 9f * u, c.x - 12f * u, c.y)
            close()
        }
        drawPath(leaf, Tokens.Leaf)
        drawLine(LeafVein, Offset(c.x - 10f * u, c.y), Offset(c.x + 10f * u, c.y), 1.2f * u)
    }
    for (i in 0..2) {
        val y = c.y - 4f * u + i * 4f * u
        drawLine(Tokens.TextMuted.copy(alpha = 0.6f), Offset(c.x + 16f * u, y), Offset(c.x + (26f + i * 4f) * u, y), 1.4f * u, StrokeCap.Round)
    }
}

private fun DrawScope.drawZ(c: Offset, s: Float, u: Float) {
    val p = Path().apply {
        moveTo(c.x - s / 2, c.y - s / 2); lineTo(c.x + s / 2, c.y - s / 2)
        lineTo(c.x - s / 2, c.y + s / 2); lineTo(c.x + s / 2, c.y + s / 2)
    }
    drawPath(p, Tokens.TextMuted, style = Stroke(1.6f * u, cap = StrokeCap.Round))
}

