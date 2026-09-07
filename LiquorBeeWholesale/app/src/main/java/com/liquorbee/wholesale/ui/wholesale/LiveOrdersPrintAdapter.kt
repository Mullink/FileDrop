package com.liquorbee.wholesale.ui.wholesale

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.liquorbee.wholesale.databinding.ItemLiveOrderRowBinding
import com.liquorbee.wholesale.network.WholesaleLiveOpenOrderDto

class LiveOrdersPrintAdapter(
    private val orders: List<WholesaleLiveOpenOrderDto>,
    private val onPrint: (WholesaleLiveOpenOrderDto) -> Unit
) : RecyclerView.Adapter<LiveOrdersPrintAdapter.ViewHolder>() {

    // orderId -> (status text, isError, isPrinting) - kept outside the ViewHolder so a status
    // survives view recycling on scroll.
    private val statusById = mutableMapOf<String, Triple<String, Boolean, Boolean>>()

    class ViewHolder(val binding: ItemLiveOrderRowBinding) : RecyclerView.ViewHolder(binding.root)

    fun setStatus(orderId: String, text: String, isError: Boolean, isPrinting: Boolean) {
        statusById[orderId] = Triple(text, isError, isPrinting)
        val index = orders.indexOfFirst { it.orderId == orderId }
        if (index >= 0) notifyItemChanged(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLiveOrderRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount() = orders.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val o = orders[position]
        val b = holder.binding
        b.textOrderNumber.text = o.orderNumber ?: o.orderId ?: "(no order #)"
        b.textCustomer.text = o.customerName ?: "—"
        b.textTotal.text = "$%.2f".format(o.total)
        b.textBalance.text = "Bal: $%.2f".format(o.balance)
        b.textBalance.setTextColor(if (o.balance > 0) Color.parseColor("#C62828") else Color.parseColor("#2E6B4F"))
        b.textDate.text = o.createdDate?.substringBefore('T')?.substringBefore(' ') ?: ""
        b.textStatus.text = orderStatusLabel(o.orderStatus)
        b.textStatus.setBackgroundColor(when (o.orderStatus) {
            2 -> Color.parseColor("#2E6B4F")
            3, 4 -> Color.parseColor("#888888")
            else -> Color.parseColor("#3B6FB0")
        })

        val status = o.orderId?.let { statusById[it] }
        if (status != null) {
            val (text, isError, isPrinting) = status
            b.textPrintStatus.visibility = View.VISIBLE
            b.textPrintStatus.text = text
            b.textPrintStatus.setTextColor(if (isError) Color.parseColor("#C62828") else Color.parseColor("#2E6B4F"))
            b.buttonPrint.isEnabled = !isPrinting
        } else {
            b.textPrintStatus.visibility = View.GONE
            b.buttonPrint.isEnabled = true
        }

        b.buttonPrint.setOnClickListener { o.orderId?.let { onPrint(o) } }
    }
}
