package com.bassam.scanner.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bassam.scanner.R
import com.bassam.scanner.data.ScanEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EntryAdapter : RecyclerView.Adapter<EntryAdapter.VH>() {

    private val items = mutableListOf<ScanEntry>()
    private val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale("ar"))

    fun submit(newItems: List<ScanEntry>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.rowTitle)
        val sub: TextView = v.findViewById(R.id.rowSubtitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.row_entry, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val it = items[position]
        holder.title.text = "${it.name} | ${it.hsCode}"
        holder.sub.text = df.format(Date(it.createdAt))
    }

    override fun getItemCount(): Int = items.size
}
