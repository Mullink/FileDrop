package com.liquorbee.wholesale.ui.wholesale

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
 * PlaceOrderFragment's debounced, background-thread filtering).
 *
 * Order Qty is entered via NumberPadDialog, not the system keyboard - editQty itself is a
 * display-only, tap-to-open field (showSoftInputOnFocus=false, no TextWatcher) so a POS terminal
 * never pops the full software keyboard for what's always just a small integer. */
class CatalogAdapter(
    private var items: List<SubCustomerCatalogItemDto>,
    private val useSubCustomerPrice: Boolean,
    private val onCartChanged: () -> Unit
) : RecyclerView.Adapter<CatalogAdapter.ViewHolder>() {

    private val quantities = mutableMapOf<String, Int>()

    inner class ViewHolder(val binding: ItemCatalogRowBinding) : RecyclerView.ViewHolder(binding.root)

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
        binding.editQty.showSoftInputOnFocus = false
        binding.editQty.isCursorVisible = false
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

        fun refreshQtyDisplay() {
            val qty = quantities[item.itemCode] ?: 0
            // Empty, not "0" - a visible "0" invites typing straight after it (accidentally
            // making "10" instead of "1"); the hint still shows "0" as a placeholder.
            b.editQty.setText(if (qty > 0) qty.toString() else "")
            b.textLineTotal.text = if (qty > 0) "$%.2f".format(price * qty) else ""
        }
        refreshQtyDisplay()

        val openPad = android.view.View.OnClickListener {
            val itemName = item.itemName ?: item.itemCode ?: "this item"
            NumberPadDialog.show(b.root.context, itemName, quantities[item.itemCode] ?: 0) { newQty ->
                val code = item.itemCode ?: return@show
                if (newQty > 0) quantities[code] = newQty else quantities.remove(code)
                refreshQtyDisplay()
                onCartChanged()
            }
        }
        b.editQty.setOnClickListener(openPad)
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
