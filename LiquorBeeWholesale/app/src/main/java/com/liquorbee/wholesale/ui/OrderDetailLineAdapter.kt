package com.liquorbee.wholesale.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.liquorbee.wholesale.databinding.ItemOrderDetailLineBinding
import com.liquorbee.wholesale.network.WholesaleOpenOrderItemDto

/** Read-only line display - editing an order's items now happens by jumping into Place Order
 * with the order pre-filled into the cart (see EditOrderPrefill), matching the web's
 * editOrderFromManagement flow, instead of an inline qty/price editor on this screen.
 * itemCode doubles as the item's SKU (there's no separate SKU field server-side). */
class OrderDetailLineAdapter(private val lines: List<WholesaleOpenOrderItemDto>) :
    RecyclerView.Adapter<OrderDetailLineAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemOrderDetailLineBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemOrderDetailLineBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount() = lines.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val line = lines[position]
        val b = holder.binding
        b.textItemName.text = line.itemName ?: "(unnamed item)"
        b.textItemCode.text = "SKU: ${line.itemCode ?: "—"}"
        b.textQty.text = "×${line.quantity}"
        b.textPrice.text = "$%.2f".format(line.price)
    }
}
