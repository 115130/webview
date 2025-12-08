package com.webview.browser

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.WebView
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList

object TabManager {
    private val _tabs = CopyOnWriteArrayList<Tab>()
    val tabs: List<Tab> get() = _tabs

    private val _currentTab = MutableLiveData<Tab?>()
    val currentTab: LiveData<Tab?> get() = _currentTab

    // 用于通知列表更新的信号
    private val _tabsListChange = MutableLiveData<Long>()
    val tabsListChange: LiveData<Long> get() = _tabsListChange

    private val keepAliveHandler = Handler(Looper.getMainLooper())
    private const val KEEP_ALIVE_CHECK_INTERVAL = 10000L // 每10秒检查一次
    private const val INACTIVITY_THRESHOLD = 60000L // 1分钟无活动阈值

    private val keepAliveRunnable = object : Runnable {
        override fun run() {
            checkBackgroundTabsActivity()
            keepAliveHandler.postDelayed(this, KEEP_ALIVE_CHECK_INTERVAL)
        }
    }

    init {
        keepAliveHandler.postDelayed(keepAliveRunnable, KEEP_ALIVE_CHECK_INTERVAL)
    }

    fun addTab(tab: Tab, context: Context? = null) {
        _tabs.add(tab)
        switchToTab(tab, context)
        notifyListChanged()
        if (context != null) {
            saveTabs(context)
        }
    }

    fun createNewTab(context: Context, url: String? = null): Tab {
        val webView = WebView(context) // 注意：WebView的初始化配置需要在Activity中统一处理或这里处理
        // 具体的WebView Setting配置将在MainActivity中统一apply
        val newTab = Tab(webView = webView)
        if (url != null) {
            newTab.url = url
            webView.loadUrl(url)
        }
        addTab(newTab, context)
        return newTab
    }

    fun closeTab(tab: Tab, context: Context? = null) {
        val index = _tabs.indexOf(tab)
        if (index == -1) return

        // 销毁 WebView
        tab.webView.loadUrl("about:blank")
        tab.webView.clearHistory()
        tab.webView.removeAllViews()
        tab.webView.destroy()
        
        _tabs.remove(tab)

        // 如果关闭的是当前标签页，需要切换到其他标签页
        if (_currentTab.value == tab) {
            if (_tabs.isNotEmpty()) {
                // 优先切换到右侧，如果没有则切换到左侧
                val newIndex = if (index < _tabs.size) index else _tabs.size - 1
                switchToTab(_tabs[newIndex], context)
            } else {
                // 如果没有标签页了，可能需要新建一个或者清空当前
                _currentTab.value = null
            }
        }
        notifyListChanged()
        if (context != null) {
            saveTabs(context)
        }
    }

    fun switchToTab(tab: Tab, context: Context? = null) {
        if (_tabs.contains(tab)) {
            // 之前的标签页记录状态
            _currentTab.value?.let { _ ->
                // 截图保存预览（简单实现，实际可能需要更复杂的View绘制缓存）
                // prevTab.previewBitmap = ...
                // 离开前台，但不立即暂停，交给保活策略判断
            }

            // 激活新标签页
            tab.updateActivity() // 更新活跃时间
            if (tab.isBackgroundPaused) {
                tab.webView.onResume()
                tab.webView.resumeTimers()
                tab.isBackgroundPaused = false
            }
            
            _currentTab.value = tab
            
            if (context != null) {
                saveTabs(context)
            }
        }
    }

    fun getTabById(id: String): Tab? {
        return _tabs.find { it.id == id }
    }

    private fun notifyListChanged() {
        _tabsListChange.value = System.currentTimeMillis()
    }

    private fun checkBackgroundTabsActivity() {
        val currentTime = System.currentTimeMillis()
        val current = _currentTab.value

        for (tab in _tabs) {
            // 跳过当前正在显示的标签页
            if (tab == current) {
                tab.updateActivity() // 确保持续活跃
                continue 
            }

            // 检查后台标签页
            if (!tab.isBackgroundPaused) {
                // 如果标签页正在执行保活任务（如播放音频），则不休眠
                if (tab.isKeepAliveActive) {
                    tab.updateActivity() // 视为活跃
                    continue
                }

                if (currentTime - tab.lastActiveTime > INACTIVITY_THRESHOLD) {
                    // 超过1分钟无活动，执行休眠
                    pauseTab(tab)
                }
            }
        }
    }

    private fun pauseTab(tab: Tab) {
        if (!tab.isBackgroundPaused && !tab.isKeepAliveActive) {
            android.util.Log.d("TabManager", "Pausing background tab: ${tab.title} due to inactivity")
            tab.webView.onPause()
            // tab.webView.pauseTimers() // 暂停全局JS定时器，注意这会影响所有WebView，需要谨慎
            
            tab.isBackgroundPaused = true
        }
    }

    fun saveTabs(context: Context) {
        try {
            val prefs = context.getSharedPreferences("tab_prefs", Context.MODE_PRIVATE)
            val tabsJson = JSONArray()
            _tabs.forEach { tab ->
                val tabJson = JSONObject()
                tabJson.put("id", tab.id)
                tabJson.put("url", tab.url)
                tabJson.put("title", tab.title)
                tabsJson.put(tabJson)
            }
            prefs.edit()
                .putString("tabs_list", tabsJson.toString())
                .putString("current_tab_id", _currentTab.value?.id)
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun restoreTabs(context: Context, webViewFactory: (Context) -> WebView): Boolean {
        val prefs = context.getSharedPreferences("tab_prefs", Context.MODE_PRIVATE)
        val tabsString = prefs.getString("tabs_list", null) ?: return false
        val currentTabId = prefs.getString("current_tab_id", null)

        try {
            val tabsJson = JSONArray(tabsString)
            if (tabsJson.length() == 0) return false

            _tabs.clear()
            var tabToSwitch: Tab? = null

            for (i in 0 until tabsJson.length()) {
                val tabJson = tabsJson.getJSONObject(i)
                val url = tabJson.optString("url", "about:blank")
                val title = tabJson.optString("title", "New Tab")
                val id = tabJson.optString("id")

                val webView = webViewFactory(context)
                val tab = Tab(id = id, webView = webView, title = title, url = url)
                
                if (url.isNotEmpty()) {
                    webView.loadUrl(url)
                }
                
                _tabs.add(tab)

                if (id == currentTabId) {
                    tabToSwitch = tab
                }
            }

            if (tabToSwitch != null) {
                switchToTab(tabToSwitch, context)
            } else if (_tabs.isNotEmpty()) {
                switchToTab(_tabs.last(), context)
            }

            notifyListChanged()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}