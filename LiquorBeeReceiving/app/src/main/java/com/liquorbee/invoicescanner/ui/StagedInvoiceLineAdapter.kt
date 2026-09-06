package com.liquorbee.invoicescanner.ui

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.recyclerview.widget.RecyclerView
import com.liquorbee.invoicescanner.databinding.ItemStagedInvoiceLineBinding
import com.liquorbee.invoicescanner.network.OcrScannedInvoiceLineItemDto

private val SHIPPED_AS_OPTIONS = listOf("Individual", "Case")

/**
 * Mirrors ocr-staged-invoice-detail.component.html's editable line-item table: every EditText/
 * Spinner mutates the bound OcrScannedInvoiceLineItemDto directly (same shape as the web's
 * two-way [(ngModel)] bindings), and [onChanged] fires after every edit so the host Activity can
 * re-evaluate hasUnsavedChanges/canPushToPurchaseOrders without needing per-field callbacks.
 *
 * TextWatchers/listeners are removed before rebinding a recycled row and re-attached after values
 * are set, so recycling never fires a listener against the WRONG line mid-scroll.
 */
class StagedInvoiceLineAdapter(
    private val lines: List<OcrScannedInvoiceLineItemDto>,
    private val readOnly: Boolean,
    private val onChanged: () -> Unit
) : RecyclerView.Adapter<StagedInvoiceLineAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemStagedInvoiceLineBinding) : RecyclerView.ViewHolder(binding.root) {
        var unitsPerCaseWatcher: TextWatcher? = null
        var shippedQtyWatcher: TextWatcher? = null
        var unitCostWatcher: TextWatcher? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemStagedInvoiceLineBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        val spinnerAdapter = ArrayAdapter(parent.context, android.R.layout.simple_spinner_dropdown_item, SHIPPED_AS_OPTIONS)
        binding.spinnerShippedAs.adapter = spinnerAdapter
        binding.spinnerSellBy.adapter = ArrayAdapter(parent.context, android.R.layout.simple_spinner_dropdown_item, SHIPPED_AS_OPTIONS)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val line = lines[position]
        val b = holder.binding

        b.textItemName.text = line.displayName ?: line.itemName ?: line.itemCode ?: "(Unmatched item)"
        b.textItemMeta.text = listOfNotNull(line.itemCategory, line.subCategory, line.size, line.upcCode)
            .filter { it.isNotBlank() }.joinToString(" • ")

        when {
            line.isNewItem -> { b.textBadge.text = "NEW ITEM"; b.textBadge.setBackgroundColor(0xFFF57C00.toInt()); b.textBadge.visibility = View.VISIBLE }
            line.isNotLinked -> { b.textBadge.text = "NOT LINKED"; b.textBadge.setBackgroundColor(0xFF888888.toInt()); b.textBadge.visibility = View.VISIBLE }
            else -> b.textBadge.visibility = View.GONE
        }

        // Remove old watchers/listeners before touching field values - this view may be a recycled
        // row that still has a PREVIOUS line's watcher attached.
        holder.unitsPerCaseWatcher?.let { b.editUnitsPerCase.removeTextChangedListener(it) }
        holder.shippedQtyWatcher?.let { b.editShippedQty.removeTextChangedListener(it) }
        holder.unitCostWatcher?.let { b.editUnitCost.removeTextChangedListener(it) }
        b.spinnerShippedAs.onItemSelectedListener = null
        b.spinnerSellBy.onItemSelectedListener = null

        b.editUnitsPerCase.setText(line.unitsPerCase?.toString() ?: "")
        b.editShippedQty.setText(shippedQty(line).toString())
        b.editUnitCost.setText(line.unitCost?.let { "%.2f".format(it) } ?: "")
        b.spinnerShippedAs.setSelection(SHIPPED_AS_OPTIONS.indexOf(line.shippedAs).coerceAtLeast(0))
        b.spinnerSellBy.setSelection(SHIPPED_AS_OPTIONS.indexOf(line.sellBy).coerceAtLeast(0))

        renderPricing(b, line)

        val enabled = !readOnly
        b.editUnitsPerCase.isEnabled = enabled
        b.editShippedQty.isEnabled = enabled
        b.editUnitCost.isEnabled = enabled
        b.spinnerShippedAs.isEnabled = enabled
        b.spinnerSellBy.isEnabled = enabled

        holder.unitsPerCaseWatcher = watcherFor { text ->
            line.unitsPerCase = text.toIntOrNull()
            onChanged()
        }.also { b.editUnitsPerCase.addTextChangedListener(it) }

        holder.shippedQtyWatcher = watcherFor { text ->
            setShippedQty(line, text.toIntOrNull() ?: 0)
            onChanged()
        }.also { b.editShippedQty.addTextChangedListener(it) }

        holder.unitCostWatcher = watcherFor { text ->
            line.unitCost = text.toDoubleOrNull()
            onChanged()
        }.also { b.editUnitCost.addTextChangedListener(it) }

        b.spinnerShippedAs.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val newValue = SHIPPED_AS_OPTIONS[pos]
                if (line.shippedAs != newValue) { line.shippedAs = newValue; onChanged() }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        b.spinnerSellBy.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val newValue = SHIPPED_AS_OPTIONS[pos]
                if (line.sellBy != newValue) { line.sellBy = newValue; onChanged() }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun renderPricing(b: ItemStagedInvoiceLineBinding, line: OcrScannedInvoiceLineItemDto) {
        val parts = mutableListOf<String>()
        parts.add("Total shipped: ${totalShipped(line)} units")
        if (line.caseDiscount > 0) parts.add("Discount: $%.2f".format(line.caseDiscount))
        parts.add("Current: ${line.currentUnitPrice?.let { "$%.2f".format(it) } ?: "—"}")
        val suggested = line.suggestedUnitPrice?.let { "$%.2f".format(it) } ?: "—"
        parts.add(if (showRecommended(line)) "Suggested: $suggested ⚠ recommended" else "Suggested: $suggested")
        // desiredMarkUp is a ratio from the backend (0.5 = 50%) - see PurchaseOrderLineAdapter's
        // matching comment.
        line.desiredMarkUp?.let { parts.add("Markup: %.1f%%".format(it * 100)) }
        line.marginStatus?.let { parts.add("Margin: $it") }
        if (line.isMarkupRateDefaulted) parts.add("⚠ default markup rate used")
        b.textPricing.text = parts.joinToString("  •  ")
    }

    // Matches shippedQty()/setShippedQty() in ocr-staged-invoice-detail.component.ts exactly: the
    // raw shipped quantity is cases when shippedAs=='Case', otherwise individual units.
    private fun shippedQty(line: OcrScannedInvoiceLineItemDto): Int =
        if (line.shippedAs == "Case") (line.shippedCases ?: 0) else line.shippedUnits

    private fun setShippedQty(line: OcrScannedInvoiceLineItemDto, value: Int) {
        if (line.shippedAs == "Case") line.shippedCases = value else line.shippedUnits = value
    }

    private fun totalShipped(line: OcrScannedInvoiceLineItemDto): Int =
        if (line.shippedAs == "Case") (line.unitsPerCase ?: 0) * (line.shippedCases ?: 0) else line.shippedUnits

    private fun showRecommended(line: OcrScannedInvoiceLineItemDto): Boolean {
        if (line.suggestedUnitPrice == null) return false
        if (line.isNewItem) return true
        return line.currentUnitPrice != null && line.currentUnitPrice < line.suggestedUnitPrice
    }

    private fun watcherFor(onTextChanged: (String) -> Unit): TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) { onTextChanged(s?.toString() ?: "") }
    }

    override fun getItemCount(): Int = lines.size
}
