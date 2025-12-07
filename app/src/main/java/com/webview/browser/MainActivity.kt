package com.webview.browser

import android.annotation.SuppressLint
import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.widget.PopupWindow
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.webview.browser.databinding.ActivityMainBinding
import com.webview.browser.databinding.PopupBookmarksBinding
import kotlin.math.abs

class MainActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {
    
    private lateinit var binding: ActivityMainBinding
    private var isAddressBarVisible = true
    private lateinit var sharedPreferences: SharedPreferences

    private var uploadMessage: ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            var results: Array<Uri>? = null
            if (data != null) {
                val dataString = data.dataString
                val clipData = data.clipData
                if (clipData != null) {
                    results = Array(clipData.itemCount) { i ->
                        clipData.getItemAt(i).uri
                    }
                } else if (dataString != null) {
                    results = arrayOf(Uri.parse(dataString))
                }
            }
            uploadMessage?.onReceiveValue(results)
        } else {
            uploadMessage?.onReceiveValue(null)
        }
        uploadMessage = null
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                uploadMessage?.onReceiveValue(arrayOf(uri))
            } else {
                uploadMessage?.onReceiveValue(null)
            }
        } else {
            uploadMessage?.onReceiveValue(null)
        }
        uploadMessage = null
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(this, "需要相机权限才能拍照", Toast.LENGTH_SHORT).show()
            uploadMessage?.onReceiveValue(null)
            uploadMessage = null
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        
        setupWebView()
        setupToolbar()
        loadHomePage()
        applySettings()
        createNotificationChannel()

        // 强制使用 Edge-to-Edge 布局，以便手动处理 Insets (解决键盘遮挡问题)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // 全局设置 DisplayCutoutMode，解决黑边问题
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            val isStatusBarHidden = prefs.getBoolean("hide_status_bar", false)
            val isAppBarVisible = binding.appBarLayout.visibility == View.VISIBLE

            // 1. 底部 Padding (键盘/导航栏) - 始终应用到 Root
            view.setPadding(insets.left, 0, insets.right, insets.bottom)

            // 2. 顶部 Padding 策略
            val topPadding = if (isStatusBarHidden) 0 else insets.top

            if (isAppBarVisible) {
                // 计算 ActionBar 高度
                val typedValue = TypedValue()
                var actionBarHeight = 0
                if (theme.resolveAttribute(android.R.attr.actionBarSize, typedValue, true)) {
                    actionBarHeight = TypedValue.complexToDimensionPixelSize(typedValue.data, resources.displayMetrics)
                }

                // 地址栏显示：Padding 应用到 AppBar
                binding.appBarLayout.setPadding(0, topPadding, 0, 0)
                
                // WebView 需要避开 (状态栏 + 地址栏) 的高度
                // 因为移除了 layout_behavior，WebView 默认在顶部，会被 AppBar 遮挡
                binding.webView.setPadding(0, topPadding + actionBarHeight, 0, 0)
            } else {
                // 地址栏隐藏：AppBar Padding 归零
                binding.appBarLayout.setPadding(0, 0, 0, 0)
                
                // WebView 顶部 Padding 归零，让内容延伸到状态栏区域
                // 解决"隐藏后白条"问题
                binding.webView.setPadding(0, 0, 0, 0)
            }

            WindowInsetsCompat.CONSUMED
        }
    }
    
    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun setupWebView() {
        binding.webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                loadWithOverviewMode = true
                useWideViewPort = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                mediaPlaybackRequiresUserGesture = false // 允许后台播放音频
            }
            
            addJavascriptInterface(WebAppInterface(this@MainActivity), "Android")
            
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    binding.progressBar.visibility = View.VISIBLE
                    binding.progressBar.progress = 0
                    binding.etUrl.setText(url)
                    injectNotificationPolyfill(view)
                }
                
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    binding.progressBar.visibility = View.GONE
                    binding.etUrl.setText(url)
                    injectNotificationPolyfill(view)
                }
                
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    return false
                }
            }
            
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    binding.progressBar.progress = newProgress
                    if (newProgress == 100) {
                        binding.progressBar.visibility = View.GONE
                    }
                }
                
                override fun onReceivedTitle(view: WebView?, title: String?) {
                    super.onReceivedTitle(view, title)
                    // 可以在这里更新标题
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    if (uploadMessage != null) {
                        uploadMessage?.onReceiveValue(null)
                        uploadMessage = null
                    }
                    uploadMessage = filePathCallback

                    if (fileChooserParams?.isCaptureEnabled == true) {
                        checkCameraPermissionAndLaunch()
                    } else {
                        launchFileChooser(fileChooserParams)
                    }
                    return true
                }
            }
            
        }
    }
    
    private fun setupToolbar() {
        binding.btnBack.setOnClickListener {
            if (binding.webView.canGoBack()) {
                binding.webView.goBack()
            }
        }
        
        binding.btnForward.setOnClickListener {
            if (binding.webView.canGoForward()) {
                binding.webView.goForward()
            }
        }
        
        binding.btnRefresh.setOnClickListener {
            binding.webView.reload()
        }
        
        binding.btnMenu.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnBookmark.setOnClickListener {
            showBookmarkPopup(it)
        }
        
        binding.etUrl.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event?.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER)) {
                loadUrl(binding.etUrl.text.toString())
                true
            } else {
                false
            }
        }
        
        // 设置可拖动悬浮按钮
        setupDraggableFab()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDraggableFab() {
        var dX = 0f
        var dY = 0f
        var startX = 0f
        var startY = 0f
        var startTime = 0L

        binding.fabShowAddressBar.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = view.x - event.rawX
                    dY = view.y - event.rawY
                    startX = view.x
                    startY = view.y
                    startTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    view.animate()
                        .x(event.rawX + dX)
                        .y(event.rawY + dY)
                        .setDuration(0)
                        .start()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val isClick = (System.currentTimeMillis() - startTime) < 200 &&
                            abs(view.x - startX) < 10 && abs(view.y - startY) < 10

                    if (isClick) {
                        view.performClick()
                        // 点击悬浮按钮显示地址栏，并更新设置
                        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
                        prefs.edit().putBoolean("hide_address_bar", false).apply()
                        showAddressBar()
                    } else {
                        // 吸附到边缘
                        val screenWidth = resources.displayMetrics.widthPixels
                        val viewWidth = view.width
                        // 简单的吸附逻辑：如果在屏幕左半边就吸附到左边，右半边吸附到右边
                        // 预留一些边距 (32px)
                        val targetX = if (view.x + viewWidth / 2 > screenWidth / 2) {
                            screenWidth - viewWidth - 32f
                        } else {
                            32f
                        }
                        
                        // 限制 Y 轴范围，防止拖出屏幕
                        val screenHeight = resources.displayMetrics.heightPixels
                        val statusBarHeight = 100 // 估算值
                        val navBarHeight = 150 // 估算值
                        var targetY = view.y
                        if (targetY < statusBarHeight) targetY = statusBarHeight.toFloat()
                        if (targetY > screenHeight - navBarHeight - view.height) targetY = (screenHeight - navBarHeight - view.height).toFloat()

                        view.animate()
                            .x(targetX)
                            .y(targetY)
                            .setDuration(300)
                            .start()
                    }
                    true
                }
                else -> false
            }
        }
    }
    
    private fun loadUrl(url: String) {
        var finalUrl = url.trim()
        
        if (finalUrl.isEmpty()) {
            return
        }
        
        // 如果不是URL格式，使用Google搜索
        if (!finalUrl.startsWith("http://") && !finalUrl.startsWith("https://")) {
            if (finalUrl.contains(".") && !finalUrl.contains(" ")) {
                finalUrl = "https://$finalUrl"
            } else {
                finalUrl = "https://www.google.com/search?q=${finalUrl}"
            }
        }
        
        binding.webView.loadUrl(finalUrl)
    }
    
    private fun showBookmarkPopup(anchorView: View) {
        val popupBinding = PopupBookmarksBinding.inflate(layoutInflater)
        val popupWindow = PopupWindow(
            popupBinding.root,
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 300f, resources.displayMetrics).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            true
        )
        popupWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popupWindow.elevation = 10f

        // 设置 RecyclerView
        popupBinding.rvBookmarks.layoutManager = LinearLayoutManager(this)
        val bookmarks = BookmarkManager.getBookmarks(this)
        val adapter = BookmarkAdapter(bookmarks) { bookmark ->
            loadUrl(bookmark.url)
            popupWindow.dismiss()
        }
        popupBinding.rvBookmarks.adapter = adapter

        // 添加按钮点击事件
        popupBinding.btnAddBookmark.setOnClickListener {
            popupWindow.dismiss()
            showAddBookmarkDialog()
        }

        // 计算显示位置
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val position = prefs.getString("toolbar_position", "top")

        if (position == "bottom") {
            // 地址栏在底部，弹窗显示在上方
            // 需要测量弹窗高度以准确显示在上方，或者使用 Gravity.BOTTOM
            popupBinding.root.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            val popupHeight = popupBinding.root.measuredHeight
            
            // 获取 anchorView 在屏幕上的位置
            val location = IntArray(2)
            anchorView.getLocationOnScreen(location)
            
            // 显示在 anchorView 上方
            // 注意：这里简单估算高度，如果列表很长，高度会受限于 maxHeight
            // 更精确的做法是先显示再 update，或者限制最大高度
            popupWindow.showAtLocation(anchorView, Gravity.NO_GRAVITY, location[0] - popupWindow.width + anchorView.width, location[1] - 400) // 400 is arbitrary offset, better logic needed
            
            // 修正：使用 showAsDropDown 的 yoff 参数
            // yoff 为负数表示向上偏移。需要偏移 (popupHeight + anchorHeight)
            // 但 popupHeight 在显示前可能不准确。
            // 简单方案：使用 Gravity.BOTTOM | Gravity.END
             popupWindow.showAtLocation(binding.root, Gravity.BOTTOM or Gravity.END, 16, binding.appBarLayout.height + 16)

        } else {
            // 地址栏在顶部，弹窗显示在下方
            popupWindow.showAsDropDown(anchorView, 0, 0, Gravity.END)
        }
    }

    private fun showAddBookmarkDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_bookmark, null)
        val etName = dialogView.findViewById<TextInputEditText>(R.id.etBookmarkName)
        val etUrl = dialogView.findViewById<TextInputEditText>(R.id.etBookmarkUrl)

        // 预填充当前网页信息
        etName.setText(binding.webView.title)
        etUrl.setText(binding.webView.url)

        MaterialAlertDialogBuilder(this)
            .setTitle("添加书签")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val name = etName.text.toString()
                val url = etUrl.text.toString()
                if (name.isNotEmpty() && url.isNotEmpty()) {
                    val bookmark = Bookmark(name, url, System.currentTimeMillis())
                    BookmarkManager.addBookmark(this, bookmark)
                    Toast.makeText(this, "书签已保存", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun checkCameraPermissionAndLaunch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        val intent = Intent(this, CameraActivity::class.java)
        cameraLauncher.launch(intent)
    }

    private fun launchFileChooser(fileChooserParams: WebChromeClient.FileChooserParams?) {
        val intent = fileChooserParams?.createIntent()
        try {
            fileChooserLauncher.launch(intent)
        } catch (e: Exception) {
            uploadMessage?.onReceiveValue(null)
            uploadMessage = null
            Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadHomePage() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val homePage = prefs.getString("home_page", "https://www.google.com") ?: "https://www.google.com"
        binding.webView.loadUrl(homePage)
    }
    
    private fun applySettings() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        
        // 应用JavaScript设置
        binding.webView.settings.javaScriptEnabled = prefs.getBoolean("javascript", true)
        
        // 应用User Agent设置
        applyUserAgent()
        
        // 应用地址栏隐藏设置
        if (prefs.getBoolean("hide_address_bar", false)) {
            hideAddressBar()
        } else {
            showAddressBar()
        }

        // 应用状态栏隐藏设置
        if (prefs.getBoolean("hide_status_bar", false)) {
            hideStatusBar()
        } else {
            showStatusBar()
        }

        // 应用地址栏位置设置
        val position = prefs.getString("toolbar_position", "top")
        applyToolbarPosition(position)

        // 应用后台保活设置
        if (prefs.getBoolean("keep_alive", false)) {
            enableKeepAlive()
        } else {
            disableKeepAlive()
        }
    }
    
    private fun enableKeepAlive() {
        try {
            // 启动前台服务
            val intent = Intent(this, KeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "启动保活服务失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
        
        // 请求忽略电池优化
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intentBattery = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intentBattery.data = Uri.parse("package:$packageName")
                    startActivity(intentBattery)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun disableKeepAlive() {
        val intent = Intent(this, KeepAliveService::class.java)
        stopService(intent)
    }

    private fun injectNotificationPolyfill(view: WebView?) {
        view?.evaluateJavascript(
            """
            if (!window.Notification) {
                window.Notification = function(title, options) {
                    Android.showNotification(title, options ? options.body : '');
                };
                window.Notification.permission = 'granted';
                window.Notification.requestPermission = function(callback) {
                    if(callback) callback('granted');
                    return Promise.resolve('granted');
                };
            } else {
                // 覆盖原生Notification以确保通知发送到Android状态栏
                const OriginalNotification = window.Notification;
                window.Notification = function(title, options) {
                    Android.showNotification(title, options ? options.body : '');
                    return new OriginalNotification(title, options);
                };
                Object.assign(window.Notification, OriginalNotification);
                window.Notification.permission = 'granted';
                 window.Notification.requestPermission = function(callback) {
                    if(callback) callback('granted');
                    return Promise.resolve('granted');
                };
            }
            """.trimIndent(), null
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "WebNotificationChannel",
                "Web Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    inner class WebAppInterface(private val mContext: Context) {
        @JavascriptInterface
        fun showNotification(title: String, body: String) {
            val builder = NotificationCompat.Builder(mContext, "WebNotificationChannel")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)

            val manager = mContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }

    private fun applyUserAgent() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val userAgentType = prefs.getString("user_agent", "default") ?: "default"
        
        val userAgent = when (userAgentType) {
            "chrome_android" -> "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            "chrome_desktop" -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            "firefox_android" -> "Mozilla/5.0 (Android 13; Mobile; rv:120.0) Gecko/120.0 Firefox/120.0"
            "safari_ios" -> "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
            "safari_mac" -> "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15"
            "custom" -> prefs.getString("custom_user_agent", "") ?: ""
            else -> null
        }
        
        if (userAgent != null && userAgent.isNotEmpty()) {
            binding.webView.settings.userAgentString = userAgent
        }
        
        // 桌面模式
        if (prefs.getBoolean("desktop_mode", false)) {
            binding.webView.settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            binding.webView.settings.useWideViewPort = true
            binding.webView.settings.loadWithOverviewMode = true
        }
    }
    
    private fun hideStatusBar() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())
        
        // 设置状态栏颜色为透明
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        ViewCompat.requestApplyInsets(binding.root)
    }
    
    private fun showStatusBar() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.show(WindowInsetsCompat.Type.statusBars())
        
        // 恢复状态栏颜色
        window.statusBarColor = resources.getColor(R.color.primary_dark, theme)
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun applyToolbarPosition(position: String?) {
        val params = binding.appBarLayout.layoutParams as CoordinatorLayout.LayoutParams
        if (position == "bottom") {
            params.gravity = Gravity.BOTTOM
        } else {
            params.gravity = Gravity.TOP
        }
        binding.appBarLayout.layoutParams = params
    }
    
    private fun hideAddressBar() {
        binding.appBarLayout.visibility = View.GONE
        binding.fabShowAddressBar.visibility = View.VISIBLE
        isAddressBarVisible = false
        ViewCompat.requestApplyInsets(binding.root)
    }
    
    private fun showAddressBar() {
        binding.appBarLayout.visibility = View.VISIBLE
        binding.fabShowAddressBar.visibility = View.GONE
        isAddressBarVisible = true
        ViewCompat.requestApplyInsets(binding.root)
    }
    
    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
        applySettings()
    }

    override fun onPause() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (!prefs.getBoolean("keep_alive", false)) {
            binding.webView.onPause()
        }
        super.onPause()
    }
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && binding.webView.canGoBack()) {
            binding.webView.goBack()
        }
        return super.onKeyDown(keyCode, event)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        binding.webView.destroy()
    }
    
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            "hide_address_bar" -> {
                if (sharedPreferences?.getBoolean("hide_address_bar", false) == true) {
                    hideAddressBar()
                } else {
                    showAddressBar()
                }
            }
            "hide_status_bar" -> {
                if (sharedPreferences?.getBoolean("hide_status_bar", false) == true) {
                    hideStatusBar()
                } else {
                    showStatusBar()
                }
            }
            "toolbar_position" -> {
                val position = sharedPreferences?.getString("toolbar_position", "top")
                applyToolbarPosition(position)
            }
            "keep_alive" -> {
                if (sharedPreferences?.getBoolean("keep_alive", false) == true) {
                    enableKeepAlive()
                } else {
                    disableKeepAlive()
                }
            }
            "user_agent", "custom_user_agent", "desktop_mode" -> {
                applyUserAgent()
                binding.webView.reload()
            }
            "javascript" -> {
                binding.webView.settings.javaScriptEnabled =
                    sharedPreferences?.getBoolean("javascript", true) ?: true
                binding.webView.reload()
            }
        }
    }
}