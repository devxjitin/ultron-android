package com.assistant.app.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import com.assistant.app.ui.theme.AssistantBackground
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * The backdrop of the home page: a mountain valley with a river, drawn in layers so it reads as a photograph rather
 * than a flat illustration. Dark theme is a moonlit night with stars, drifting clouds, mist, moonlight on the water,
 * swaying pines and fireflies. Light theme is the same valley in morning sun with light shafts, soft clouds and
 * sparkling water.
 *
 * Four stacked canvases keep it cheap: the sky and the landscape never change, so Compose keeps them as recorded
 * drawing and only the two animated layers are redrawn each frame from one looping clock.
 */
@Composable
fun HomeScene(dark: Boolean, modifier: Modifier = Modifier.fillMaxSize()) {
    Crossfade(targetState = dark, animationSpec = tween(900), modifier = modifier, label = "home-scene") { isDark ->
        Scene(isDark, Modifier.fillMaxSize())
    }
}

private class TreeSpec(val u: Float, val left: Boolean, val scale: Float, val round: Boolean, val phase: Float, val jitter: FloatArray)
private class StarSpec(val x: Float, val y: Float, val radius: Float, val phase: Float, val rate: Int, val bright: Boolean)
private class FlySpec(val x: Float, val y: Float, val phaseA: Float, val phaseB: Float)
private class Blob(val dx: Float, val dy: Float, val r: Float)
private class CloudSpec(val base: Float, val y: Float, val size: Float, val loops: Int, val blobs: List<Blob>)
private class BladeSpec(val x: Float, val height: Float, val lean: Float, val phase: Float)
private class SparkleSpec(val u: Float, val f: Float, val phase: Float, val rate: Int)

private class Geometry {
    val farRidge = makeRidge(5, 0.55f)
    val midRidge = makeRidge(12, 0.5f)
    val nearRidge = makeRidge(31, 0.47f)
    val trees: List<TreeSpec>
    val stars: List<StarSpec>
    val flies: List<FlySpec>
    val clouds: List<CloudSpec>
    val blades: List<BladeSpec>
    val sparkles: List<SparkleSpec>
    val ripples: List<FloatArray>

    init {
        val rnd = Random(21)
        val list = ArrayList<TreeSpec>()
        for (i in 0 until 12) {
            val u = 0.03f + i * 0.085f + rnd.nextFloat() * 0.03f
            for (left in listOf(true, false)) {
                val round = rnd.nextFloat() < 0.28f
                list += TreeSpec(
                    u + if (left) 0f else 0.035f, left, 0.7f + rnd.nextFloat() * 0.65f, round, rnd.nextFloat(),
                    FloatArray(6) { rnd.nextFloat() }
                )
            }
        }
        trees = list.sortedBy { it.u }
        val sr = Random(7)
        stars = List(110) {
            StarSpec(sr.nextFloat(), sr.nextFloat() * 0.56f, 0.7f + sr.nextFloat() * 1.5f, sr.nextFloat(), 2 + sr.nextInt(6), sr.nextFloat() > 0.93f)
        }
        val fr = Random(3)
        flies = List(20) { FlySpec(fr.nextFloat(), fr.nextFloat(), fr.nextFloat(), fr.nextFloat()) }
        val cr = Random(9)
        clouds = List(5) { i ->
            CloudSpec(
                base = cr.nextFloat() * 1.4f, y = 0.1f + cr.nextFloat() * 0.3f, size = 0.7f + cr.nextFloat() * 0.8f,
                loops = 1 + i % 2,
                blobs = List(13) { Blob((cr.nextFloat() - 0.5f) * 4.2f, (cr.nextFloat() - 0.6f) * 0.9f, 0.55f + cr.nextFloat() * 0.6f) }
            )
        }
        val br = Random(15)
        blades = List(90) { BladeSpec(br.nextFloat(), 0.04f + br.nextFloat() * 0.07f, (br.nextFloat() - 0.5f) * 0.03f, br.nextFloat()) }
        val pr = Random(17)
        sparkles = List(46) { SparkleSpec(0.06f + pr.nextFloat() * 0.94f, pr.nextFloat() * 2f - 1f, pr.nextFloat(), 3 + pr.nextInt(6)) }
        val rr = Random(11)
        ripples = List(16) { floatArrayOf(rr.nextFloat() * 2f - 1f, 0.2f + rr.nextFloat() * 0.5f) }
    }
}

