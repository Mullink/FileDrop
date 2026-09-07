package com.liquorbee.wholesale.ui.wholesale

import android.content.Context
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import com.liquorbee.wholesale.databinding.DialogNumberPadBinding

/** A custom on-screen numeric keypad, used instead of the system IME for quantity entry - a
 * fixed POS terminal doesn't need (and shouldn't show) a full software keyboard for what's
 * always just a small integer. */
object NumberPadDialog {

    fun show(context: Context, title: String, initialValue: Int, onConfirm: (Int) -> Unit) {
        val binding = DialogNumberPadBinding.inflate(LayoutInflater.from(context))
        binding.textPadTitle.text = title
        var current = if (initialValue > 0) initialValue.toString() else ""
        fun render() { binding.textPadValue.text = current.ifEmpty { "0" } }
        render()

        val dialog = AlertDialog.Builder(context)
            .setView(binding.root)
            .create()

        fun digit(d: String) {
            if (current.length >= 5) return // 99999 units is already an absurd single order line
            current = if (current == "0") d else current + d
            render()
        }

        binding.pad0.setOnClickListener { digit("0") }
        binding.pad1.setOnClickListener { digit("1") }
        binding.pad2.setOnClickListener { digit("2") }
        binding.pad3.setOnClickListener { digit("3") }
        binding.pad4.setOnClickListener { digit("4") }
        binding.pad5.setOnClickListener { digit("5") }
        binding.pad6.setOnClickListener { digit("6") }
        binding.pad7.setOnClickListener { digit("7") }
        binding.pad8.setOnClickListener { digit("8") }
        binding.pad9.setOnClickListener { digit("9") }
        binding.padBackspace.setOnClickListener {
            if (current.isNotEmpty()) current = current.dropLast(1)
            render()
        }
        binding.padClear.setOnClickListener { current = ""; render() }
        binding.padEnter.setOnClickListener {
            onConfirm(current.toIntOrNull() ?: 0)
            dialog.dismiss()
        }

        dialog.show()
    }
}
