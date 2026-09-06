package com.liquorbee.invoicescanner.annotation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max

/**
 * Freehand drawing + text-note surface over a photo, using the same pointer-event -> fractional
 * (0-1) coordinate -> full-resolution-composite pattern as the existing web annotator
 * (invoice-image-annotator.component.ts's onPointerDown/onPointerMove/onPointerUp + toFraction()).
 * Coordinates are stored as fractions of the DISPLAYED image box inside this view (accounting for
 * letterboxing from aspect-fit scaling), so a stroke drawn here composites correctly onto the
 * full-resolution bitmap via AnnotationCompositor regardless of this view's on-screen size.
 */
class DrawingCanvasView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Mode { DRAW, NOTE }

    var mode: Mode = Mode.DRAW
    var onNoteRequested: ((xFraction: Float, yFraction: Float) -> Unit)? = null

    private var baseBitmap: Bitmap? = null
    private val strokes = mutableListOf<Stroke>()
    private val notes = mutableListOf<TextNote>()
    private var currentStroke: Stroke? = null

    private var imageBox = RectF()

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#E53935")
        strokeWidth = 6f
    }

    private val noteMarkerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1565C0")
    }

    fun setBaseImage(bitmap: Bitmap) {
        baseBitmap = bitmap
        recalculateImageBox()
        invalidate()
    }

    fun loadTemplateMarkup(strokesIn: List<Stroke>, notesIn: List<TextNote>) {
        strokes.clear(); strokes.addAll(strokesIn)
        notes.clear(); notes.addAll(notesIn)
        invalidate()
    }

    fun addNote(xFraction: Float, yFraction: Float, text: String) {
        if (text.isBlank()) return
        notes.add(TextNote(xFraction, yFraction, text))
        invalidate()
    }

    fun getStrokes(): List<Stroke> = strokes
    fun getNotes(): List<TextNote> = notes

    // Strokes and notes aren't interleaved with a shared timestamp, so "last action" just means
    // last stroke if there is one, else last note - good enough for a utility annotation tool.
    fun undoLast() {
        if (strokes.isNotEmpty()) strokes.removeAt(strokes.size - 1)
        else if (notes.isNotEmpty()) notes.removeAt(notes.size - 1)
        invalidate()
    }

    fun clearAll() {
        strokes.clear(); notes.clear(); invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recalculateImageBox()
    }

    private fun recalculateImageBox() {
        val bmp = baseBitmap ?: return
        if (width == 0 || height == 0) return

        val viewRatio = width.toFloat() / height.toFloat()
        val imageRatio = bmp.width.toFloat() / bmp.height.toFloat()

        imageBox = if (imageRatio > viewRatio) {
            val displayedHeight = width / imageRatio
            val top = (height - displayedHeight) / 2f
            RectF(0f, top, width.toFloat(), top + displayedHeight)
        } else {
            val displayedWidth = height * imageRatio
            val left = (width - displayedWidth) / 2f
            RectF(left, 0f, left + displayedWidth, height.toFloat())
        }
    }

    private fun toFraction(viewX: Float, viewY: Float): FractionalPoint? {
        if (imageBox.width() <= 0f || imageBox.height() <= 0f) return null
        val fx = (viewX - imageBox.left) / imageBox.width()
        val fy = (viewY - imageBox.top) / imageBox.height()
        return FractionalPoint(fx.coerceIn(0f, 1f), fy.coerceIn(0f, 1f))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = baseBitmap ?: return
        canvas.drawBitmap(bmp, null, imageBox, null)

        val longEdge = max(imageBox.width(), imageBox.height())

        for (stroke in strokes) drawStroke(canvas, stroke, longEdge)
        currentStroke?.let { drawStroke(canvas, it, longEdge) }

        for (note in notes) drawNoteMarker(canvas, note)
    }

    private fun drawStroke(canvas: Canvas, stroke: Stroke, longEdge: Float) {
        if (stroke.points.size < 2) return
        val path = Path()
        val first = toViewPoint(stroke.points.first())
        path.moveTo(first.first, first.second)
        for (pt in stroke.points.drop(1)) {
            val v = toViewPoint(pt)
            path.lineTo(v.first, v.second)
        }
        strokePaint.color = Color.parseColor(stroke.colorHex)
        strokePaint.strokeWidth = max(2f, stroke.strokeWidthFraction * longEdge)
        canvas.drawPath(path, strokePaint)
    }

    private fun drawNoteMarker(canvas: Canvas, note: TextNote) {
        val (x, y) = toViewPoint(FractionalPoint(note.x, note.y))
        noteMarkerPaint.color = Color.parseColor(note.colorHex)
        canvas.drawCircle(x, y, 10f, noteMarkerPaint)
        canvas.drawText(note.text, x + 14f, y + 6f, noteMarkerPaint.apply { textSize = 32f })
    }

    private fun toViewPoint(p: FractionalPoint): Pair<Float, Float> =
        Pair(imageBox.left + p.x * imageBox.width(), imageBox.top + p.y * imageBox.height())

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val fraction = toFraction(event.x, event.y) ?: return false

        when (mode) {
            Mode.NOTE -> {
                if (event.action == MotionEvent.ACTION_UP) {
                    onNoteRequested?.invoke(fraction.x, fraction.y)
                }
                return true
            }
            Mode.DRAW -> {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        currentStroke = Stroke().also { it.points.add(fraction) }
                        invalidate()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        currentStroke?.points?.add(fraction)
                        invalidate()
                    }
                    MotionEvent.ACTION_UP -> {
                        currentStroke?.let { if (it.points.size >= 2) strokes.add(it) }
                        currentStroke = null
                        invalidate()
                    }
                }
                return true
            }
        }
    }
}
