package com.webview.browser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object BookmarkManager {
    private const val PREF_NAME = "bookmarks_pref"
    private const val KEY_BOOKMARKS = "bookmark_list"

    fun addBookmark(context: Context, bookmark: Bookmark) {
        val bookmarks = getBookmarks(context).toMutableList()
        bookmarks.add(bookmark)
        saveBookmarks(context, bookmarks)
    }

    fun getBookmarks(context: Context): List<Bookmark> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_BOOKMARKS, "[]")
        val bookmarks = mutableListOf<Bookmark>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                bookmarks.add(Bookmark(
                    name = obj.getString("name"),
                    url = obj.getString("url"),
                    timestamp = obj.getLong("timestamp")
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // 按照保存的时间排序 (默认升序，即旧的在前，新的在后)
        return bookmarks.sortedBy { it.timestamp }
    }
    
    fun removeBookmark(context: Context, bookmark: Bookmark) {
        val bookmarks = getBookmarks(context).toMutableList()
        bookmarks.removeAll { it.timestamp == bookmark.timestamp && it.url == bookmark.url }
        saveBookmarks(context, bookmarks)
    }

    private fun saveBookmarks(context: Context, bookmarks: List<Bookmark>) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        bookmarks.forEach {
            val obj = JSONObject()
            obj.put("name", it.name)
            obj.put("url", it.url)
            obj.put("timestamp", it.timestamp)
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_BOOKMARKS, jsonArray.toString()).apply()
    }
}