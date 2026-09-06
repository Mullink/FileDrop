package com.liquorbee.invoicescanner.annotation

/**
 * Mirrors the MAUI attempt's Annotation/AnnotationModels.cs design (same fractional 0-1
 * coordinate convention as the existing web annotator's toFraction() in
 * invoice-image-annotator.component.ts) - only the storage/rendering tech changed, not the shape.
 */

data class FractionalPoint(val x: Float, val y: Float)

/** One freehand pen stroke. Points are fractions of the image's width/height, not absolute
 * pixels, so a stroke drawn on a small preview composites correctly onto the full-resolution
 * photo at upload time. */
data class Stroke(
    val points: MutableList<FractionalPoint> = mutableListOf(),
    val colorHex: String = "#E53935", // red, matches the existing web annotator's marker color
    val strokeWidthFraction: Float = 0.006f // fraction of the image's long edge
)

/** A short text note pinned to a point on the photo (e.g. "case damaged", "short 2 units"). */
data class TextNote(
    val x: Float,
    val y: Float,
    val text: String,
    val colorHex: String = "#1565C0" // blue, visually distinct from stroke red
)
