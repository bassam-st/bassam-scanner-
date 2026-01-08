package com.bassam.scanner.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bassam.scanner.data.ScanItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScanAdapter : RecyclerView.Adapter<ScanAdapter.VH>() {

    private val items = mutableListOf<ScanItem>()
    private val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale("ar"))

    fun submit(list: List<ScanItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val t1: TextView = v.findViewById(android.R.id.text1)
        val t2: TextView = v.findViewById(android.R.id.text2)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val it = items[position]
        holder.t1.text = "${it.hsCode} — ${it.name}"
        holder.t2.text = df.format(Date(it.createdAt))
    }

    override fun getItemCount(): Int = items.size
}
