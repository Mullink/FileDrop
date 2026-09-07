package com.liquorbee.wholesale.ui.wholesale

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.liquorbee.wholesale.databinding.ItemPosCustomerRowBinding
import com.liquorbee.wholesale.network.WholesaleQuanticCustomerDto

class PosCustomersAdapter(
    private val customers: List<WholesaleQuanticCustomerDto>,
    private val onSendRequest: (WholesaleQuanticCustomerDto) -> Unit,
    private val onManageLink: (WholesaleQuanticCustomerDto) -> Unit
) : RecyclerView.Adapter<PosCustomersAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemPosCustomerRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPosCustomerRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount() = customers.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val c = customers[position]
        val b = holder.binding
        b.textDisplayName.text = (c.displayName ?: "(No name)") + if (c.isLinked) "  🔗" else ""
        b.textContact.text = listOfNotNull(c.emailAddress, c.phoneNumber).joinToString(" • ")

        if (c.isLinked) {
            b.buttonAction.visibility = android.view.View.GONE
        } else if (c.requestId != null) {
            b.buttonAction.visibility = android.view.View.VISIBLE
            b.buttonAction.text = "Manage Link"
            b.buttonAction.setOnClickListener { onManageLink(c) }
        } else {
            b.buttonAction.visibility = android.view.View.VISIBLE
            b.buttonAction.text = "Send Request"
            b.buttonAction.setOnClickListener { onSendRequest(c) }
        }
    }
}