private fun makeRidge(seed: Long, roughness: Float): FloatArray {
    val n = 128
    val rnd = Random(seed)
    val a = FloatArray(n + 1)
    a[0] = rnd.nextFloat(); a[n] = rnd.nextFloat()
    var step = n
    var amp = 1f
    while (step > 1) {
        val half = step / 2
        var i = half
        while (i < n) {
            a[i] = (a[i - half] + a[i + half]) / 2f + (rnd.nextFloat() - 0.5f) * amp
            i += step
        }
        amp *= roughness
        step = half
    }
    val lo = a.minOrNull() ?: 0f
    val span = max((a.maxOrNull() ?: 1f) - lo, 0.0001f)
    return FloatArray(n + 1) { (a[it] - lo) / span }
}

/** Position within the 60 second loop; whole numbers of cycles keep every motion seamless when the loop restarts. */
private fun wave(t: Float, cycles: Int, phase: Float = 0f): Float = sin((t * cycles + phase) * 2f * PI.toFloat())

private fun riverCenter(u: Float, w: Float): Float = w * (0.5f + 0.09f * sin(u * 3.4f + 0.5f) * (0.35f + u))

private fun riverHalfWidth(u: Float, w: Float): Float = w * (0.03f + 0.31f * u.pow(1.25f))

private fun DrawScope.glow(center: Offset, radius: Float, color: Color, alpha: Float) {
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center, radius), radius, center)
}

private class Look(val dark: Boolean) {
    val skyStops = if (dark) {
        listOf(AssistantBackground, Color(0xFF0A1429), Color(0xFF16264A), Color(0xFF2E4672))
    } else {
        listOf(Color(0xFF6FB3E6), Color(0xFF9CCBEC), Color(0xFFD2E8F2), Color(0xFFF3F1E4))
    }
    val haze = skyStops.last()
    val bodyColor = if (dark) Color(0xFFBFD4FF) else Color(0xFFFFE9A8)
    val glint = if (dark) Color(0xFFEFEAD2) else Color(0xFFFFF4C4)
}

@Composable
private fun Scene(dark: Boolean, modifier: Modifier) {
    val clock = rememberInfiniteTransition(label = "scene-clock")
    val time = clock.animateFloat(
        initialValue = 0f, targetValue = 1f,
        // Stepped to 30 updates a second: an unchanged value does not redraw, which halves the work and the battery use.
        animationSpec = infiniteRepeatable(tween(60_000, easing = Easing { f -> (f * 1800f).toInt() / 1800f }), RepeatMode.Restart),
        label = "t"
    )
    val geo = remember { Geometry() }
    val look = remember(dark) { Look(dark) }
    Box(modifier) {
        // Static layers: nothing inside reads the clock, so they are recorded once.
        Canvas(Modifier.fillMaxSize()) { drawSky(look) }
        Canvas(Modifier.fillMaxSize()) { drawSkyLife(look, time.value, geo) }
        Canvas(Modifier.fillMaxSize()) { drawLandscape(look, geo) }
        Canvas(Modifier.fillMaxSize()) { drawLandscapeLife(look, time.value, geo) }
    }
}

private val DrawScope.horizonY: Float get() = size.height * 0.6f

private fun DrawScope.bodyCenter() = Offset(size.width * 0.72f, size.height * 0.2f)

private fun DrawScope.bodyRadius() = min(size.width, size.height) * 0.062f

// ---------------------------------------------------------------- static sky

