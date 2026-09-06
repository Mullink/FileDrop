package com.liquorbee.invoicescanner.ui

import android.graphics.Bitmap
import com.liquorbee.invoicescanner.annotation.Stroke
import com.liquorbee.invoicescanner.annotation.TextNote

/** One captured page plus whatever's been drawn on it so far. */
data class ScanPage(
    val bitmap: Bitmap,
    var strokes: MutableList<Stroke> = mutableListOf(),
    var notes: MutableList<TextNote> = mutableListOf()
) {
    val hasAnnotations: Boolean get() = strokes.isNotEmpty() || notes.isNotEmpty()
}

/**
 * In-memory holder shared between ScanActivity and DrawActivity for the pages in the batch
 * currently being built. Deliberately not passed through Intent extras - Bitmaps are far too
 * large for Android's Binder transaction size limit (~1MB total per Intent). Cleared once a batch
 * is submitted or the scan screen is abandoned.
 */
object PageStore {
    val pages = mutableListOf<ScanPage>()

    fun clear() = pages.clear()
}
