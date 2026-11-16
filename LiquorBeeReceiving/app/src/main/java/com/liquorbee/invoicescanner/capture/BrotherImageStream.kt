package com.liquorbee.invoicescanner.capture

import java.io.ByteArrayOutputStream

/**
 * DS-640 colour band header observed in the field:
 * 00 02 01 00 15 00 00 00 08 00 00 00 00 00, followed by 524288 image bytes.
 * Offsets 6 and 10 are SEPARATE little-endian uint32 fields: payload byte count
 * and band metadata. Brother's ParseXSCcmdImageDataInfo reads them separately;
 * only the first determines how many image bytes follow. The second can be
 * nonzero, especially in the final band. It is not the high half of a uint64.
 * USB transfers
 * are not records: a header or payload can span reads, or share a read.
 * Only interpret headers BETWEEN payloads; identical bytes inside an image stay intact.
 */
internal class BrotherImageStream(private val log: (String) -> Unit = {}) {
    private val image = ByteArrayOutputStream()
    private val header = ByteArray(14)
    private var headerSize = 0
    private var remaining = 0
    private var rawJpeg = false
    private var trailing = false
    private val jpeg = JpegCompletion()
    var bandCount = 0
        private set
    var sawTrailer = false
        private set
    val imageSize: Int get() = image.size()
    val complete: Boolean get() = jpeg.complete && remaining == 0 && headerSize == 0

    fun accept(bytes: ByteArray, count: Int) {
        require(count in 0..bytes.size)
        var offset = 0
        while (offset < count) {
            if (trailing) {
                log("Post-image status: ${count - offset} bytes, starts ${hex(bytes, offset, count)}")
                return
            }
            if (rawJpeg) {
                append(bytes, offset, count - offset)
                return
            }
            if (remaining > 0) {
                val take = minOf(remaining, count - offset)
                append(bytes, offset, take)
                offset += take
                remaining -= take
                continue
            }
            header[headerSize++] = bytes[offset++]
            // Compatibility with the previously observed unframed JPEG response.
            if (headerSize == 2 && imageSize == 0 && bandCount == 0 &&
                u(header[0]) == 0xff && u(header[1]) == 0xd8) {
                rawJpeg = true
                append(header, 0, headerSize)
                headerSize = 0
                continue
            }
            if (headerSize == 4) {
                val band = header[0] == 0.toByte() && header[1] == 2.toByte() &&
                    header[2] == 1.toByte() && header[3] == 0.toByte()
                val trailer = header[0] == 0.toByte() && header[1] == 0x21.toByte() &&
                    header[2] == 1.toByte() && header[3] == 0.toByte()
                check(band || trailer) { "Unexpected scanner record: ${hex(header, 0, headerSize)}" }
            }
            if (headerSize == 6 && header[1] == 0x21.toByte()) {
                log("Scanner trailer: ${hex(header, 0, headerSize)}")
                // Do not interpret unknown device status codes as success.
                check(header[4] == 0.toByte() && header[5] == 0x20.toByte()) {
                    "The scanner reported an unsuccessful end status: ${hex(header, 0, headerSize)}"
                }
                sawTrailer = true
                trailing = true
                headerSize = 0
                continue
            }
            if (headerSize == header.size) {
                check(u(header[4]) in listOf(0x11, 0x15) && header[5] == 0.toByte()) {
                    "Unsupported scanner image band: ${hex(header, 0, headerSize)}"
                }
                val size = uint32(header, 6)
                val bandInfo = uint32(header, 10)
                log("Image band #${bandCount + 1}: $size payload bytes; metadata=$bandInfo; header ${hex(header, 0, headerSize)}")
                // Bound the actual payload before narrowing to Int. Metadata never
                // controls allocation or consumes bytes from the next USB record.
                check(size <= MAX_IMAGE_BYTES.toLong() && imageSize.toLong() + size <= MAX_IMAGE_BYTES) {
                    "Scanner image exceeds the supported memory limit ($size band bytes, $imageSize received). Scan this invoice in sections."
                }
                remaining = size.toInt()
                bandCount++
                headerSize = 0
            }
        }
    }

    private fun append(bytes: ByteArray, offset: Int, count: Int) {
        check(imageSize.toLong() + count <= MAX_IMAGE_BYTES) { "Scanner image is too large." }
        for (i in offset until offset + count) jpeg.accept(u(bytes[i]))
        image.write(bytes, offset, count)
    }

