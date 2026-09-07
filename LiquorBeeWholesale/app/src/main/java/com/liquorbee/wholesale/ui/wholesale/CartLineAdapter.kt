package com.liquorbee.wholesale.ui.wholesale

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.liquorbee.wholesale.databinding.ItemCartLineBinding
import com.liquorbee.wholesale.network.SubCustomerCatalogItemDto

class CartLineAdapter(
    private var lines: List<Pair<SubCustomerCatalogItemDto, Int>>,
    private val priceFor: (SubCustomerCatalogItemDto) -> Double,
    private val onEditQty: (SubCustomerCatalogItemDto, Int) -> Unit,
    private val onRemove: (String) -> Unit
) : RecyclerView.Adapter<CartLineAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemCartLineBinding) : RecyclerView.ViewHolder(binding.root)

    fun updateLines(newLines: List<Pair<SubCustomerCatalogItemDto, Int>>) {
        lines = newLines
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCartLineBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount() = lines.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (item, qty) = lines[position]
        val b = holder.binding
        val price = priceFor(item)
        b.textItemName.text = item.itemName ?: "(unnamed item)"
        b.textItemCode.text = "SKU: ${item.itemCode ?: "—"}"
        b.textQty.text = "×$qty"
        b.textLineTotal.text = "$%.2f".format(price * qty)
        // Tap anywhere on the row except the remove button to open the number pad for this line -
        // matches the catalog row's own "tap the qty to edit it" convention.
        b.root.setOnClickListener { onEditQty(item, qty) }
        b.buttonRemove.setOnClickListener { item.itemCode?.let { onRemove(it) } }
    }
}
