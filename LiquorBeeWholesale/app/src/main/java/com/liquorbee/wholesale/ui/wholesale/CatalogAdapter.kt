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
 * doesn't say which one to use, the client picks based on which mode is active.
 *
 * Built to stay smooth with 10k-30k catalog rows: cart state is a Map keyed by itemCode (not row
 * position), so RecyclerView's normal view-recycling is safe with no per-row identity bugs, and
 * updateItems() swaps the backing list in place with notifyDataSetChanged() rather than
 * reconstructing/re-attaching a whole new adapter on every search keystroke (see
 * PlaceOrderFragment's debounced, background-thread filtering). */
class CatalogAdapter(
    private var items: List<SubCustomerCatalogItemDto>,
    private val useSubCustomerPrice: Boolean,
    private val onCartChanged: () -> Unit
) : RecyclerView.Adapter<CatalogAdapter.ViewHolder>() {

    private val quantities = mutableMapOf<String, Int>()

    inner class ViewHolder(val binding: ItemCatalogRowBinding) : RecyclerView.ViewHolder(binding.root) {
        var watcher: TextWatcher? = null
    }

    fun updateItems(newItems: List<SubCustomerCatalogItemDto>) {
        items = newItems
        notifyDataSetChanged()
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

        b.textItemCode.text = item.itemCode ?: "—"
        b.textItemName.text = item.itemName ?: "(unnamed item)"
        b.textQtyOnHand.text = item.qtyOnHand.toString()
        b.textQtyOnHand.setTextColor(qtyOnHandColor(item.qtyOnHand))
        b.textPrice.text = "$%.2f".format(price)
        b.textPrice.setTextColor(COLOR_GOOD)

        holder.watcher?.let { b.editQty.removeTextChangedListener(it) }
        val qty = quantities[item.itemCode] ?: 0
        // Empty, not "0" - typing into a field that already shows "0" risks becoming "10" instead
        // of "1" if the old digit isn't cleared/selected first. The hint still shows "0" as a
        // placeholder so the field doesn't look broken/blank.
        b.editQty.setText(if (qty > 0) qty.toString() else "")
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

    // 0 = out of stock (red), 1-4 = running low (orange), 5+ = plenty (green) - a quick glance at
    // the column should be enough to tell staff whether an order is safe to fulfill.
    private fun qtyOnHandColor(qty: Int): Int = when {
        qty <= 0 -> COLOR_BAD
        qty < 5 -> COLOR_WARN
        else -> COLOR_GOOD
    }

    companion object {
        private const val COLOR_GOOD = 0xFF2E6B4F.toInt()
        private const val COLOR_WARN = 0xFFB9812E.toInt()
        private const val COLOR_BAD = 0xFFC62828.toInt()
    }
}