private fun DrawScope.drawSky(look: Look) {
    val w = size.width
    val h = size.height
    val hy = horizonY
    drawRect(
        Brush.verticalGradient(listOf(look.skyStops[0], look.skyStops[1], look.skyStops[2], look.skyStops[3]), startY = 0f, endY = hy + 4f),
        size = Size(w, hy + 4f)
    )
    val c = bodyCenter()
    val r = bodyRadius()
    if (look.dark) {
        // Faint band of the Milky Way across the sky.
        for (i in 0 until 26) {
            val f = i / 25f
            val p = Offset(w * (-0.05f + 1.1f * f), h * (0.42f - 0.34f * f + 0.03f * sin(f * 9f)))
            glow(p, min(w, h) * (0.16f + 0.05f * sin(f * 7f + 1f)), Color(0xFF8CA0D8), 0.045f)
        }
        // Light low on the horizon.
        glow(Offset(w * 0.62f, hy), w * 0.8f, Color(0xFF4C6FA8), 0.32f)
        // Moon: wide halo, soft bloom, then a lit disc with maria and a shaded side.
        glow(c, r * 7f, Color(0xFF8FA9E8), 0.22f)
        glow(c, r * 3.2f, Color(0xFFC4D6FF), 0.35f)
        val disc = Path().apply { addOval(Rect(c, r)) }
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFFCEB), Color(0xFFE6E1C8), Color(0xFFC9C4AA)), c - Offset(r * 0.25f, r * 0.2f), r * 1.3f), r, c)
        clipPath(disc) {
            val maria = Color(0xFF8E8A76)
            drawCircle(maria.copy(alpha = 0.38f), r * 0.34f, c + Offset(-r * 0.30f, -r * 0.20f))
            drawCircle(maria.copy(alpha = 0.32f), r * 0.26f, c + Offset(r * 0.18f, -r * 0.34f))
            drawCircle(maria.copy(alpha = 0.30f), r * 0.30f, c + Offset(-r * 0.05f, r * 0.30f))
            drawCircle(maria.copy(alpha = 0.26f), r * 0.16f, c + Offset(r * 0.42f, r * 0.12f))
            drawCircle(maria.copy(alpha = 0.22f), r * 0.12f, c + Offset(-r * 0.55f, r * 0.30f))
            // The unlit side stays faintly visible, like earthshine.
            drawCircle(Color(0xFF0B1630).copy(alpha = 0.82f), r * 1.05f, c + Offset(r * 1.45f, -r * 0.1f))
        }
    } else {
        glow(Offset(c.x, hy), w * 0.9f, Color(0xFFFFE6B0), 0.4f)
        glow(c, r * 9f, Color(0xFFFFE9B0), 0.28f)
        glow(c, r * 4f, Color(0xFFFFF0C4), 0.5f)
        glow(c, r * 1.9f, Color.White, 0.8f)
        drawCircle(Color(0xFFFFFBEB), r * 0.95f, c)
    }
}

// ---------------------------------------------------------------- animated sky

private fun DrawScope.drawSkyLife(look: Look, t: Float, geo: Geometry) {
    val w = size.width
    val h = size.height
    val dark = look.dark
    if (dark) {
        geo.stars.forEach { s ->
            val a = 0.25f + 0.75f * ((wave(t, s.rate, s.phase) + 1f) / 2f)
            val p = Offset(s.x * w, s.y * h)
            if (s.bright) {
                glow(p, 9f, Color(0xFFCFE0FF), 0.35f * a)
                val len = 6f * a
                drawLine(Color.White.copy(alpha = 0.7f * a), p - Offset(len, 0f), p + Offset(len, 0f), strokeWidth = 1f)
                drawLine(Color.White.copy(alpha = 0.7f * a), p - Offset(0f, len), p + Offset(0f, len), strokeWidth = 1f)
                drawCircle(Color.White.copy(alpha = a), s.radius + 0.6f, p)
            } else {
                drawCircle(Color(0xFFEAF0FF).copy(alpha = a * 0.85f), s.radius, p)
            }
        }
        // One shooting star each minute.
        val p = ((t - 0.3f) / 0.025f)
        if (p in 0f..1f) {
            val from = Offset(w * 0.18f, h * 0.09f)
            val to = Offset(w * 0.46f, h * 0.24f)
            val head = from + (to - from) * p
            val tail = from + (to - from) * max(0f, p - 0.25f)
            drawLine(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.9f)), tail, head), tail, head, strokeWidth = 2.2f, cap = StrokeCap.Round)
        }
    } else {
        // Soft light shafts from the sun, breathing slowly.
        val c = bodyCenter()
        val shimmer = 0.55f + 0.2f * wave(t, 3)
        for (i in 0 until 5) {
            val angle = 118f + i * 11f
            rotate(angle - 90f, c) {
                val len = h * 0.95f
                val half = w * (0.05f + 0.02f * i)
                val path = Path().apply { moveTo(c.x - 4f, c.y); lineTo(c.x - half, c.y + len); lineTo(c.x + half, c.y + len); lineTo(c.x + 4f, c.y); close() }
                drawPath(path, Brush.verticalGradient(listOf(Color(0xFFFFF3C8).copy(alpha = 0.16f * shimmer), Color.Transparent), c.y, c.y + len))
            }
        }
    }
    // Volumetric clouds: many soft blobs, shaded underneath and lit from the moon or the sun.
    val lit = if (dark) Color(0xFFA9BDE8) else Color.White
    val shade = if (dark) Color(0xFF1B2A52) else Color(0xFF9FB6CC)
    geo.clouds.forEach { cl ->
        val x = (((cl.base + t * 1.6f * cl.loops) % 1.6f) - 0.3f) * w
        val y = cl.y * h
        val s = min(w, h) * 0.06f * cl.size
        cl.blobs.forEach { b ->
            val p = Offset(x + b.dx * s, y + b.dy * s + s * 0.35f)
            glow(p, s * b.r * 1.7f, shade, if (dark) 0.2f else 0.3f)
        }
        cl.blobs.forEach { b ->
            val p = Offset(x + b.dx * s, y + b.dy * s)
            glow(p, s * b.r * 1.6f, lit, if (dark) 0.2f else 0.55f)
        }
    }
}

