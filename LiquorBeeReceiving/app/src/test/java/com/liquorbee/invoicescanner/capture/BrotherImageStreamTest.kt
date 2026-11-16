package com.liquorbee.invoicescanner.capture

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.ArrayDeque
import java.util.Random
import org.junit.Assert.*
import org.junit.Test

class BrotherImageStreamTest {
    private fun header(size: Int): ByteArray = byteArrayOf(0, 2, 1, 0, 0x15, 0) +
        ByteArray(8) { if (it < 4) (size ushr (it * 8)).toByte() else 0 }
    private val trailer = byteArrayOf(0, 0x21, 1, 0, 0, 0x20)
    private fun framed(jpeg: ByteArray, bandSize: Int): ByteArray {
        val result = ByteArrayOutputStream()
        var offset = 0
        while (offset < jpeg.size) {
            val size = minOf(bandSize, jpeg.size - offset)
            result.write(header(size)); result.write(jpeg, offset, size)
            offset += size
        }
        result.write(trailer)
        return result.toByteArray()
    }

    @Test fun reproducesFieldLogWithHeadersBetweenTwo256KiBReads() {
        val jpeg = largeJpeg
        assertTrue(jpeg.size > 2 * 524288)
        val stream = BrotherImageStream()
        var offset = 0
        while (offset < jpeg.size) {
            val size = minOf(524288, jpeg.size - offset)
            val bandHeader = header(size)
            if (offset == 0) assertArrayEquals(byteArrayOf(0,2,1,0,0x15,0,0,0,8,0,0,0,0,0), bandHeader)
            stream.accept(bandHeader, 14)
            var inBand = 0
            while (inBand < size) {
                val count = minOf(262144, size - inBand)
                stream.accept(jpeg.copyOfRange(offset + inBand, offset + inBand + count), count)
                inBand += count
            }
            offset += size
        }
        stream.accept(trailer, trailer.size)
        val output = stream.finish()
        assertArrayEquals(jpeg, output)
        val decoded = decode(output)!!
        assertEquals(1024, decoded.javaClass.getMethod("getWidth").invoke(decoded))
        assertEquals(2048, decoded.javaClass.getMethod("getHeight").invoke(decoded))
        assertTrue(stream.sawTrailer)
    }

    @Test fun splitHeadersPayloadsAndTrailerReassembleAtEverySmallChunkSize() {
        val wire = framed(smallJpeg, 97)
        for (chunkSize in listOf(1, 2, 3, 7, 13, 14, 29, 128, wire.size)) {
            val stream = BrotherImageStream()
            var offset = 0
            while (offset < wire.size) {
                val end = minOf(offset + chunkSize, wire.size)
                stream.accept(wire.copyOfRange(offset, end), end - offset)
                offset = end
            }
            assertArrayEquals("chunkSize=$chunkSize", smallJpeg, stream.finish())
        }
    }

    @Test fun preservesHeaderLikeBytesAndFalseEndMarkerInsideJpegMetadata() {
        val metadata = header(524288) + byteArrayOf(0xff.toByte(), 0xd9.toByte())
        val segment = byteArrayOf(0xff.toByte(), 0xe1.toByte(), 0, (metadata.size + 2).toByte()) + metadata
        val jpeg = smallJpeg.take(2).toByteArray() + segment + smallJpeg.drop(2).toByteArray()
        val stream = BrotherImageStream()
        val wire = framed(jpeg, 14)
        stream.accept(wire, wire.size)
        assertArrayEquals(jpeg, stream.finish())
        assertNotNull(decode(stream.finish()))
        val incomplete = BrotherImageStream()
        val prefix = jpeg.copyOfRange(0, 2 + segment.size)
        incomplete.accept(prefix, prefix.size)
        assertFalse(incomplete.complete)
        assertThrows(IllegalStateException::class.java) { incomplete.finish() }
    }

    @Test fun supportsUnframedJpegAndShortFourteenByteImageReads() {
        val stream = BrotherImageStream()
        smallJpeg.toList().chunked(14).forEach { stream.accept(it.toByteArray(), it.size) }
        assertArrayEquals(smallJpeg, stream.finish())
    }

    @Test fun incompleteBandAndMissingJpegEndAreFailures() {
        val stream = BrotherImageStream()
        val missing = header(smallJpeg.size) + smallJpeg.dropLast(20).toByteArray()
        stream.accept(missing, missing.size)
        assertThrows(IllegalStateException::class.java) { stream.finish() }
        val withoutEnd = framed(smallJpeg.dropLast(2).toByteArray(), 500)
        val noEnd = BrotherImageStream()
        noEnd.accept(withoutEnd, withoutEnd.size)
        assertThrows(IllegalStateException::class.java) { noEnd.finish() }
    }

