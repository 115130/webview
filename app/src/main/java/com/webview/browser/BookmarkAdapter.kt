package com.webview.browser

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.webview.browser.databinding.ItemBookmarkBinding

class BookmarkAdapter(
    private var bookmarks: List<Bookmark>,
    private val onItemClick: (Bookmark) -> Unit
) : RecyclerView.Adapter<BookmarkAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemBookmarkBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBookmarkBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val bookmark = bookmarks[position]
        holder.binding.tvName.text = bookmark.name
        holder.itemView.setOnClickListener {
            onItemClick(bookmark)
        }
    }

    override fun getItemCount(): Int = bookmarks.size

    fun updateData(newBookmarks: List<Bookmark>) {
        bookmarks = newBookmarks
        notifyDataSetChanged()
    }
}