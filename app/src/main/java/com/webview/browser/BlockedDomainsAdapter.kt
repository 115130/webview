package com.webview.browser

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BlockedDomainsAdapter(
    private val domains: MutableList<String>,
    private val onDeleteClick: (String) -> Unit
) : RecyclerView.Adapter<BlockedDomainsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDomain: TextView = view.findViewById(R.id.tvDomain)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_blocked_domain, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val domain = domains[position]
        holder.tvDomain.text = domain
        holder.btnDelete.setOnClickListener {
            onDeleteClick(domain)
        }
    }

    override fun getItemCount() = domains.size

    fun updateData(newDomains: List<String>) {
        domains.clear()
        domains.addAll(newDomains)
        notifyDataSetChanged()
    }
}