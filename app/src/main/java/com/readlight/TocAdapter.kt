package com.readlight

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class TocItem(val title: String, val index: Int, val depth: Int)

class TocAdapter(
    private val chapters: List<TocItem>,
    private val getCurrentChapter: () -> Int,
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<TocAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tocTitle)
        val indicator: View = view.findViewById(R.id.tocIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_toc, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = chapters[position]
        val isCurrentChapter = item.index == getCurrentChapter()
        val density = holder.itemView.context.resources.displayMetrics.density
        val basePadding = 24
        val indentPadding = basePadding + (item.depth * 24)
        val indentPx = (indentPadding * density).toInt()
        val basePx = (basePadding * density).toInt()

        holder.title.setPadding(
            indentPx,
            holder.title.paddingTop,
            holder.title.paddingRight,
            holder.title.paddingBottom
        )

        val indicatorParams = holder.indicator.layoutParams as ViewGroup.MarginLayoutParams
        indicatorParams.marginStart = indentPx
        indicatorParams.marginEnd = basePx
        holder.indicator.layoutParams = indicatorParams

        holder.indicator.visibility = if (isCurrentChapter) View.VISIBLE else View.GONE
        holder.title.text = item.title
        holder.itemView.setOnClickListener { onClick(item.index) }
    }

    override fun getItemCount() = chapters.size
}
