package com.liquorbee.invoicescanner.imaging

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.RenderNode
import android.graphics.drawable.BitmapDrawable
import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.liquorbee.invoicescanner.R
import com.liquorbee.invoicescanner.annotation.AnnotationCompositor
import com.liquorbee.invoicescanner.ui.PageThumbnailAdapter
import com.liquorbee.invoicescanner.ui.ScanPage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LongInvoiceImageTest {
    private fun fixture() = InstrumentationRegistry.getInstrumentation().context.assets
        .open("long-invoice-2550x21600.jpg").use { it.readBytes() }

    @Test fun previousFullResolutionPathFailsOnAndroidRenderingLimit() {
        var original: Bitmap? = null
        var rejected = false
        val node = RenderNode("old full-sized invoice")
        try {
            val bytes = fixture()
            original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            assertNotNull(original)
            assertEquals(2550, original!!.width)
            assertEquals(21600, original.height)
            val canvas = node.beginRecording(320, 320)
            try { canvas.drawBitmap(original, 0f, 0f, null) }
            finally { node.endRecording() }
        } catch (error: OutOfMemoryError) {
            rejected = true
        } catch (error: RuntimeException) {
            assertTrue(error.message.orEmpty(), error.message.orEmpty().contains("too large"))
            rejected = true
        } finally {
            original?.recycle()
            node.discardDisplayList()
        }
        assertTrue("The old full-sized bitmap must exceed memory or renderer limits on this test device", rejected)
    }

    @Test fun fullLengthScanDecodesPreviewsAndEncodesWithoutCroppingFooter() {
        val logs = mutableListOf<String>()
        val bitmap = ScannerBitmapDecoder.decode(fixture()) { logs.add(it) }
        val page = ScanPage(bitmap)
        try {
            assertEquals(5400, bitmap.height)
            assertTrue(bitmap.allocationByteCount <= 16 * 1024 * 1024)
            assertTrue(maxOf(page.thumbnail.width, page.thumbnail.height) <= 320)
            assertTrue(page.thumbnail !== bitmap)
            val node = RenderNode("bounded invoice")
            val canvas = node.beginRecording(320, 320)
            try { canvas.drawBitmap(bitmap, 0f, 0f, null) }
            finally { node.endRecording(); node.discardDisplayList() }
            val instrument = InstrumentationRegistry.getInstrumentation()
            instrument.runOnMainSync {
                val context = ContextThemeWrapper(instrument.targetContext, R.style.Theme_InvoiceScanner)
                val adapter = PageThumbnailAdapter(listOf(page), {}, {})
                val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
                adapter.onBindViewHolder(holder, 0)
                assertSame(page.thumbnail, (holder.binding.imageThumbnail.drawable as BitmapDrawable).bitmap)
            }
            assertSame(bitmap, AnnotationCompositor.composite(bitmap, emptyList(), emptyList()))
            val processed = ImagePipeline.process(bitmap, 4200, 95)
            val upload = BitmapFactory.decodeByteArray(processed.bytes, 0, processed.bytes.size)!!
            try {
                assertEquals(4200, upload.height)
                assertTrue(Color.red(upload.getPixel(upload.width / 2, 5)) < 32)
                assertTrue("Footer is still present", Color.red(upload.getPixel(upload.width / 2, upload.height - 5)) < 32)
                assertTrue(Color.red(upload.getPixel(upload.width / 2, upload.height / 2)) > 220)
            } finally { upload.recycle() }
            assertFalse("Processing must not recycle the page retained for review", bitmap.isRecycled)
            assertTrue(logs.any { it.contains("sample=4") })
        } finally { page.thumbnail.recycle(); bitmap.recycle() }
    }
}
