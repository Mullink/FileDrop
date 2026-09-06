package com.liquorbee.invoicescanner.ui

import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.liquorbee.invoicescanner.databinding.ItemPurchaseOrderLineBinding
import com.liquorbee.invoicescanner.network.PurchaseOrderLineDto

/**
 * Mirrors pos-purchase-orders.ts's receive-line editing (updateShippedUnits/onManualUnitsChange)
 * exactly, but presented as ONE Qty field instead of separate Cases+Units fields - the field's
 * meaning depends on Qty Type, derived from unitsPerCase == 1 (same convention as
 * CreatePoLineAdapter.kt's textQtyType): Qty Type Cases -> editing Qty recomputes
 * shippedUnits = Qty x unitsPerCase (the old updateShippedUnits math); Qty Type Bottles -> Qty IS
 * shippedUnits directly (the old onManualUnitsChange math), shippedCases left untouched either way.
 *
 * Unit Cost is now also editable (previously read-only) - editing it recomputes
 * caseCost = unitCost x unitsPerCase, matching CreatePoLineAdapter's unitCostWatcher exactly, so
 * the two adapters stay consistent with each other.
 *
 * Every EditText mutates the bound PurchaseOrderLineDto in place, matching the web's two-way
 * bindings on the same row objects - the whole list is POSTed back as-is on Save/Receive.
 */
