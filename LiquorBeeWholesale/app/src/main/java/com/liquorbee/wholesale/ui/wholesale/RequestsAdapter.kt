package com.liquorbee.wholesale.ui.wholesale

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.liquorbee.wholesale.databinding.ItemRequestRowBinding
import com.liquorbee.wholesale.network.SubCustomerRequestDto

class RequestsAdapter(
    private val requests: List<SubCustomerRequestDto>,
    private val onCancel: (SubCustomerRequestDto) -> Unit
) : RecyclerView.Adapter<RequestsAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemRequestRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRequestRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount() = requests.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val r = requests[position]
        val b = holder.binding
        b.textBusinessName.text = r.toBusinessName ?: r.recipientBusinessName ?: r.toEmailAddress ?: "(No business name)"
        b.textEmail.text = r.toEmailAddress ?: ""
        b.textPriceList.text = r.priceListName?.let { "Price list: $it" } ?: ""
        b.textDates.text = "Sent ${dateOnly(r.createDate)}  •  Updated ${dateOnly(r.modDate)}"

        b.textStatus.text = r.status
        b.textStatus.setBackgroundColor(statusColor(r.status))

        if (r.status == "Proposed") {
            b.buttonCancel.visibility = View.VISIBLE
            b.buttonCancel.setOnClickListener { onCancel(r) }
        } else {
            b.buttonCancel.visibility = View.GONE
        }
    }

    private fun statusColor(status: String): Int = when (status) {
        "Proposed" -> Color.parseColor("#B9812E")
        "Linked" -> Color.parseColor("#2E6B4F")
        "Rejected", "Deleted" -> Color.parseColor("#888888")
        else -> Color.parseColor("#888888")
    }

    private fun dateOnly(raw: String): String = raw.substringBefore('T').substringBefore(' ')
}
