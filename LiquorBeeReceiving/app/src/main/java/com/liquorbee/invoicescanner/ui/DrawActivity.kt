package com.liquorbee.invoicescanner.ui

import android.app.AlertDialog
import android.graphics.Bitmap
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.liquorbee.invoicescanner.annotation.AnnotationDatabase
import com.liquorbee.invoicescanner.annotation.AnnotationJson
import com.liquorbee.invoicescanner.annotation.DrawingCanvasView
import com.liquorbee.invoicescanner.annotation.DrawingTemplateEntity
import com.liquorbee.invoicescanner.databinding.ActivityDrawBinding
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

const val EXTRA_PAGE_INDEX = "extra_page_index"

/**
 * Freehand strokes + text notes over one captured page. Everything here is local-only per the
 * approved plan - "Save as Template"/"Load Template" read/write this device's Room database
 * (AnnotationDatabase), never the backend. "Done" writes the resulting strokes/notes back into
 * PageStore.pages[index] in place and finishes - ScanActivity composites them onto the full-
 * resolution photo at submit time (AnnotationCompositor), not here.
 */
class DrawActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDrawBinding
    private var pageIndex: Int = -1
    private lateinit var db: AnnotationDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDrawBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AnnotationDatabase.getInstance(this)
        pageIndex = intent.getIntExtra(EXTRA_PAGE_INDEX, -1)
        val page = PageStore.pages.getOrNull(pageIndex)
        if (page == null) {
            finish()
            return
        }

        binding.drawingCanvas.setBaseImage(page.bitmap)
        binding.drawingCanvas.loadTemplateMarkup(page.strokes, page.notes)

        binding.toggleMode.setOnCheckedChangeListener { _, isChecked ->
            binding.drawingCanvas.mode = if (isChecked) DrawingCanvasView.Mode.NOTE else DrawingCanvasView.Mode.DRAW
        }

        binding.drawingCanvas.onNoteRequested = { x, y -> promptForNoteText(x, y) }

        binding.buttonUndo.setOnClickListener { binding.drawingCanvas.undoLast() }
        binding.buttonClear.setOnClickListener { binding.drawingCanvas.clearAll() }
        binding.buttonSaveTemplate.setOnClickListener { promptSaveTemplate(page.bitmap) }
        binding.buttonLoadTemplate.setOnClickListener { showLoadTemplateDialog() }
        binding.buttonDone.setOnClickListener { saveAndFinish(page) }
    }

    private fun promptForNoteText(xFraction: Float, yFraction: Float) {
        val input = EditText(this).apply { inputType = InputType.TYPE_CLASS_TEXT }
        AlertDialog.Builder(this)
            .setTitle("Add note")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                binding.drawingCanvas.addNote(xFraction, yFraction, input.text.toString())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptSaveTemplate(baseImage: Bitmap) {
        val input = EditText(this).apply { hint = "Template name" }
        AlertDialog.Builder(this)
            .setTitle("Save as Template")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                lifecycleScope.launch {
                    val stream = ByteArrayOutputStream()
                    baseImage.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                    db.drawingTemplateDao().insert(
                        DrawingTemplateEntity(
                            name = name,
                            baseImageBytes = stream.toByteArray(),
                            strokesJson = AnnotationJson.toJson(binding.drawingCanvas.getStrokes()),
                            notesJson = AnnotationJson.notesToJson(binding.drawingCanvas.getNotes())
                        )
                    )
                    Toast.makeText(this@DrawActivity, "Template \"$name\" saved.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLoadTemplateDialog() {
        lifecycleScope.launch {
            val templates = db.drawingTemplateDao().getAll()
            if (templates.isEmpty()) {
                Toast.makeText(this@DrawActivity, "No saved templates yet.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val names = templates.map { it.name }.toTypedArray()
            AlertDialog.Builder(this@DrawActivity)
                .setTitle("Load Template")
                .setItems(names) { _, which ->
                    val chosen = templates[which]
                    // Applies the template's saved strokes/notes onto the CURRENT photo (the
                    // reusable-checklist use case), appended to whatever's already drawn here.
                    val strokes = AnnotationJson.strokesFrom(chosen.strokesJson)
                    val notes = AnnotationJson.notesFrom(chosen.notesJson)
                    val combinedStrokes = binding.drawingCanvas.getStrokes() + strokes
                    val combinedNotes = binding.drawingCanvas.getNotes() + notes
                    binding.drawingCanvas.loadTemplateMarkup(combinedStrokes, combinedNotes)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun saveAndFinish(page: ScanPage) {
        page.strokes = binding.drawingCanvas.getStrokes().toMutableList()
        page.notes = binding.drawingCanvas.getNotes().toMutableList()
        finish()
    }
}