// ---------------------------------------------------------------- static landscape

private fun DrawScope.ridge(r: FloatArray, baseY: Float, amp: Float, color: Color, fog: Color, fogAlpha: Float, rim: Color) {
    val w = size.width
    val path = Path().apply {
        moveTo(0f, baseY + amp)
        r.forEachIndexed { i, v -> lineTo(w * i / (r.size - 1), baseY - v * amp) }
        lineTo(w, baseY + amp); close()
    }
    drawPath(path, color)
    clipPath(path) {
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, fog.copy(alpha = fogAlpha)), startY = baseY - amp, endY = baseY + amp * 0.1f))
    }
    val line = Path().apply { r.forEachIndexed { i, v -> val x = w * i / (r.size - 1); val y = baseY - v * amp; if (i == 0) moveTo(x, y) else lineTo(x, y) } }
    drawPath(line, rim, style = Stroke(width = 1.6f))
}

private fun DrawScope.treeline(seed: Long, baseY: Float, minH: Float, maxH: Float, color: Color) {
    val rnd = Random(seed)
    val w = size.width
    val path = Path().apply {
        moveTo(0f, baseY + maxH)
        lineTo(0f, baseY)
        var x = 0f
        while (x < w) {
            val tw = w * (0.008f + rnd.nextFloat() * 0.012f)
            val th = minH + rnd.nextFloat() * (maxH - minH)
            lineTo(x + tw * 0.5f, baseY - th)
            lineTo(x + tw, baseY - th * 0.25f)
            x += tw
        }
        lineTo(w, baseY + maxH); close()
    }
    drawPath(path, color)
}

private fun DrawScope.riverPath(): Path {
    val w = size.width
    val h = size.height
    val hy = horizonY
    val n = 30
    val left = ArrayList<Offset>(n + 1)
    val right = ArrayList<Offset>(n + 1)
    for (i in 0..n) {
        val u = i / n.toFloat()
        val y = hy + (h - hy) * u
        left += Offset(riverCenter(u, w) - riverHalfWidth(u, w), y)
        right += Offset(riverCenter(u, w) + riverHalfWidth(u, w), y)
    }
    return Path().apply {
        moveTo(left[0].x, left[0].y)
        left.drop(1).forEach { lineTo(it.x, it.y) }
        right.reversed().forEach { lineTo(it.x, it.y) }
        close()
    }
}

