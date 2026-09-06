package com.liquorbee.invoicescanner.imaging

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Port of the desktop app's Scanning/ImagePipeline.cs (System.Drawing) to Android's Bitmap/Canvas
 * APIs - same effective behavior: grayscale, downscale to a max long edge, JPEG-encode at a fixed
 * quality, plus a small thumbnail. Not optional per the original's own comment: a raw phone-camera
 * photo is far larger than Claude's per-image limits and the API's request-time budget need.
 */
object ImagePipeline {

    data class ProcessedImage(val bytes: ByteArray, val thumbnailBytes: ByteArray)

    // Same luminance weights as the desktop's grayscale ColorMatrix (0.30/0.59/0.11).
    private val GRAYSCALE_MATRIX = ColorMatrix(
        floatArrayOf(
            0.30f, 0.59f, 0.11f, 0f, 0f,
            0.30f, 0.59f, 0.11f, 0f, 0f,
            0.30f, 0.59f, 0.11f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    )

    fun process(source: Bitmap, maxLongEdgePx: Int, jpegQuality: Int): ProcessedImage {
        val scale = min(1.0, maxLongEdgePx.toDouble() / max(source.width, source.height))
        val targetWidth = max(1, (source.width * scale).roundToInt())
        val targetHeight = max(1, (source.height * scale).roundToInt())

        val resized = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resized)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(GRAYSCALE_MATRIX)
        }
        val srcRect = android.graphics.Rect(0, 0, source.width, source.height)
        val dstRect = android.graphics.Rect(0, 0, targetWidth, targetHeight)
        canvas.drawBitmap(source, srcRect, dstRect, paint)

        val jpegBytes = encodeJpeg(resized, jpegQuality)
        val thumbnail = createThumbnail(resized)

        return ProcessedImage(jpegBytes, thumbnail)
    }

    private fun createThumbnail(source: Bitmap, maxEdge: Int = 160): ByteArray {
        val scale = min(1.0, maxEdge.toDouble() / max(source.width, source.height))
        val w = max(1, (source.width * scale).roundToInt())
        val h = max(1, (source.height * scale).roundToInt())
        val thumb = Bitmap.createScaledBitmap(source, w, h, true)
        return encodeJpeg(thumb, 80)
    }

    private fun encodeJpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }
}
