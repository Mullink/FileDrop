package com.liquorbee.wholesale.ui.wholesale

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.liquorbee.wholesale.databinding.ItemLinkRowBinding
import com.liquorbee.wholesale.network.SubCustomerDto

class LinksAdapter(
    private val links: List<SubCustomerDto>,
    private val onUnlink: (SubCustomerDto) -> Unit
) : RecyclerView.Adapter<LinksAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemLinkRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLinkRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount() = links.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val l = links[position]
        val b = holder.binding
        b.textStoreName.text = l.storeName ?: l.emailAddress
        b.textEmail.text = l.emailAddress
        b.textDetails.text = listOfNotNull(l.accountType, l.city, l.state, l.phoneNumber).filter { it.isNotBlank() }.joinToString(" • ")
        b.buttonUnlink.setOnClickListener { onUnlink(l) }
    }
}
