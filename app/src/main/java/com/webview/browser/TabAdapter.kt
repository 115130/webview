package com.webview.browser

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class TabAdapter(
    private var tabs: List<Tab>,
    private val onTabSelected: (Tab) -> Unit,
    private val onTabClosed: (Tab) -> Unit
) : RecyclerView.Adapter<TabAdapter.TabViewHolder>() {

    class TabViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardTab: MaterialCardView = itemView.findViewById(R.id.cardTab)
        val tvTitle: TextView = itemView.findViewById(R.id.tvTabTitle)
        val tvUrl: TextView = itemView.findViewById(R.id.tvTabUrl)
        val ivFavicon: ImageView = itemView.findViewById(R.id.ivFavicon)
        val btnClose: ImageButton = itemView.findViewById(R.id.btnCloseTab)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabAdapter.TabViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tab_preview, parent, false)
        return TabViewHolder(view)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        val tab = tabs[position]
        
        holder.tvTitle.text = if (tab.title.isEmpty()) "New Tab" else tab.title
        holder.tvUrl.text = if (tab.url.isEmpty()) "about:blank" else tab.url
        
        if (tab.favicon != null) {
            holder.ivFavicon.setImageBitmap(tab.favicon)
        } else {
            holder.ivFavicon.setImageResource(R.mipmap.ic_launcher)
        }

        // 高亮当前选中的标签页
        val currentTab = TabManager.currentTab.value
        if (tab == currentTab) {
            holder.cardTab.strokeWidth = 4 // dp conversion needed in real app
            holder.cardTab.strokeColor = holder.itemView.context.getColor(R.color.primary)
        } else {
            holder.cardTab.strokeWidth = 0
        }

        holder.itemView.setOnClickListener {
            onTabSelected(tab)
        }

        holder.btnClose.setOnClickListener {
            onTabClosed(tab)
        }
    }

    override fun getItemCount(): Int = tabs.size

    fun updateData(newTabs: List<Tab>) {
        tabs = newTabs
        notifyDataSetChanged()
    }
}