private fun DrawScope.drawLandscape(look: Look, geo: Geometry) {
    val w = size.width
    val h = size.height
    val hy = horizonY
    val dark = look.dark
    val haze = look.haze
    // Mountains fade into the haze with distance.
    ridge(geo.farRidge, hy - h * 0.005f, h * 0.2f, if (dark) Color(0xFF1B2B4D) else Color(0xFFA9C2D6), haze, 0.55f, if (dark) Color(0xFF8FA9E8).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.5f))
    ridge(geo.midRidge, hy + h * 0.005f, h * 0.15f, if (dark) Color(0xFF132241) else Color(0xFF86A8BD), haze, 0.5f, if (dark) Color(0xFF8FA9E8).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.35f))
    ridge(geo.nearRidge, hy + h * 0.02f, h * 0.1f, if (dark) Color(0xFF0C1832) else Color(0xFF668F8C), haze, 0.4f, Color.Transparent)
    // Distant forest in two rows, each softened by haze.
    treeline(41, hy + h * 0.012f, h * 0.012f, h * 0.034f, if (dark) Color(0xFF0B1A2C) else Color(0xFF5F8F83))
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, haze.copy(alpha = if (dark) 0.35f else 0.5f)), startY = hy - h * 0.03f, endY = hy + h * 0.02f), Offset(0f, hy - h * 0.03f), Size(w, h * 0.05f))
    treeline(57, hy + h * 0.03f, h * 0.018f, h * 0.05f, if (dark) Color(0xFF08141F) else Color(0xFF3F7A57))
    // Ground
    drawRect(
        Brush.verticalGradient(
            listOf(if (dark) Color(0xFF0A1A26) else Color(0xFF5C9F60), if (dark) Color(0xFF050D14) else Color(0xFF326F3F)),
            startY = hy, endY = h
        ),
        topLeft = Offset(0f, hy + h * 0.03f), size = Size(w, h - hy - h * 0.03f)
    )
    // River water mirrors the sky near the horizon and turns deep toward the viewer.
    val river = riverPath()
    drawPath(
        river,
        Brush.verticalGradient(
            listOf(look.haze.copy(alpha = if (dark) 0.85f else 0.95f), if (dark) Color(0xFF1B3562) else Color(0xFF7FC4E6), if (dark) Color(0xFF07132A) else Color(0xFF2B87BE)),
            startY = hy, endY = h
        )
    )
    // Wet dark bank edge.
    drawPath(river, Color.Black.copy(alpha = if (dark) 0.4f else 0.22f), style = Stroke(width = 3.5f))
}

// ---------------------------------------------------------------- animated landscape

private fun DrawScope.pine(cx: Float, baseY: Float, height: Float, sway: Float, brush: Brush, alpha: Float, jitter: FloatArray) {
    val width = height * 0.38f
    rotate(sway, Offset(cx, baseY)) {
        drawRect(brush, Offset(cx - width * 0.04f, baseY - height * 0.14f), Size(width * 0.08f, height * 0.14f), alpha)
        val tiers = 6
        for (i in 0 until tiers) {
            val f = i / (tiers - 1f)
            val bottom = baseY - height * (0.09f + f * 0.62f)
            val top = bottom - height * 0.3f * (1f - 0.3f * f)
            val half = width * 0.5f * (1f - f * 0.78f) * (0.85f + jitter[i] * 0.3f)
            val mid = (top + bottom) / 2f
            val path = Path().apply {
                moveTo(cx, top)
                lineTo(cx + half * 0.5f, mid + (jitter[i] - 0.5f) * height * 0.02f)
                lineTo(cx + half, bottom)
                lineTo(cx + half * 0.3f, bottom - (bottom - top) * 0.12f)
                lineTo(cx - half * 0.3f, bottom - (bottom - top) * 0.12f)
                lineTo(cx - half, bottom)
                lineTo(cx - half * 0.5f, mid - (jitter[i] - 0.5f) * height * 0.02f)
                close()
            }
            drawPath(path, brush, alpha)
        }
    }
}

private val RoundBlobs = listOf(
    floatArrayOf(-0.25f, -0.55f, 0.30f), floatArrayOf(0.25f, -0.55f, 0.30f), floatArrayOf(0f, -0.78f, 0.32f),
    floatArrayOf(-0.32f, -0.36f, 0.28f), floatArrayOf(0.32f, -0.36f, 0.28f), floatArrayOf(0f, -0.46f, 0.36f)
)

private fun DrawScope.roundTree(cx: Float, baseY: Float, height: Float, sway: Float, brush: Brush, alpha: Float, highlight: Color?) {
    val width = height * 0.62f
    rotate(sway, Offset(cx, baseY)) {
        drawRect(brush, Offset(cx - width * 0.05f, baseY - height * 0.4f), Size(width * 0.1f, height * 0.4f), alpha)
        RoundBlobs.forEach { b -> drawCircle(brush, width * b[2], Offset(cx + width * b[0], baseY + height * b[1]), alpha) }
        highlight?.let { c ->
            drawCircle(c.copy(alpha = c.alpha * alpha), width * 0.16f, Offset(cx - width * 0.15f, baseY - height * 0.86f))
            drawCircle(c.copy(alpha = c.alpha * alpha), width * 0.12f, Offset(cx + width * 0.1f, baseY - height * 0.6f))
        }
    }
}

