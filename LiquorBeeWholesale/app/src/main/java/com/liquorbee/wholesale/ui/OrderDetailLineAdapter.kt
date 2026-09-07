package com.liquorbee.wholesale.ui

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.liquorbee.wholesale.databinding.ItemOrderDetailLineBinding
import com.liquorbee.wholesale.network.WholesaleOpenOrderItemDto

/** Edits WholesaleOpenOrderItemDto in place (quantity/price are `var`) - the whole list gets
 * POSTed back as-is to PatchManagementOrder, same convention as the receiving app's PO editors.
 * Setting quantity to 0 and saving removes that line (server drops zero-qty items; an empty
 * resulting order gets deleted entirely - see SubCustomerService.PatchWholesaleOpenOrderByCustomerAsync). */
class OrderDetailLineAdapter(private val lines: List<WholesaleOpenOrderItemDto>) :
    RecyclerView.Adapter<OrderDetailLineAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemOrderDetailLineBinding) : RecyclerView.ViewHolder(binding.root) {
        var qtyWatcher: TextWatcher? = null
        var priceWatcher: TextWatcher? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemOrderDetailLineBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount() = lines.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val line = lines[position]
        val b = holder.binding

        holder.qtyWatcher?.let { b.editQty.removeTextChangedListener(it) }
        holder.priceWatcher?.let { b.editPrice.removeTextChangedListener(it) }

        b.textItemName.text = line.itemName ?: "(unnamed item)"
        b.textItemCode.text = line.itemCode ?: ""
        b.editQty.setText(line.quantity.toString())
        b.editPrice.setText("%.2f".format(line.price))

        val qtyWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { line.quantity = s?.toString()?.toIntOrNull() ?: 0 }
        }
        b.editQty.addTextChangedListener(qtyWatcher)
        holder.qtyWatcher = qtyWatcher

        val priceWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { line.price = s?.toString()?.toDoubleOrNull() ?: 0.0 }
        }
        b.editPrice.addTextChangedListener(priceWatcher)
        holder.priceWatcher = priceWatcher
    }
}
