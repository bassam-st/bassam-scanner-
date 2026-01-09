package com.bassam.scanner.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bassam.scanner.data.ScanEntity
import com.bassam.scanner.databinding.ItemScanBinding

class ScanAdapter : RecyclerView.Adapter<ScanAdapter.Holder>() {

    private val items = mutableListOf<ScanEntity>()

    fun submitList(list: List<ScanEntity>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class Holder(val binding: ItemScanBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val b = ItemScanBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(b)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.binding.txtItem.text = item.itemName
        holder.binding.txtHs.text = "HS: ${item.hsCode}"
    }

    override fun getItemCount(): Int = items.size
}
