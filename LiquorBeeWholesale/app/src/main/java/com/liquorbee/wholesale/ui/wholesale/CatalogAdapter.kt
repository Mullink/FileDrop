package com.liquorbee.wholesale.ui.wholesale

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.liquorbee.wholesale.databinding.ItemCatalogRowBinding
import com.liquorbee.wholesale.network.SubCustomerCatalogItemDto

/** priceFor(item) matches the web's management-place-order.component.ts exactly: General
 * Customer mode uses retailPrice, a specific linked account uses subCustomerPrice - the backend
 * doesn't say which one to use, the client picks based on which mode is active. */
class CatalogAdapter(
    private val items: List<SubCustomerCatalogItemDto>,
    private val useSubCustomerPrice: Boolean,
    private val onCartChanged: () -> Unit
) : RecyclerView.Adapter<CatalogAdapter.ViewHolder>() {

    private val quantities = mutableMapOf<String, Int>()

    inner class ViewHolder(val binding: ItemCatalogRowBinding) : RecyclerView.ViewHolder(binding.root) {
        var watcher: TextWatcher? = null
    }

    fun priceFor(item: SubCustomerCatalogItemDto): Double = if (useSubCustomerPrice) item.subCustomerPrice else item.retailPrice

    fun cartLines(): List<Pair<SubCustomerCatalogItemDto, Int>> =
        items.mapNotNull { item -> quantities[item.itemCode]?.takeIf { it > 0 }?.let { item to it } }

    fun cartTotal(): Double = cartLines().sumOf { (item, qty) -> priceFor(item) * qty }
    fun cartCount(): Int = cartLines().sumOf { it.second }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCatalogRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val b = holder.binding
        val price = priceFor(item)

        b.textItemName.text = "${item.itemCode ?: "?"} — ${item.itemName ?: "(unnamed item)"}"
        b.textItemMeta.text = "Qty on hand: ${item.qtyOnHand}  •  $%.2f".format(price)

        holder.watcher?.let { b.editQty.removeTextChangedListener(it) }
        val qty = quantities[item.itemCode] ?: 0
        b.editQty.setText(qty.toString())
        b.textLineTotal.text = if (qty > 0) "$%.2f".format(price * qty) else ""

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val newQty = s?.toString()?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                val code = item.itemCode ?: return
                if (newQty > 0) quantities[code] = newQty else quantities.remove(code)
                b.textLineTotal.text = if (newQty > 0) "$%.2f".format(price * newQty) else ""
                onCartChanged()
            }
        }
        b.editQty.addTextChangedListener(watcher)
        holder.watcher = watcher
    }
}