private fun DrawScope.drawLandscapeLife(look: Look, t: Float, geo: Geometry) {
    val w = size.width
    val h = size.height
    val hy = horizonY
    val dark = look.dark
    val river = riverPath()
    val c = bodyCenter()

    // Mist drifting along the valley floor and over the water.
    val mist = if (dark) Color(0xFF8FA6D6) else Color.White
    for (i in 0 until 6) {
        val x = (((i * 0.31f + t * 1.0f * (1 + i % 2)) % 1.5f) - 0.25f) * w
        val y = h * (0.575f + 0.026f * i)
        val rad = w * (0.34f + 0.04f * (i % 3))
        scale(1f, 0.16f, Offset(x, y)) {
            drawCircle(Brush.radialGradient(listOf(mist.copy(alpha = if (dark) 0.2f else 0.34f), Color.Transparent), Offset(x, y), rad), rad, Offset(x, y))
        }
    }

    clipPath(river) {
        // Light of the moon or sun on the water: a broken column straight below it.
        for (k in 0 until 26) {
            val f = k / 25f
            val y = hy + (h - hy) * f.pow(1.4f)
            val u = (y - hy) / (h - hy)
            val half = bodyRadius() * (0.5f + 1.4f * u) * (0.55f + 0.45f * wave(t, 12, k * 0.17f))
            val x = c.x + wave(t, 9, k * 0.31f) * w * 0.012f * (0.4f + u)
            drawLine(look.glint.copy(alpha = (0.75f - 0.45f * f) * (0.55f + 0.45f * wave(t, 6, k * 0.23f))), Offset(x - half, y), Offset(x + half, y), strokeWidth = 1.6f + 5f * u, cap = StrokeCap.Round)
        }
        // Reflections of the trees, wobbling with the ripples.
        geo.trees.forEach { tr ->
            val u = tr.u.coerceAtMost(1f)
            val y = hy + (h - hy) * u
            val side = riverHalfWidth(u, w) + w * (0.02f + 0.06f * u) * (0.6f + tr.scale * 0.4f)
            val cx = riverCenter(u, w) + if (tr.left) -side else side
            val height = h * (0.05f + 0.25f * u) * tr.scale * if (tr.round) 0.8f else 1f
            val wobble = wave(t, 10, tr.phase + u) * (1.5f + 4f * u)
            val brush = Brush.verticalGradient(listOf(if (dark) Color(0xFF16253C) else Color(0xFF3F7F8E), if (dark) Color(0xFF040A12) else Color(0xFF14405A)), startY = y, endY = y + height)
            translate(left = wobble) {
                scale(1f, -0.9f, Offset(cx, y)) {
                    if (tr.round) roundTree(cx, y, height, 0f, brush, 0.4f, null) else pine(cx, y, height, 0f, brush, 0.4f, tr.jitter)
                }
            }
        }
        // Slow ripples running toward the viewer.
        geo.ripples.forEachIndexed { i, r ->
            val p = (t * 8f + i / geo.ripples.size.toFloat()) % 1f
            val u = p.pow(1.4f)
            val y = hy + (h - hy) * u
            val hw = riverHalfWidth(u, w)
            val x = riverCenter(u, w) + r[0] * hw * 0.8f
            val len = hw * r[1] * 0.5f
            drawLine(Color.White.copy(alpha = sin(p * PI.toFloat()) * (if (dark) 0.22f else 0.4f)), Offset(x - len, y), Offset(x + len, y), strokeWidth = 1.2f + 2.4f * u, cap = StrokeCap.Round)
        }
        if (!dark) {
            // Sunlight flickering on the water.
            geo.sparkles.forEach { s ->
                val a = wave(t, s.rate, s.phase).coerceAtLeast(0f).pow(4)
                if (a > 0.05f) {
                    val u = s.u
                    val y = hy + (h - hy) * u
                    val x = riverCenter(u, w) + s.f * riverHalfWidth(u, w) * 0.9f
                    val len = 3f + 9f * u
                    drawLine(Color.White.copy(alpha = a), Offset(x - len, y), Offset(x + len, y), strokeWidth = 1.5f + u * 2f, cap = StrokeCap.Round)
                    drawLine(Color.White.copy(alpha = a * 0.7f), Offset(x, y - len * 0.5f), Offset(x, y + len * 0.5f), strokeWidth = 1.2f)
                }
            }
        }
    }

    // Trees from far to near, swaying in the wind and lit from the moon or sun side.
    geo.trees.forEach { tr ->
        val u = tr.u.coerceAtMost(1.02f)
        val uc = u.coerceAtMost(1f)
        val y = hy + h * 0.02f + (h - hy - h * 0.02f) * u
        val side = riverHalfWidth(uc, w) + w * (0.02f + 0.07f * u) * (0.6f + tr.scale * 0.4f)
        val cx = riverCenter(uc, w) + if (tr.left) -side else side
        val height = h * (0.05f + 0.25f * u) * tr.scale
        val sway = wave(t, 6, tr.phase) * (0.8f + 1.5f * u)
        val far = ((1f - u) * 0.55f).coerceIn(0f, 0.55f)
        val topColor = lerp(if (tr.round) (if (dark) Color(0xFF1F3C4B) else Color(0xFF63B55C)) else (if (dark) Color(0xFF1B3445) else Color(0xFF3E8F4F)), look.haze, far)
        val baseColor = lerp(if (tr.round) (if (dark) Color(0xFF0A1B25) else Color(0xFF2D7A3C)) else (if (dark) Color(0xFF08141D) else Color(0xFF16502D)), look.haze, far * 0.8f)
        val brush = Brush.verticalGradient(listOf(topColor, baseColor), startY = y - height, endY = y)
        if (tr.round) roundTree(cx, y, height, sway, brush, 1f, if (dark) Color(0xFF8FB4D8).copy(alpha = 0.14f) else Color(0xFFD7F28A).copy(alpha = 0.3f))
        else pine(cx, y, height, sway, brush, 1f, tr.jitter)
    }

    // Foreground grass and reeds, bending together.
    val grassColor = if (dark) Color(0xFF040A10) else Color(0xFF1F5A2C)
    val cxBottom = riverCenter(1f, w)
    val hwBottom = riverHalfWidth(1f, w)
    geo.blades.forEach { b ->
        val x = b.x * w
        if (abs(x - cxBottom) < hwBottom * 0.92f) return@forEach
        val hh = b.height * h
        val bend = b.lean * w + wave(t, 5, b.phase) * hh * 0.14f
        val blade = Path().apply {
            moveTo(x - 3.5f, h + 2f)
            quadraticBezierTo(x + bend * 0.2f, h - hh * 0.5f, x + bend, h - hh)
            quadraticBezierTo(x + bend * 0.2f + 2f, h - hh * 0.5f, x + 3.5f, h + 2f)
            close()
        }
        drawPath(blade, grassColor)
    }

    if (dark) {
        geo.flies.forEach { f ->
            val x = f.x * w + wave(t, 3, f.phaseA) * w * 0.03f
            val y = h * (0.64f + 0.32f * f.y) + wave(t, 4, f.phaseB) * h * 0.015f
            val a = 0.3f + 0.7f * ((wave(t, 6, f.phaseA) + 1f) / 2f)
            glow(Offset(x, y), 13f, Color(0xFFE8F77A), 0.55f * a)
            drawCircle(Color(0xFFF7FFB0).copy(alpha = a), 2.4f, Offset(x, y))
        }
    } else {
        // Pollen and tiny insects floating in the sunlight.
        geo.flies.forEach { f ->
            val x = f.x * w + wave(t, 2, f.phaseA) * w * 0.04f
            val y = h * (0.45f + 0.5f * f.y) + wave(t, 3, f.phaseB) * h * 0.02f
            val a = 0.25f + 0.55f * ((wave(t, 4, f.phaseA) + 1f) / 2f)
            drawCircle(Color.White.copy(alpha = a * 0.6f), 2.2f, Offset(x, y))
        }
    }

    // Soft vignette, like a lens.
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = if (dark) 0.5f else 0.22f)), Offset(w / 2f, h * 0.45f), max(w, h) * 0.78f))
}
