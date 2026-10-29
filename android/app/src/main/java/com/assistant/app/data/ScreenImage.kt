package com.assistant.app.data

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

/** Small helpers for a screenshot taken by the accessibility service before it goes to the model. */
object ScreenImage {

    /** True when the picture is (almost) all black, which is how a protected window can appear. */
    fun looksBlank(bitmap: Bitmap): Boolean {
        val steps = 24
        for (i in 0 until steps) {
            for (j in 0 until steps) {
                val pixel = bitmap.getPixel((bitmap.width - 1) * i / (steps - 1), (bitmap.height - 1) * j / (steps - 1))
                val brightest = maxOf((pixel shr 16) and 0xFF, (pixel shr 8) and 0xFF, pixel and 0xFF)
                if (brightest > 10) return false
            }
        }
        return true
    }

    /** A JPEG small enough to send to the model quickly. */
    fun toJpeg(bitmap: Bitmap, maxSide: Int = 1280): ByteArray {
        val scale = maxSide.toFloat() / maxOf(bitmap.width, bitmap.height)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else bitmap
        return ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
    }
}
