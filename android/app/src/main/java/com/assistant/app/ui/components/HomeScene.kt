package com.assistant.app.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import com.assistant.app.ui.theme.AssistantBackground
import java.util.Random
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * The animated backdrop of the home page. Dark theme is a night: stars, a moon, a river with moonlight, swaying trees and
 * fireflies. Light theme is the same place by day: sun, drifting clouds, birds, a bright river and green trees.
 * One looping clock drives everything, so the scene costs a single canvas redraw per frame and never recomposes.
 */
@Composable
fun HomeScene(dark: Boolean, modifier: Modifier = Modifier.fillMaxSize()) {
    Crossfade(targetState = dark, animationSpec = tween(900), modifier = modifier, label = "home-scene") { isDark ->
        Scene(isDark, Modifier.fillMaxSize())
    }
}

private class TreeSpec(val u: Float, val left: Boolean, val scale: Float, val round: Boolean, val phase: Float)
private class StarSpec(val x: Float, val y: Float, val radius: Float, val phase: Float, val rate: Int)
private class FlySpec(val x: Float, val y: Float, val phaseA: Float, val phaseB: Float)
private class CloudSpec(val base: Float, val y: Float, val size: Float, val loops: Int)

/** Position within the 60 second loop; whole numbers of cycles keep every motion seamless when the loop restarts. */
private fun wave(t: Float, cycles: Int, phase: Float = 0f): Float = sin((t * cycles + phase) * 2f * PI.toFloat())

private fun riverCenter(u: Float, w: Float): Float = w * (0.5f + 0.09f * sin(u * 3.4f + 0.5f) * (0.35f + u))

private fun riverHalfWidth(u: Float, w: Float): Float = w * (0.03f + 0.31f * u.pow(1.25f))

@Composable
private fun Scene(dark: Boolean, modifier: Modifier) {
    val clock = rememberInfiniteTransition(label = "scene-clock")
    val time = clock.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(60_000, easing = LinearEasing), RepeatMode.Restart),
        label = "t"
    )
    val trees = remember {
        val rnd = Random(21)
        val us = listOf(0.06f, 0.16f, 0.29f, 0.43f, 0.6f, 0.78f, 0.97f)
        buildList {
            us.forEachIndexed { i, u ->
                add(TreeSpec(u, true, 0.8f + rnd.nextFloat() * 0.5f, i % 2 == 1, rnd.nextFloat()))
                add(TreeSpec(u + 0.04f, false, 0.8f + rnd.nextFloat() * 0.5f, i % 2 == 0, rnd.nextFloat()))
            }
        }.sortedBy { it.u }
    }
    val stars = remember {
        val rnd = Random(7)
        List(80) { StarSpec(rnd.nextFloat(), rnd.nextFloat() * 0.55f, 0.8f + rnd.nextFloat() * 1.6f, rnd.nextFloat(), 2 + rnd.nextInt(5)) }
    }
    val flies = remember {
        val rnd = Random(3)
        List(18) { FlySpec(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat()) }
    }
    val clouds = remember {
        listOf(CloudSpec(0.1f, 0.14f, 1.0f, 1), CloudSpec(0.55f, 0.25f, 0.8f, 2), CloudSpec(0.85f, 0.09f, 0.65f, 1), CloudSpec(0.3f, 0.34f, 0.7f, 2))
    }
    val ripples = remember {
        val rnd = Random(11)
        List(18) { floatArrayOf(rnd.nextFloat() * 2f - 1f, 0.25f + rnd.nextFloat() * 0.5f) }
    }
    Canvas(modifier) {
        drawScene(dark, time.value, trees, stars, flies, clouds, ripples)
    }
}

