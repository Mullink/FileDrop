package com.liquorbee.invoicescanner.imaging

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Color
import android.graphics.Rect

internal object ScannerBitmapDecoder {
    fun decode(jpeg: ByteArray, log: (String) -> Unit = {}): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        BitmapDecodePolicy.sampleSize(bounds.outWidth, bounds.outHeight) // Validate before probing.
        val contentHeight = contentHeight(jpeg, bounds.outWidth, bounds.outHeight)
        var sample = BitmapDecodePolicy.sampleSize(bounds.outWidth, contentHeight)
        log("JPEG dimensions: ${bounds.outWidth}x${bounds.outHeight}; retained height=$contentHeight; bounded decode sample=$sample.")
        while (true) {
            try {
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inScaled = false
                }
                // Crop at the compressed source, before choosing/allocating the final bitmap.
                // Cropping an already sampled bitmap cannot restore lost invoice text.
                val bitmap = (if (contentHeight < bounds.outHeight) {
                    val decoder = BitmapRegionDecoder.newInstance(jpeg, 0, jpeg.size, false)
                    try { decoder?.decodeRegion(Rect(0, 0, bounds.outWidth, contentHeight), options) }
                    finally { decoder?.recycle() }
                } else BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options))
                    ?: throw IllegalStateException("The DS-640 returned data that could not be decoded as an image.")
                log("Bitmap decoded: ${bitmap.width}x${bitmap.height}; ${bitmap.allocationByteCount} bytes.")
                return bitmap
            } catch (error: OutOfMemoryError) {
                if (maxOf(bounds.outWidth, contentHeight) / sample <= 1024) {
                    throw IllegalStateException("Not enough memory to load this scan. Submit or remove saved pages and retry.", error)
                }
                sample *= 2
                log("Memory pressure while decoding; retrying at sample=$sample.")
            }
        }
    }

    private fun contentHeight(jpeg: ByteArray, width: Int, height: Int): Int {
        var sample = 1
        while (maxOf(width, height).toLong() > 1536L * sample) sample *= 2
        val probe = try {
            BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
                inScaled = false
            })
        } catch (_: OutOfMemoryError) { return height }
        if (probe == null) return height
        try {
            val last = lastNonBlackRow(probe)
            // Only remove a substantial, contiguous, entirely black suffix. Never remove
            // white paper, gray bed margins, internal gaps, or an all-black/unknown scan.
            val tail = probe.height - last - 1
            if (last < 0 || tail < maxOf(16, probe.height / 20)) return height
            // Retain two probe rows around the detected edge for JPEG ringing/sampling.
            return minOf(height, ((last + 3L) * height / probe.height + 1).toInt())
        } finally { probe.recycle() }
    }

    private fun lastNonBlackRow(bitmap: Bitmap): Int {
        val pixels = IntArray(bitmap.width)
        for (y in bitmap.height - 1 downTo 0) {
            bitmap.getPixels(pixels, 0, bitmap.width, 0, y, bitmap.width, 1)
            if (pixels.any { Color.red(it) > 24 || Color.green(it) > 24 || Color.blue(it) > 24 }) return y
        }
        return -1
    }

    fun thumbnail(bitmap: Bitmap): Bitmap {
        val edge = maxOf(bitmap.width, bitmap.height)
        val scale = minOf(1.0, 320.0 / edge)
        return Bitmap.createScaledBitmap(bitmap, maxOf(1, (bitmap.width * scale).toInt()),
            maxOf(1, (bitmap.height * scale).toInt()), true)
    }
}
