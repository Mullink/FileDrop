package com.liquorbee.wholesale.ui.wholesale

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.liquorbee.wholesale.databinding.ItemOrderRowBinding
import com.liquorbee.wholesale.network.WholesaleOpenOrderDto

// The backend already excludes wholesale orders with no real PosOrderNumber from
// GetWholesaleOpenOrders (they never reached the POS), so orderNumber should always be present
// here - but defensively never surface the internal "WH-" external id if it ever leaks through;
// show "Pending" instead of a raw code that means nothing to a cashier.
fun displayOrderLabel(orderNumber: String?, orderId: String?): String {
    if (!orderNumber.isNullOrBlank()) return orderNumber
    if (!orderId.isNullOrBlank() && !orderId.startsWith("WH-", ignoreCase = true)) return orderId
    return "Pending"
}

// orderStatus: 1=Open, 2=Ready, 3=Closed, 4=Cancelled (see LiquorBeePosOrderService.MapStringStatusToInt).
fun orderStatusLabel(status: Int): String = when (status) {
    2 -> "Ready"
    3 -> "Closed"
    4 -> "Cancelled"
    else -> "Open"
}

private fun orderStatusColor(status: Int): Int = when (status) {
    2 -> Color.parseColor("#2E6B4F")
    3, 4 -> Color.parseColor("#888888")
    else -> Color.parseColor("#3B6FB0")
}

class OrdersAdapter(
    private val orders: List<WholesaleOpenOrderDto>,
    private val onClick: (WholesaleOpenOrderDto) -> Unit
) : RecyclerView.Adapter<OrdersAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemOrderRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemOrderRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount() = orders.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val o = orders[position]
        val b = holder.binding
        b.textOrderNumber.text = displayOrderLabel(o.orderNumber, o.orderId)
        b.textCustomer.text = o.customerName ?: o.customerEmail ?: o.customerPhone ?: "—"
        b.textTotal.text = "$%.2f".format(o.total)
        b.textBalance.text = "Bal: $%.2f".format(o.balance)
        b.textBalance.setTextColor(if (o.balance > 0) Color.parseColor("#C62828") else Color.parseColor("#2E6B4F"))
        b.textDate.text = o.createdDate?.substringBefore('T')?.substringBefore(' ') ?: ""
        b.textStatus.text = orderStatusLabel(o.orderStatus)
        b.textStatus.setBackgroundColor(orderStatusColor(o.orderStatus))
        b.root.setOnClickListener { onClick(o) }
    }
}