private fun DrawScope.drawScene(
    dark: Boolean,
    t: Float,
    trees: List<TreeSpec>,
    stars: List<StarSpec>,
    flies: List<FlySpec>,
    clouds: List<CloudSpec>,
    ripples: List<FloatArray>
) {
    val w = size.width
    val h = size.height
    val horizon = h * 0.62f
    val skyTop = AssistantBackground
    val skyHorizon = if (dark) Color(0xFF2A3F78) else Color(0xFFD6EEFA)

    // Sky
    drawRect(
        Brush.verticalGradient(
            listOf(skyTop, if (dark) Color(0xFF111B3C) else Color(0xFFDDF0FA), skyHorizon),
            startY = 0f, endY = horizon
        ),
        size = Size(w, horizon + 2f)
    )

    val bodyCenter = Offset(w * 0.72f, h * 0.2f)
    val bodyRadius = min(w, h) * 0.07f

    if (dark) {
        stars.forEach { s ->
            val a = 0.3f + 0.7f * ((wave(t, s.rate, s.phase) + 1f) / 2f)
            drawCircle(Color.White.copy(alpha = a), s.radius, Offset(s.x * w, s.y * h))
        }
        // Moon with a slowly breathing glow, drawn as a crescent.
        val pulse = 1f + 0.05f * wave(t, 6)
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFFBFD4FF).copy(alpha = 0.40f), Color.Transparent), bodyCenter, bodyRadius * 3.6f * pulse),
            bodyRadius * 3.6f * pulse, bodyCenter
        )
        val disc = Path().apply { addOval(Rect(bodyCenter, bodyRadius)) }
        val bite = Path().apply { addOval(Rect(bodyCenter + Offset(bodyRadius * 0.5f, -bodyRadius * 0.18f), bodyRadius * 0.9f)) }
        drawPath(Path.combine(PathOperation.Difference, disc, bite), Color(0xFFF4F1DE))
    } else {
        // Sun with turning rays.
        val pulse = 1f + 0.04f * wave(t, 6)
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFFFFE08A).copy(alpha = 0.55f), Color.Transparent), bodyCenter, bodyRadius * 3.4f * pulse),
            bodyRadius * 3.4f * pulse, bodyCenter
        )
        rotate(t * 360f, bodyCenter) {
            for (i in 0 until 12) {
                rotate(i * 30f, bodyCenter) {
                    drawLine(
                        Color(0xFFFFC93C).copy(alpha = 0.7f),
                        bodyCenter + Offset(0f, -bodyRadius * 1.4f), bodyCenter + Offset(0f, -bodyRadius * (if (i % 2 == 0) 1.95f else 1.7f)),
                        strokeWidth = 4f, cap = StrokeCap.Round
                    )
                }
            }
        }
        drawCircle(Color(0xFFFFC93C), bodyRadius, bodyCenter)
        drawCircle(Color(0xFFFFE27A), bodyRadius * 0.72f, bodyCenter)
    }

    // Clouds drift across; fainter and darker at night.
    val cloudColor = if (dark) Color(0xFF5B6BA0).copy(alpha = 0.28f) else Color.White.copy(alpha = 0.9f)
    clouds.forEach { c ->
        val x = (((c.base + t * 1.4f * c.loops) % 1.4f) - 0.2f) * w
        val y = c.y * h
        val s = min(w, h) * 0.07f * c.size
        drawOval(cloudColor, Offset(x - s * 1.6f, y - s * 0.3f), Size(s * 3.2f, s * 1.0f))
        drawOval(cloudColor, Offset(x - s * 0.9f, y - s * 0.8f), Size(s * 1.8f, s * 1.1f))
        drawOval(cloudColor, Offset(x + s * 0.1f, y - s * 0.6f), Size(s * 1.5f, s * 0.9f))
    }

    // Birds by day.
    if (!dark) {
        for (i in 0 until 3) {
            val x = (((0.1f + i * 0.17f + t * 1.2f * (1 + i % 2)) % 1.2f) - 0.1f) * w
            val y = h * (0.12f + 0.05f * i) + wave(t, 5 + i) * h * 0.008f
            val span = min(w, h) * (0.018f + 0.004f * i)
            val lift = wave(t, 40 + i * 3, i * 0.2f) * span * 0.5f
            val bird = Path().apply {
                moveTo(x - span, y - lift); quadraticBezierTo(x - span * 0.4f, y - span * 0.5f, x, y)
                quadraticBezierTo(x + span * 0.4f, y - span * 0.5f, x + span, y - lift)
            }
            drawPath(bird, Color(0xFF37474F).copy(alpha = 0.7f), style = Stroke(width = 2.6f, cap = StrokeCap.Round))
        }
    }

    // Far hills.
    fun hills(baseFrac: Float, amp: Float, phase: Float, color: Color) {
        val p = Path().apply {
            moveTo(0f, horizon + h * 0.03f)
            val steps = 28
            for (i in 0..steps) {
                val x = w * i / steps
                lineTo(x, h * baseFrac - sin(i * 0.45f + phase) * h * amp - sin(i * 0.21f + phase * 2f) * h * amp * 0.6f)
            }
            lineTo(w, horizon + h * 0.03f); close()
        }
        drawPath(p, color)
    }
    hills(0.585f, 0.018f, 0.6f, if (dark) Color(0xFF16284F) else Color(0xFFA7D6C0))
    hills(0.605f, 0.014f, 2.1f, if (dark) Color(0xFF10203E) else Color(0xFF86C3A0))

    // Ground
    drawRect(
        Brush.verticalGradient(
            listOf(if (dark) Color(0xFF0E2233) else Color(0xFF6FB866), if (dark) Color(0xFF07131E) else Color(0xFF3F8C4B)),
            startY = horizon, endY = h
        ),
        topLeft = Offset(0f, horizon), size = Size(w, h - horizon)
    )

    // River
    val samples = 30
    val left = ArrayList<Offset>(samples + 1)
    val right = ArrayList<Offset>(samples + 1)
    for (i in 0..samples) {
        val u = i / samples.toFloat()
        val y = horizon + (h - horizon) * u
        left += Offset(riverCenter(u, w) - riverHalfWidth(u, w), y)
        right += Offset(riverCenter(u, w) + riverHalfWidth(u, w), y)
    }
    val river = Path().apply {
        moveTo(left[0].x, left[0].y)
        left.drop(1).forEach { lineTo(it.x, it.y) }
        right.reversed().forEach { lineTo(it.x, it.y) }
        close()
    }
    drawPath(
        river,
        Brush.verticalGradient(
            listOf(if (dark) Color(0xFF2A4A86) else Color(0xFF8AD0F0), if (dark) Color(0xFF0C2146) else Color(0xFF2E8FCC)),
            startY = horizon, endY = h
        )
    )
    val shine = if (dark) Color(0xFFCFE0FF) else Color.White
    // Light of the moon or sun lying on the water.
    val glint = if (dark) Color(0xFFF4F1DE) else Color(0xFFFFF1B0)
    for (k in 0 until 9) {
        val u = 0.02f + k * 0.05f
        val y = horizon + (h - horizon) * u
        val lineW = riverHalfWidth(u, w) * (0.55f + 0.4f * wave(t, 12, k * 0.13f))
        val c = riverCenter(u, w)
        drawLine(glint.copy(alpha = 0.6f * (1f - k / 10f)), Offset(c - lineW, y), Offset(c + lineW, y), strokeWidth = 2f + 4f * u, cap = StrokeCap.Round)
    }
    // Ripples travel downstream toward the viewer.
    ripples.forEachIndexed { i, r ->
        val p = (t * 8f + i / ripples.size.toFloat()) % 1f
        val u = p.pow(1.4f)
        val y = horizon + (h - horizon) * u
        val hw = riverHalfWidth(u, w)
        val x = riverCenter(u, w) + r[0] * hw * 0.8f
        val len = hw * r[1] * 0.5f
        val a = sin(p * PI.toFloat()) * 0.5f
        drawLine(shine.copy(alpha = a), Offset(x - len, y), Offset(x + len, y), strokeWidth = 1.5f + 3f * u, cap = StrokeCap.Round)
    }

    // Trees from far to near, swaying in the wind.
    val farTint = skyHorizon
    trees.forEach { tr ->
        val u = tr.u.coerceAtMost(1.02f)
        val y = horizon + (h - horizon) * u
        val hw = riverHalfWidth(u.coerceAtMost(1f), w)
        val offset = hw + w * (0.02f + 0.07f * u) * (0.6f + tr.scale * 0.4f)
        val cx = riverCenter(u.coerceAtMost(1f), w) + if (tr.left) -offset else offset
        val height = h * (0.05f + 0.24f * u) * tr.scale
        val sway = wave(t, 6, tr.phase) * (1.2f + 1.6f * u)
        val base = if (tr.round) (if (dark) Color(0xFF0D2432) else Color(0xFF3C8D48)) else (if (dark) Color(0xFF0A1A26) else Color(0xFF1F6B3A))
        val color = lerp(base, farTint, ((1f - u) * 0.45f).coerceIn(0f, 0.45f))
        if (tr.round) roundTree(cx, y, height, sway, color, if (dark) null else Color(0xFF6BBF63))
        else pine(cx, y, height, sway, color)
    }

    // Fireflies at night.
    if (dark) {
        flies.forEach { f ->
            val x = f.x * w + wave(t, 3, f.phaseA) * w * 0.03f
            val y = h * (0.64f + 0.32f * f.y) + wave(t, 4, f.phaseB) * h * 0.015f
            val a = 0.3f + 0.7f * ((wave(t, 6, f.phaseA) + 1f) / 2f)
            drawCircle(Color(0xFFE8F77A).copy(alpha = a * 0.3f), 11f, Offset(x, y))
            drawCircle(Color(0xFFF3FF9A).copy(alpha = a), 3f, Offset(x, y))
        }
    }
}

