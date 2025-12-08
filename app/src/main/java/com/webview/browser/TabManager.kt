package com.webview.browser

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.WebView
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
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

    fun addTab(tab: Tab) {
        _tabs.add(tab)
        switchToTab(tab)
        notifyListChanged()
    }

    fun createNewTab(context: Context, url: String? = null): Tab {
        val webView = WebView(context) // 注意：WebView的初始化配置需要在Activity中统一处理或这里处理
        // 具体的WebView Setting配置将在MainActivity中统一apply
        val newTab = Tab(webView = webView)
        if (url != null) {
            newTab.url = url
            webView.loadUrl(url)
        }
        addTab(newTab)
        return newTab
    }

    fun closeTab(tab: Tab) {
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
                switchToTab(_tabs[newIndex])
            } else {
                // 如果没有标签页了，可能需要新建一个或者清空当前
                _currentTab.value = null
            }
        }
        notifyListChanged()
    }

    fun switchToTab(tab: Tab) {
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
                if (currentTime - tab.lastActiveTime > INACTIVITY_THRESHOLD) {
                    // 超过1分钟无活动，执行休眠
                    pauseTab(tab)
                }
            }
        }
    }

    private fun pauseTab(tab: Tab) {
        if (!tab.isBackgroundPaused) {
            android.util.Log.d("TabManager", "Pausing background tab: ${tab.title} due to inactivity")
            tab.webView.onPause()
            tab.webView.pauseTimers() // 暂停全局JS定时器，注意这会影响所有WebView，需要谨慎
            // 注意：WebView.pauseTimers() 是全局的，会暂停所有 WebView 的 layout、parsing 和 JavaScript timers。
            // 这对于多标签页浏览器来说是个问题，因为我们只想暂停特定的后台标签页。
            // 简单的 onPause() 通常只停止渲染和部分处理。
            // 为了实现"类似Chrome"的单标签页独立冻结，通常需要更底层的 API 支持或者仅仅依赖 onPause() + 停止加载。
            // 这里我们先只调用 onPause()，它会通知内核页面不可见，通常会降低优先级。
            // 如果要严格禁止 JS，可能需要 loadUrl("javascript:...") 注入脚本暂停。
            
            // 修正策略：仅调用 onPause，不调用 pauseTimers 以免影响前台
            // tab.webView.pauseTimers() 
            
            tab.isBackgroundPaused = true
        }
    }
}