class PurchaseOrderLineAdapter(
    private val lines: List<PurchaseOrderLineDto>,
    private val readOnly: Boolean,
    private val onChanged: () -> Unit,
    private val onRemoved: (Int) -> Unit
) : RecyclerView.Adapter<PurchaseOrderLineAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemPurchaseOrderLineBinding) : RecyclerView.ViewHolder(binding.root) {
        var qtyWatcher: TextWatcher? = null
        var unitCostWatcher: TextWatcher? = null
        var priceWatcher: TextWatcher? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPurchaseOrderLineBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val line = lines[position]
        val b = holder.binding

        b.textItemName.text = line.displayName ?: line.itemName ?: line.itemCode ?: "(Unmatched item)"
        b.textItemMeta.text = listOfNotNull(line.itemCode, line.itemCategory, line.subCategory)
            .filter { it.isNotBlank() }.joinToString(" • ")
        b.textWarningBadge.visibility = if (line.isNotLinked || line.hasDuplicates) View.VISIBLE else View.GONE

        // Matches the web's Curr. Qty column exactly (#16a34a green / #dc2626 red).
        b.textCurrQty.text = "Qty: ${line.currentQty ?: 0}"
        b.textCurrQty.setTextColor(if ((line.currentQty ?: 0) > 0) 0xFF16A34A.toInt() else 0xFFDC2626.toInt())

        b.textAvgCost.text = "Units/case: ${line.unitsPerCase ?: 0} • Avg cost: ${money(line.averageCost)}"
        renderCostIncrease(b, line)
        renderMarginBadge(b, line)

        b.buttonRemove.visibility = if (readOnly) View.GONE else View.VISIBLE
        b.buttonRemove.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onRemoved(pos)
        }

        val isBottles = (line.unitsPerCase ?: 1) == 1
        b.textQtyType.text = "Qty Type: " + if (isBottles) "Bottles" else "Cases"

        holder.qtyWatcher?.let { b.editQty.removeTextChangedListener(it) }
        holder.unitCostWatcher?.let { b.editUnitCost.removeTextChangedListener(it) }
        holder.priceWatcher?.let { b.editCurrentUnitPrice.removeTextChangedListener(it) }

        b.editQty.setText((if (isBottles) line.shippedUnits else line.shippedCases)?.toString() ?: "")
        b.editUnitCost.setText(line.unitCost?.let { "%.2f".format(it) } ?: "")
        b.editCurrentUnitPrice.setText(line.currentUnitPrice?.let { "%.2f".format(it) } ?: "")
        renderPricing(b, line)

        val enabled = !readOnly
        b.editQty.isEnabled = enabled
        b.editUnitCost.isEnabled = enabled
        b.editCurrentUnitPrice.isEnabled = enabled

        val suggested = line.suggestedUnitPrice
        b.buttonUseSuggestedPrice.visibility = if (enabled && suggested != null && suggested > 0.0) View.VISIBLE else View.GONE
        b.buttonUseSuggestedPrice.setOnClickListener {
            // Just sets the text - the priceWatcher already attached below picks up the change
            // and handles line.currentUnitPrice/renderPricing/onChanged, same as if the user had
            // typed it in themselves.
            b.editCurrentUnitPrice.setText(suggested?.let { "%.2f".format(it) } ?: "")
        }

        holder.qtyWatcher = watcherFor { text ->
            val qty = text.toIntOrNull()
            if (isBottles) {
                // onManualUnitsChange - deliberately does NOT touch shippedCases, matching the web.
                line.shippedUnits = qty
            } else {
                // updateShippedUnits - Cases entered directly, Units autofilled from Cases x unitsPerCase.
                line.shippedCases = qty
                line.shippedUnits = (qty ?: 0) * (line.unitsPerCase ?: 0)
            }
            onChanged()
        }.also { b.editQty.addTextChangedListener(it) }

        holder.unitCostWatcher = watcherFor { text ->
            val unitCost = text.toDoubleOrNull()
            line.unitCost = unitCost
            // Matches CreatePoLineAdapter's unitCostWatcher exactly - keeps caseCost consistent.
            line.caseCost = (unitCost ?: 0.0) * (line.unitsPerCase ?: 1)
            renderPricing(b, line)
            onChanged()
        }.also { b.editUnitCost.addTextChangedListener(it) }

        holder.priceWatcher = watcherFor { text ->
            line.currentUnitPrice = text.toDoubleOrNull()
            renderPricing(b, line)
            onChanged()
        }.also { b.editCurrentUnitPrice.addTextChangedListener(it) }
    }

    // Same red/loss-or-break-even, green/real-margin convention as CreatePoLineAdapter, computed
    // the same way (price vs unit cost) rather than trusting marginStatus's own wording alone -
    // that field can be null (a brand-new item with no prior price to compare against).
    private fun renderPricing(b: ItemPurchaseOrderLineBinding, line: PurchaseOrderLineDto) {
        val parts = mutableListOf<String>()
        parts.add("Suggested: ${money(line.suggestedUnitPrice)}")
        // desiredMarkUp is a ratio from the backend (0.5 = 50%), same convention the web app uses
        // (pos-purchase-orders.html multiplies by 100 at every display site) - without this *100
        // a 50% markup rendered as "0.5%".
        line.desiredMarkUp?.let { parts.add("Markup: %.1f%%".format(it * 100)) }
        line.marginStatus?.let { parts.add("Margin: $it") }
        if (line.isNewItem) parts.add("NEW ITEM")
        if (line.hasPriceChange) parts.add("price changed since staging")
        b.textPricing.text = parts.joinToString("  •  ")

        val unitCost = line.unitCost ?: 0.0
        val price = line.currentUnitPrice ?: 0.0
        b.textPricing.setTextColor(
            if (unitCost <= 0.0) 0xFF888888.toInt()
            else if (price - unitCost <= 0.0) 0xFFC62828.toInt()
            else 0xFF2E7D32.toInt()
        )
    }

    // Matches the web's Cost Increase column (pos-purchase-orders.html) - red up-arrow when
    // unitCost rose vs oldCog, green down-arrow when it dropped, gray $0.00 when unchanged/no
    // prior cost to compare against.
    private fun renderCostIncrease(b: ItemPurchaseOrderLineBinding, line: PurchaseOrderLineDto) {
        val oldCog = line.oldCog ?: 0.0
        val unitCost = line.unitCost ?: 0.0
        if (oldCog <= 0.0) {
            b.textCostIncrease.text = ""
            return
        }
        val delta = unitCost - oldCog
        val pct = (delta / oldCog) * 100.0
        when {
            delta > 0.0 -> {
                b.textCostIncrease.text = "↑ ${money(delta)} (%.1f%%)".format(pct)
                b.textCostIncrease.setTextColor(0xFFDC2626.toInt())
            }
            delta < 0.0 -> {
                b.textCostIncrease.text = "↓ (${money(-delta)}) (%.1f%%)".format(-pct)
                b.textCostIncrease.setTextColor(0xFF16A34A.toInt())
            }
            else -> {
                b.textCostIncrease.text = "$0.00"
                b.textCostIncrease.setTextColor(0xFF888888.toInt())
            }
        }
    }

    // Above Margin (green pill) / Under Margin (red pill) - matches the web's
    // badge-above-margin/badge-under-margin CSS classes exactly (pos-purchase-orders.css).
    private fun renderMarginBadge(b: ItemPurchaseOrderLineBinding, line: PurchaseOrderLineDto) {
        val status = line.marginStatus?.trim()
        if (status.isNullOrEmpty()) {
            b.textMarginBadge.visibility = View.GONE
            return
        }
        val isUnder = status == "Under Margin"
        b.textMarginBadge.visibility = View.VISIBLE
        b.textMarginBadge.text = status
        b.textMarginBadge.setTextColor(if (isUnder) 0xFF991B1B.toInt() else 0xFF065F46.toInt())
        b.textMarginBadge.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 24f
            setColor(if (isUnder) 0xFFFEE2E2.toInt() else 0xFFD1FAE5.toInt())
        }
    }

    private fun money(value: Double?): String = value?.let { "$%.2f".format(it) } ?: "—"

    private fun watcherFor(onTextChanged: (String) -> Unit): TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) { onTextChanged(s?.toString() ?: "") }
    }

    override fun getItemCount(): Int = lines.size
}
