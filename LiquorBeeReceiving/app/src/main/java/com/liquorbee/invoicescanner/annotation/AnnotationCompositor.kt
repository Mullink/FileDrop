package com.liquorbee.invoicescanner.annotation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.max

/**
 * Flattens freehand strokes and text notes onto a photo at full resolution - same interaction
 * shape as the existing web annotator's exportComposite() (invoice-image-annotator.component.ts)
 * and the MAUI attempt's AnnotationCompositor.cs: fractional (0-1) coordinates in, a rendered
 * bitmap out. Runs BEFORE ImagePipeline (which then grayscales/downscales/JPEG-encodes the
 * result) - by the time ImagePipeline sees it, it's just a bitmap with no separate concept of
 * "annotation". Drawing is optional: if strokes and notes are both empty this is a no-op copy.
 */
object AnnotationCompositor {

    fun composite(base: Bitmap, strokes: List<Stroke>, notes: List<TextNote>): Bitmap {
        val result = base.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val longEdge = max(result.width, result.height).toFloat()

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        for (stroke in strokes) {
            if (stroke.points.size < 2) continue
            val path = Path()
            val first = stroke.points.first()
            path.moveTo(first.x * result.width, first.y * result.height)
            for (pt in stroke.points.drop(1)) {
                path.lineTo(pt.x * result.width, pt.y * result.height)
            }
            strokePaint.color = Color.parseColor(stroke.colorHex)
            strokePaint.strokeWidth = max(2f, stroke.strokeWidthFraction * longEdge)
            canvas.drawPath(path, strokePaint)
        }

        for (note in notes) {
            if (note.text.isBlank()) continue

            val fontSize = longEdge * 0.022f
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor(note.colorHex)
                textSize = fontSize
            }
            val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(230, 255, 255, 255)
            }

            val x = note.x * result.width
            val y = note.y * result.height
            val textWidth = textPaint.measureText(note.text)
            val padding = fontSize * 0.35f

            val backgroundRect = RectF(x - padding, y - fontSize - padding, x + textWidth + padding, y + padding)
            canvas.drawRoundRect(backgroundRect, padding, padding, backgroundPaint)
            canvas.drawText(note.text, x, y, textPaint)
        }

        return result
    }
}
