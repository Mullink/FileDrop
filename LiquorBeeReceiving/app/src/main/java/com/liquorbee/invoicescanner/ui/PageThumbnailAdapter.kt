package com.liquorbee.invoicescanner.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.liquorbee.invoicescanner.databinding.ItemPageThumbnailBinding

class PageThumbnailAdapter(
    private val pages: List<ScanPage>,
    private val onDrawClicked: (Int) -> Unit,
    private val onRemoveClicked: (Int) -> Unit
) : RecyclerView.Adapter<PageThumbnailAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemPageThumbnailBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPageThumbnailBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val page = pages[position]
        holder.binding.imageThumbnail.setImageBitmap(page.bitmap)
        holder.binding.textPageNumber.text = "Page ${position + 1}"
        holder.binding.textAnnotatedBadge.visibility = if (page.hasAnnotations) View.VISIBLE else View.GONE
        holder.binding.buttonDraw.setOnClickListener { onDrawClicked(position) }
        holder.binding.buttonRemove.setOnClickListener { onRemoveClicked(position) }
    }

    override fun getItemCount(): Int = pages.size
}
