package com.liquorbee.invoicescanner.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.liquorbee.invoicescanner.R
import com.liquorbee.invoicescanner.databinding.ItemPurchaseOrderBinding
import com.liquorbee.invoicescanner.network.PurchaseOrderHeaderDto

class PurchaseOrderListAdapter(
    private val orders: List<PurchaseOrderHeaderDto>,
    private val onClicked: (PurchaseOrderHeaderDto) -> Unit
) : RecyclerView.Adapter<PurchaseOrderListAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemPurchaseOrderBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPurchaseOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val order = orders[position]
        holder.binding.textVendor.text = order.vendor ?: "(Unknown vendor)"
        holder.binding.textStatus.text = order.status ?: "—"
        applyStatusColor(holder, order.status)
        holder.binding.textOrderId.text = "Order #${order.orderId ?: order.invoiceId}"
        holder.binding.textDates.text = "Submitted ${order.submittedDate?.let { dateOnly(it) } ?: "—"}" +
            (order.shippedDate?.let { " • Shipped ${dateOnly(it)}" } ?: "")
        holder.binding.textTotal.text = "$%.2f".format(order.netAmount)
        holder.binding.textMeta.text = "${order.lineItems} line(s) • ${order.totalCases} case(s)" +
            (order.checkNumber?.takeIf { it.isNotBlank() }?.let { " • Check #$it" } ?: "")

        holder.binding.root.setOnClickListener { onClicked(order) }
    }

    // Matches pos-purchase-orders.css's .status-staged/.status-received exactly (plain
    // orange/green) - anything else (no "Cancelled" color exists on the web either) keeps the
    // default text-only style rather than inventing a color that isn't part of the web's own scheme.
    private fun applyStatusColor(holder: ViewHolder, status: String?) {
        val context = holder.binding.root.context
        val colorRes = when (status?.trim()?.lowercase()) {
            "staged" -> R.color.status_staged
            "received" -> R.color.status_received
            else -> null
        }
        if (colorRes == null) {
            holder.binding.textStatus.setBackgroundColor(Color.TRANSPARENT)
            holder.binding.textStatus.setTextColor(Color.BLACK)
            return
        }
        val pillColor = ContextCompat.getColor(context, colorRes)
        val pill = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 24f
            setColor(pillColor)
        }
        holder.binding.textStatus.background = pill
        holder.binding.textStatus.setTextColor(Color.WHITE)
        holder.binding.textStatus.setPadding(20, 8, 20, 8)
    }

    override fun getItemCount(): Int = orders.size

    // The API returns full ISO datetimes for what are really just dates - a receiving time-of-day
    // was never meaningful here, so only the date part is shown.
    private fun dateOnly(raw: String): String = raw.substringBefore('T').substringBefore(' ')
}
