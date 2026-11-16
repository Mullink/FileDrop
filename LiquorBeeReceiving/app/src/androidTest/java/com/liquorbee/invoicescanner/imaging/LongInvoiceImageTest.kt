package com.liquorbee.invoicescanner.imaging

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.RenderNode
import android.graphics.drawable.BitmapDrawable
import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import android.widget.ImageView
import android.view.View
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
    @Test fun paddedScanKeepsTextResolutionAndFooterWithinMemoryLimit() {
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets
            .open("padded-invoice-2550x21600.jpg").use { it.readBytes() }
        val bitmap = ScannerBitmapDecoder.decode(bytes)
        try {
            // The old full-canvas decode/upload shrank the same printed area to ~230 px.
            assertEquals(1275, bitmap.width)
            assertTrue(bitmap.height in 2750..2800)
            assertTrue(bitmap.allocationByteCount <= BitmapDecodePolicy.MAX_PIXELS * 4)
            assertTrue("Header retained", Color.red(bitmap.getPixel(300, 25)) < 32)
            assertTrue("Footer retained", Color.red(bitmap.getPixel(300, 2710)) < 32)
            assertTrue("Gray margin must not be mistaken for empty black padding",
                Color.red(bitmap.getPixel(1000, 1000)) in 100..180)
            val processed = ImagePipeline.process(bitmap, 4200, 95)
            val upload = BitmapFactory.decodeByteArray(processed.bytes, 0, processed.bytes.size)!!
            try {
                assertEquals(1275, upload.width)
                assertEquals(bitmap.height, upload.height)
                assertTrue(Color.red(upload.getPixel(300, 2710)) < 32)
            } finally { upload.recycle() }
        } finally { bitmap.recycle() }
    }

    @Test fun blackOrUncertainScansAreNotDestructivelyCropped() {
        for (background in listOf(Color.BLACK, Color.DKGRAY, Color.WHITE)) {
            val original = Bitmap.createBitmap(100, 2000, Bitmap.Config.ARGB_8888)
            original.eraseColor(background)
            // A real light footer below a large black gap must keep the whole page.
            if (background == Color.BLACK) original.setPixel(50, 1998, Color.WHITE)
            val output = java.io.ByteArrayOutputStream()
            original.compress(Bitmap.CompressFormat.PNG, 100, output)
            val bitmap = ScannerBitmapDecoder.decode(output.toByteArray())
            try { assertEquals(2000, bitmap.height) }
            finally { bitmap.recycle(); original.recycle() }
        }
    }

    private fun fixture() = InstrumentationRegistry.getInstrumentation().context.assets
        .open("long-invoice-2550x21600.jpg").use { it.readBytes() }

    @Test fun thumbnailShowsHeaderAndFooterInsteadOfBlankMiddleCrop() {
        val bitmap = ScannerBitmapDecoder.decode(fixture())
        val page = ScanPage(bitmap)
        try {
            val instrument = InstrumentationRegistry.getInstrumentation()
            instrument.runOnMainSync {
                val context = ContextThemeWrapper(instrument.targetContext, R.style.Theme_InvoiceScanner)
                val adapter = PageThumbnailAdapter(listOf(page), {}, {})
                val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
                adapter.onBindViewHolder(holder, 0)
                val preview = holder.binding.imageThumbnail
                assertEquals(ImageView.ScaleType.FIT_CENTER, preview.scaleType)
                preview.measure(View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(130, View.MeasureSpec.EXACTLY))
                preview.layout(0, 0, 100, 130)
                fun darkPixels(top: Int, bottom: Int): Int {
                    val rendered = Bitmap.createBitmap(100, 130, Bitmap.Config.ARGB_8888)
                    try {
                        preview.draw(Canvas(rendered))
                        return (top until bottom).sumOf { y -> (0 until 100).count { x -> Color.red(rendered.getPixel(x, y)) < 160 } }
                    } finally { rendered.recycle() }
                }
                assertTrue("Invoice header must remain visible", darkPixels(0, 4) > 0)
                assertTrue("Invoice footer must remain visible", darkPixels(126, 130) > 0)
                preview.scaleType = ImageView.ScaleType.CENTER_CROP
                // ScaleType requests a layout; apply it before inspecting the old rendering.
                preview.measure(View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(130, View.MeasureSpec.EXACTLY))
                preview.layout(0, 0, 100, 130)
                assertEquals("Old center crop showed only blank paper", 0, darkPixels(0, 130))
            }
        } finally { page.thumbnail.recycle(); bitmap.recycle() }
    }

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
