package com.liquorbee.invoicescanner.imaging

/** Bound decoded pixels before allocating, not after a full-resolution decode. */
internal object BitmapDecodePolicy {
    const val MAX_PIXELS = 12_000_000L
    const val MAX_EDGE = 8192

    fun sampleSize(width: Int, height: Int): Int {
        require(width > 0 && height > 0) { "The scanner returned unreadable image dimensions." }
        var sample = 1
        while (true) {
            val w = (width.toLong() + sample - 1) / sample
            val h = (height.toLong() + sample - 1) / sample
            if (w <= MAX_EDGE && h <= MAX_EDGE && w * h <= MAX_PIXELS) return sample
            sample *= 2
        }
    }
}
