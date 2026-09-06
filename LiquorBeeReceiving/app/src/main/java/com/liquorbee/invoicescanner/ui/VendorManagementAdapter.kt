package com.liquorbee.invoicescanner.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.liquorbee.invoicescanner.databinding.ItemVendorBinding
import com.liquorbee.invoicescanner.network.CustomerInvoiceTemplateListItemDto

class VendorManagementAdapter(
    private val vendors: List<CustomerInvoiceTemplateListItemDto>,
    private val onToggled: (CustomerInvoiceTemplateListItemDto, Boolean) -> Unit
) : RecyclerView.Adapter<VendorManagementAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemVendorBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemVendorBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val vendor = vendors[position]
        val b = holder.binding
        b.textVendorName.text = vendor.vendorName
        // Avoid the listener firing from setChecked() itself re-triggering a save during bind/scroll.
        b.switchActive.setOnCheckedChangeListener(null)
        b.switchActive.isChecked = vendor.isActive
        b.switchActive.setOnCheckedChangeListener { _, isChecked -> onToggled(vendor, isChecked) }
    }

    override fun getItemCount(): Int = vendors.size
}