private fun DrawScope.pine(cx: Float, baseY: Float, height: Float, sway: Float, color: Color) {
    val width = height * 0.42f
    rotate(sway, Offset(cx, baseY)) {
        drawRect(color, Offset(cx - width * 0.05f, baseY - height * 0.16f), Size(width * 0.1f, height * 0.16f))
        for (i in 0 until 3) {
            val bottom = baseY - height * (0.12f + i * 0.24f)
            val top = bottom - height * 0.40f
            val half = width * (0.5f - i * 0.12f)
            val tier = Path().apply { moveTo(cx, top); lineTo(cx + half, bottom); lineTo(cx - half, bottom); close() }
            drawPath(tier, color)
        }
    }
}

private fun DrawScope.roundTree(cx: Float, baseY: Float, height: Float, sway: Float, color: Color, highlight: Color?) {
    val width = height * 0.5f
    rotate(sway, Offset(cx, baseY)) {
        drawRect(color, Offset(cx - width * 0.06f, baseY - height * 0.4f), Size(width * 0.12f, height * 0.4f))
        drawOval(color, Offset(cx - width * 0.5f, baseY - height), Size(width, height * 0.72f))
        drawCircle(color, width * 0.32f, Offset(cx - width * 0.26f, baseY - height * 0.42f))
        drawCircle(color, width * 0.32f, Offset(cx + width * 0.26f, baseY - height * 0.42f))
        highlight?.let { drawCircle(it.copy(alpha = 0.45f), width * 0.2f, Offset(cx - width * 0.12f, baseY - height * 0.78f)) }
    }
}