    fun finish(): ByteArray {
        check(imageSize > 0) { "The scanner returned no image. Check that paper is loaded." }
        check(remaining == 0 && headerSize == 0) {
            "The scanner stopped before the image finished ($remaining band bytes still missing)."
        }
        check(jpeg.complete) {
            "The scanner returned an incomplete JPEG. The full invoice has not been captured."
        }
        return image.toByteArray()
    }

    companion object {
        private const val MAX_IMAGE_BYTES = 64 * 1024 * 1024
        private fun u(value: Byte) = value.toInt() and 0xff
        private fun uint32(bytes: ByteArray, offset: Int): Long =
            (0..3).fold(0L) { value, i -> value or (u(bytes[offset + i]).toLong() shl (8 * i)) }
        private fun hex(bytes: ByteArray, start: Int, end: Int): String =
            (start until minOf(end, start + 16)).joinToString(" ") { "%02X".format(bytes[it]) }
    }
}

/** Recognize EOI structurally: FF D9 inside EXIF/other length-delimited metadata is not EOI. */
private class JpegCompletion {
    private var state = 0 // marker prefix, marker code, length high, length low, segment data, entropy, entropy FF
    private var first = true
    private var sawScan = false
    private var marker = 0
    private var length = 0
    private var left = 0
    private var afterSegment = 0
    var complete = false
        private set

    fun accept(value: Int) {
        if (complete) return // Device status/padding after EOI is drained separately.
        when (state) {
            0 -> { check(value == 0xff) { "Invalid JPEG marker prefix." }; state = 1 }
            1 -> if (value != 0xff) marker(value, false)
            2 -> { length = value shl 8; state = 3 }
            3 -> {
                left = (length or value) - 2
                check(left >= 0) { "Invalid JPEG segment length." }
                state = if (left == 0) afterSegment else 4
            }
            4 -> { left--; if (left == 0) state = afterSegment }
            5 -> if (value == 0xff) state = 6
            6 -> when {
                value == 0 || value in 0xd0..0xd7 -> state = 5
                value == 0xff -> Unit
                else -> marker(value, true)
            }
        }
    }

    private fun marker(value: Int, inScan: Boolean) {
        if (first) {
            check(value == 0xd8) { "Image payload is not JPEG." }
            first = false
            state = 0
            return
        }
        check(value != 0 && value != 0xd8) { "Invalid JPEG marker." }
        if (value == 0xd9) {
            check(sawScan) { "JPEG ended before any image data." }
            complete = true
        } else if (value == 1 || value in 0xd0..0xd7) {
            state = if (inScan) 5 else 0
        } else {
            marker = value
            if (marker == 0xda) sawScan = true
            afterSegment = if (marker == 0xda || (inScan && marker == 0xdc)) 5 else 0
            state = 2
        }
    }
}

/** Pure USB read loop so pauses, short reads and framing can be replayed in JVM tests. */
internal class BrotherScanStreamReader(
    private val read: (ByteArray) -> Int,
    private val nowMs: () -> Long,
    private val pause: () -> Unit,
    private val checkCancelled: () -> Unit = {},
    private val log: (String) -> Unit = {}
) {
    fun readImage(): ByteArray {
        val image = BrotherImageStream(log)
        val buffer = ByteArray(256 * 1024)
        val started = nowMs()
        var lastData = started
        var failure: IllegalStateException? = null
        var reads = 0
        while (true) {
            checkCancelled()
            check(nowMs() - started < 5 * 60_000L) { "Scanner did not finish within five minutes." }
            val count = read(buffer)
            if (count > 0) {
                lastData = nowMs()
                reads++
                if (failure == null) {
                    try { image.accept(buffer, count) }
                    catch (e: IllegalStateException) {
                        failure = e
                        log("Image protocol error: ${e.message}; continuing to drain scanner data.")
                    }
                }
                log("USB read #$reads: $count bytes; ${image.imageSize} JPEG bytes retained.")
                continue
            }
            // A zero-length success may return immediately. Counting five such replies
            // used to stop the host in milliseconds while the paper was still feeding.
            val idle = nowMs() - lastData
            val waitMs = when {
                failure != null -> 30_000L
                image.complete -> 5_000L
                image.imageSize > 0 -> 30_000L
                else -> 90_000L
            }
            if (idle >= waitMs) {
                log("Scanner idle for ${idle}ms; ${image.bandCount} bands, ${image.imageSize} JPEG bytes, " +
                    "complete=${image.complete}, trailer=${image.sawTrailer}.")
                failure?.let { throw it }
                return image.finish()
            }
            pause()
        }
    }
}
