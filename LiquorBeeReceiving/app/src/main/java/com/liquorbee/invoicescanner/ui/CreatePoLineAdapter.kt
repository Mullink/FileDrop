package com.liquorbee.invoicescanner.ui

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.liquorbee.invoicescanner.databinding.ItemCreatePoLineBinding
import com.liquorbee.invoicescanner.network.PurchaseOrderLineDto

/**
 * Editable line list for CreatePurchaseOrderActivity - mirrors the web's
 * updateManualPOShippedUnits/updateManualPOUnitCost math exactly: editing Cases recomputes
 * ShippedUnits = Cases x UnitsPerCase, editing Unit Cost recomputes CaseCost = UnitCost x
 * UnitsPerCase. Mutates the bound PurchaseOrderLineDto in place, same pattern as
 * PurchaseOrderLineAdapter/StagedInvoiceLineAdapter.
 */
class CreatePoLineAdapter(
    private val lines: MutableList<PurchaseOrderLineDto>,
    private val onRemoved: (Int) -> Unit,
    private val onLineChanged: () -> Unit
) : RecyclerView.Adapter<CreatePoLineAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemCreatePoLineBinding) : RecyclerView.ViewHolder(binding.root) {
        var casesWatcher: TextWatcher? = null
        var unitCostWatcher: TextWatcher? = null
        var priceWatcher: TextWatcher? = null
        var unitsPerCaseWatcher: TextWatcher? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCreatePoLineBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val line = lines[position]
        val b = holder.binding

        b.textItemName.text = line.displayName ?: line.itemName ?: line.itemCode ?: "(Unknown item)"
        b.textItemMeta.text = listOfNotNull(line.itemCategory, line.upcCode).filter { it.isNotBlank() }.joinToString(" • ")
        // Set once at add-time by the Receive Type dialog (unitsPerCase pinned to 1 for Bottles) -
        // display-only here, matching the user's ask that the qty field just say "Qty" and the
        // chosen type show separately rather than the field label itself flipping.
        b.textQtyType.text = "Qty Type: " + if ((line.unitsPerCase ?: 1) == 1) "Bottles" else "Cases"

        holder.casesWatcher?.let { b.editCases.removeTextChangedListener(it) }
        holder.unitCostWatcher?.let { b.editUnitCost.removeTextChangedListener(it) }
        holder.priceWatcher?.let { b.editPrice.removeTextChangedListener(it) }
        holder.unitsPerCaseWatcher?.let { b.editUnitsPerCase.removeTextChangedListener(it) }

        b.editCases.setText((line.shippedCases ?: 1).toString())
        b.editUnitCost.setText(line.unitCost?.let { "%.2f".format(it) } ?: "")
        b.editPrice.setText(line.currentUnitPrice?.let { "%.2f".format(it) } ?: "")
        b.editUnitsPerCase.setText((line.unitsPerCase ?: 1).toString())
        renderTotal(b, line)

        holder.casesWatcher = watcherFor { text ->
            line.shippedCases = text.toIntOrNull()?.coerceAtLeast(0) ?: 0
            line.shippedUnits = (line.shippedCases ?: 0) * (line.unitsPerCase ?: 1)
            renderTotal(b, line)
            onLineChanged()
        }.also { b.editCases.addTextChangedListener(it) }

        holder.unitCostWatcher = watcherFor { text ->
            line.unitCost = text.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
            line.caseCost = (line.unitCost ?: 0.0) * (line.unitsPerCase ?: 1)
            renderTotal(b, line)
            onLineChanged()
        }.also { b.editUnitCost.addTextChangedListener(it) }

        holder.priceWatcher = watcherFor { text ->
            line.currentUnitPrice = text.toDoubleOrNull()
            renderTotal(b, line)
        }.also { b.editPrice.addTextChangedListener(it) }

        // Editable after the item's already on the order, not just at add-time - the catalog's
        // own unitsPerCase has been wrong often enough to need a quick correction here too. Unit
        // Cost stays the authoritative field on this screen (matches the watcher above), so
        // changing this recomputes caseCost/shippedUnits from it rather than the other way around.
        holder.unitsPerCaseWatcher = watcherFor { text ->
            line.unitsPerCase = text.toIntOrNull()?.coerceAtLeast(1) ?: 1
            line.shippedUnits = (line.shippedCases ?: 0) * (line.unitsPerCase ?: 1)
            line.caseCost = (line.unitCost ?: 0.0) * (line.unitsPerCase ?: 1)
            b.textQtyType.text = "Qty Type: " + if ((line.unitsPerCase ?: 1) == 1) "Bottles" else "Cases"
            renderTotal(b, line)
            onLineChanged()
        }.also { b.editUnitsPerCase.addTextChangedListener(it) }

        b.buttonRemove.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onRemoved(pos)
        }
    }

    // Simple 2-tier color: red for a loss/break-even (price at or below cost), green for any real
    // margin. No per-category "desired markup" comparison here (that rule lookup isn't loaded on
    // this screen) - this is just "are we losing money on this line or not."
    private fun renderTotal(b: ItemCreatePoLineBinding, line: PurchaseOrderLineDto) {
        val units = line.shippedUnits ?: 0
        val caseCost = line.caseCost ?: 0.0
        val unitCost = line.unitCost ?: 0.0
        val price = line.currentUnitPrice ?: 0.0
        val lineTotal = (line.shippedCases ?: 0) * caseCost

        val marginText: String
        val marginColor: Int
        if (unitCost > 0.0) {
            val marginPct = (price - unitCost) / unitCost * 100.0
            marginText = " • Margin: %.1f%%".format(marginPct)
            marginColor = if (marginPct <= 0.0) 0xFFC62828.toInt() else 0xFF2E7D32.toInt()
        } else {
            marginText = ""
            marginColor = 0xFF888888.toInt()
        }

        b.textLineTotal.text = "$units unit(s) • Case cost: $%.2f • Line total: $%.2f%s".format(caseCost, lineTotal, marginText)
        b.textLineTotal.setTextColor(marginColor)
    }

    private fun watcherFor(onTextChanged: (String) -> Unit): TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) { onTextChanged(s?.toString() ?: "") }
    }

    override fun getItemCount(): Int = lines.size
}
