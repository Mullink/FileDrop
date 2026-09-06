package com.liquorbee.invoicescanner.capture

import android.graphics.Bitmap

/**
 * One captured page, before annotation/pipeline processing - just the raw bitmap a source
 * produced, whatever that source was.
 */
data class CapturedPage(val bitmap: Bitmap)

sealed class CaptureResult {
    data class Success(val page: CapturedPage) : CaptureResult()
    data class Failure(val message: String) : CaptureResult()
    object Cancelled : CaptureResult()
}

/**
 * Abstraction over "where a page's image comes from" - mirrors the desktop app's ITwainScanner
 * interface (LiquorBeeInvoiceScanner/Scanning/ITwainScanner.cs), which wraps the Epson TWAIN
 * driver the same way this wraps the phone camera, a USB-attached Brother DS-640, or a plain
 * imported file (FilePickerCaptureSource - mirrors the desktop's FilePickerScanner.cs fallback).
 * The three are not equally available on every device - e.g. a camera-less POS terminal only ever
 * has the file picker and (eventually) Brother-USB - so isAvailable() lets the UI adapt per device
 * rather than assuming a phone-shaped hardware set. The rest of the app (template pick, drawing/
 * annotation, upload) only ever deals in CapturedPage / CaptureResult and never needs to know which
 * concrete source produced a page.
 */
interface PageCaptureSource {
    val displayName: String

    /** True if this source is actually usable right now (e.g. a Brother scanner is plugged in
     * and its driver is ready) - lets the UI grey out/hide a source instead of offering a capture
     * button that's guaranteed to fail. */
    fun isAvailable(): Boolean
}
