package com.liquorbee.invoicescanner.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.liquorbee.invoicescanner.databinding.ItemStagedInvoiceBinding
import com.liquorbee.invoicescanner.network.OcrScannedInvoiceHeaderDto
import java.text.SimpleDateFormat
import java.util.Locale

class StagedInvoiceListAdapter(
    private val invoices: List<OcrScannedInvoiceHeaderDto>,
    private val onClicked: (OcrScannedInvoiceHeaderDto) -> Unit
) : RecyclerView.Adapter<StagedInvoiceListAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemStagedInvoiceBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemStagedInvoiceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val inv = invoices[position]
        holder.binding.textVendor.text = inv.vendorName
        holder.binding.textStatus.text = inv.status
        holder.binding.textInvoiceNumber.text = "Invoice #${inv.invoiceNumber}"
        holder.binding.textDate.text = formatInvoiceDate(inv.invoiceDate) + " • ${inv.lineItemCount} line(s)"
        holder.binding.textTotal.text = inv.invoiceTotal?.let { "$%.2f".format(it) } ?: "—"

        if (inv.legacyInvoiceId != null) {
            holder.binding.textPoStatus.text = "✓ Staged to Purchase Orders (#${inv.legacyInvoiceId})"
            holder.binding.textPoStatus.setTextColor(0xFF2E7D32.toInt())
        } else {
            holder.binding.textPoStatus.text = "Not yet pushed to Purchase Orders"
            holder.binding.textPoStatus.setTextColor(0xFF888888.toInt())
        }

        holder.binding.root.setOnClickListener { onClicked(inv) }
    }

    override fun getItemCount(): Int = invoices.size

    private fun formatInvoiceDate(isoDate: String?): String {
        if (isoDate.isNullOrBlank()) return "—"
        return try {
            val parsed = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(isoDate.take(19))
            SimpleDateFormat("MMM d, yyyy", Locale.US).format(parsed!!)
        } catch (e: Exception) {
            isoDate
        }
    }
}