    @Test fun rejectsUnboundedDeviceLengthsAndBadTrailerStatus() {
        for (length in listOf(0, -1, 65 * 1024 * 1024)) {
            val stream = BrotherImageStream()
            assertThrows(IllegalStateException::class.java) { stream.accept(header(length), 14) }
        }
        val stream = BrotherImageStream()
        val wire = header(smallJpeg.size) + smallJpeg + byteArrayOf(0,0x21,1,0,1,0x20)
        assertThrows(IllegalStateException::class.java) { stream.accept(wire, wire.size) }
    }

    private class FakeUsb {
        var time = 0L
        var consumed = 0
        val reads = ArrayDeque<Pair<Long, ByteArray?>>()
        fun add(bytes: ByteArray?, advance: Long = 0) { reads.add(advance to bytes) }
        fun run(): ByteArray = BrotherScanStreamReader(
            read = { buffer ->
                if (reads.isEmpty()) { time += 1000; -1 } else {
                    val (advance, bytes) = reads.removeFirst()
                    time += advance; consumed++
                    if (bytes == null) 0 else { bytes.copyInto(buffer); bytes.size }
                }
            },
            nowMs = { time }, pause = { time += 10 }
        ).readImage()
    }

    @Test fun rapidZeroLengthRepliesDoNotStopThePaperFeed() {
        val usb = FakeUsb()
        usb.add(header(smallJpeg.size)); usb.add(smallJpeg.copyOfRange(0, 100))
        repeat(20) { usb.add(null) } // Old loop returned after five, within milliseconds.
        usb.add(smallJpeg.copyOfRange(100, smallJpeg.size)); usb.add(trailer)
        assertArrayEquals(smallJpeg, usb.run())
        assertEquals(24, usb.consumed)
    }

    @Test fun tenSecondMidImagePauseStillReceivesTheRest() {
        val usb = FakeUsb()
        usb.add(header(smallJpeg.size)); usb.add(smallJpeg.copyOfRange(0, 100))
        repeat(10) { usb.add(null, 1000) }
        usb.add(smallJpeg.copyOfRange(100, smallJpeg.size)); usb.add(trailer)
        assertArrayEquals(smallJpeg, usb.run())
    }

    @Test fun continuesReadingTrailerAfterJpegEnd() {
        val usb = FakeUsb()
        usb.add(header(smallJpeg.size)); usb.add(smallJpeg)
        repeat(10) { usb.add(null) }
        usb.add(trailer, 2000)
        assertArrayEquals(smallJpeg, usb.run())
        assertEquals(13, usb.consumed)
        assertTrue(usb.time >= 7000)
    }

    @Test fun interruptedStreamFailsAfterElapsedIdleRatherThanAcceptingPartialImage() {
        val usb = FakeUsb()
        usb.add(header(smallJpeg.size)); usb.add(smallJpeg.copyOfRange(0, 100))
        assertThrows(IllegalStateException::class.java) { usb.run() }
        assertTrue(usb.time >= 30000)
    }

    @Test fun protocolFailureStillDrainsQueuedData() {
        val usb = FakeUsb()
        usb.add(byteArrayOf(1,2,3,4))
        usb.add(smallJpeg); usb.add(trailer)
        assertThrows(IllegalStateException::class.java) { usb.run() }
        assertEquals(3, usb.consumed)
        assertTrue(usb.time >= 30000)
    }

    companion object {
        // Android's compile bootclasspath omits desktop imaging, but local JVM tests
        // run on the full JDK. Reflection keeps fixture generation out of app dependencies.
        private fun decode(bytes: ByteArray): Any? = Class.forName("javax.imageio.ImageIO")
            .getMethod("read", java.io.InputStream::class.java).invoke(null, ByteArrayInputStream(bytes))
        private fun image(width: Int, height: Int): ByteArray {
            val type = Class.forName("java.awt.image.BufferedImage")
            val integer = Int::class.javaPrimitiveType!!
            val image = type.getConstructor(integer, integer, integer).newInstance(width, height, 1)
            val random = Random(640)
            val pixels = IntArray(width * height) { random.nextInt() }
            type.getMethod("setRGB", integer, integer, integer, integer, IntArray::class.java, integer, integer)
                .invoke(image, 0, 0, width, height, pixels, 0, width)
            return ByteArrayOutputStream().also {
                Class.forName("javax.imageio.ImageIO").getMethod("write", Class.forName("java.awt.image.RenderedImage"),
                    String::class.java, java.io.OutputStream::class.java).invoke(null, image, "jpeg", it)
            }.toByteArray()
        }
        private val smallJpeg = image(16, 16)
        private val largeJpeg by lazy { image(1024, 2048) }
    }
}
