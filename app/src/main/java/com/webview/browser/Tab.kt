package com.webview.browser

import android.graphics.Bitmap
import android.webkit.WebView
import java.util.UUID

data class Tab(
    val id: String = UUID.randomUUID().toString(),
    var webView: WebView,
    var title: String = "New Tab",
    var url: String = "",
    var favicon: Bitmap? = null,
    var previewBitmap: Bitmap? = null, // 标签页预览图
    var lastActiveTime: Long = System.currentTimeMillis(), // 用于保活策略
    var isBackgroundPaused: Boolean = false, // 标记是否已被保活策略暂停
    var isKeepAliveActive: Boolean = false // 标记是否正在执行保活任务（如播放音频、下载等）
) {
    fun updateActivity() {
        lastActiveTime = System.currentTimeMillis()
        if (isBackgroundPaused) {
            isBackgroundPaused = false
            webView.onResume() // 恢复活动
            webView.resumeTimers()
        }
    }
}