package com.assistant.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Custom rounded-stroke nav icons (24x24 viewport). Colour comes from the `Icon` tint. */
object AssistantIcons {

    /** Soft rounded house with an orb "door", echoing the voice-orb identity. */
    val Home: ImageVector by lazy {
        icon("AssistantHome") {
            path(stroke = ink, strokeLineWidth = STROKE, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(4.5f, 10.5f)
                lineTo(12f, 4f)
                lineTo(19.5f, 10.5f)
                verticalLineTo(18.5f)
                quadTo(19.5f, 20.5f, 17.5f, 20.5f)
                horizontalLineTo(6.5f)
                quadTo(4.5f, 20.5f, 4.5f, 18.5f)
                close()
                circle(12f, 14.5f, 2.6f)
            }
        }
    }

    /** Open book with a small spark: knowledge that "lights up" the assistant. */
    val Knowledge: ImageVector by lazy {
        icon("AssistantKnowledge") {
            path(stroke = ink, strokeLineWidth = STROKE, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(12f, 8.5f)
                curveTo(10.5f, 7f, 7.5f, 6.5f, 4f, 7f)
                verticalLineTo(19f)
                curveTo(7.5f, 18.5f, 10.5f, 19f, 12f, 20.5f)
                curveTo(13.5f, 19f, 16.5f, 18.5f, 20f, 19f)
                verticalLineTo(7f)
                curveTo(16.5f, 6.5f, 13.5f, 7f, 12f, 8.5f)
                verticalLineTo(20.5f)
                moveTo(19f, 1.5f)
                verticalLineTo(4.5f)
                moveTo(17.5f, 3f)
                horizontalLineTo(20.5f)
            }
        }
    }

    /** Ring with a sound-wave inside: past conversations. */

    /** Three sliders instead of a gear. */
    val Settings: ImageVector by lazy {
        icon("AssistantSettings") {
            path(stroke = ink, strokeLineWidth = STROKE, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(4f, 6f); horizontalLineTo(12.8f)
                moveTo(17.2f, 6f); horizontalLineTo(20f)
                circle(15f, 6f, 2.2f)
                moveTo(4f, 12f); horizontalLineTo(5.8f)
                moveTo(10.2f, 12f); horizontalLineTo(20f)
                circle(8f, 12f, 2.2f)
                moveTo(4f, 18f); horizontalLineTo(11.8f)
                moveTo(16.2f, 18f); horizontalLineTo(20f)
                circle(14f, 18f, 2.2f)
            }
        }
    }

    private const val STROKE = 1.8f
    private val ink = SolidColor(Color.Black)

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcToRelative(r, r, 0f, true, true, 2 * r, 0f)
        arcToRelative(r, r, 0f, true, true, -2 * r, 0f)
        close()
    }
}
