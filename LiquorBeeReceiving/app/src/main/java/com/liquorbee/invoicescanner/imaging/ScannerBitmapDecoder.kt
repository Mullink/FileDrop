package com.liquorbee.invoicescanner.imaging

import android.graphics.Bitmap
import android.graphics.BitmapFactory

internal object ScannerBitmapDecoder {
    fun decode(jpeg: ByteArray, log: (String) -> Unit = {}): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        var sample = BitmapDecodePolicy.sampleSize(bounds.outWidth, bounds.outHeight)
        log("JPEG dimensions: ${bounds.outWidth}x${bounds.outHeight}; bounded decode sample=$sample.")
        while (true) {
            try {
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inScaled = false
                }
                val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options)
                    ?: throw IllegalStateException("The DS-640 returned data that could not be decoded as an image.")
                log("Bitmap decoded: ${bitmap.width}x${bitmap.height}; ${bitmap.allocationByteCount} bytes.")
                return bitmap
            } catch (error: OutOfMemoryError) {
                if (maxOf(bounds.outWidth, bounds.outHeight) / sample <= 1024) {
                    throw IllegalStateException("Not enough memory to load this scan. Submit or remove saved pages and retry.", error)
                }
                sample *= 2
                log("Memory pressure while decoding; retrying at sample=$sample.")
            }
        }
    }

    fun thumbnail(bitmap: Bitmap): Bitmap {
        val edge = maxOf(bitmap.width, bitmap.height)
        val scale = minOf(1.0, 320.0 / edge)
        return Bitmap.createScaledBitmap(bitmap, maxOf(1, (bitmap.width * scale).toInt()),
            maxOf(1, (bitmap.height * scale).toInt()), true)
    }
}
