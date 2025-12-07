# WebView 浏览器开发文档

本文档详细说明了 WebView 浏览器项目的架构、核心功能实现细节以及主要类和方法的用途。

## 1. 项目概述

本项目是一个基于 Android WebView 的轻量级浏览器，具有以下核心特性：
*   **沉浸式体验**：支持 Edge-to-Edge 布局，可隐藏状态栏和地址栏。
*   **后台保活**：通过前台服务和 WakeLock 防止应用在后台被系统杀掉，支持 AI 网页持续运行。
*   **网页通知桥接**：将网页的 HTML5 Notification 转发为 Android 系统通知。
*   **高度可配置**：支持自定义 User Agent、JavaScript 开关、地址栏位置等。
*   **书签管理**：支持添加和管理网页书签。
*   **原生拍照**：集成 CameraX 实现应用内拍照上传。

## 2. 核心类说明

### 2.1 MainActivity.kt

应用的主入口，负责 UI 逻辑、WebView 管理和设置应用。

#### 核心方法

*   **`onCreate(savedInstanceState: Bundle?)`**
    *   **作用**：Activity 初始化入口。
    *   **逻辑**：
        *   初始化 ViewBinding。
        *   注册 `SharedPreferences` 监听器。
        *   **Edge-to-Edge 适配**：全局设置 `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES` 以适配挖孔屏。
        *   **窗口适配监听器 (`OnApplyWindowInsetsListener`)**：这是解决 UI 遮挡问题的核心。它监听系统窗口 Insets（状态栏、键盘等），并根据当前设置（是否隐藏状态栏/地址栏）动态计算并应用 Padding。
            *   处理底部键盘遮挡：始终为根布局应用底部 Padding。
            *   处理顶部布局：手动计算 ActionBar 高度，根据状态栏可见性动态调整 WebView 的顶部 Padding，确保内容不被遮挡且无多余白边。

*   **`setupWebView()`**
    *   **作用**：初始化 WebView 及其配置。
    *   **逻辑**：
        *   启用 JavaScript、DOM Storage。
        *   设置 `mediaPlaybackRequiresUserGesture = false` 允许后台自动播放音频。
        *   **注入 JS 接口**：`addJavascriptInterface(WebAppInterface(...), "Android")`。
        *   设置 `WebViewClient` 和 `WebChromeClient`。
        *   **文件选择处理**：重写 `onShowFileChooser`，根据 `isCaptureEnabled` 智能判断是启动相机还是文件选择器。

*   **`setupDraggableFab()`**
    *   **作用**：初始化可拖动的悬浮按钮。
    *   **逻辑**：
        *   使用 `setOnTouchListener` 监听触摸事件。
        *   处理拖动位移。
        *   区分点击和拖动操作。
        *   实现松手后的自动边缘吸附动画。

*   **`injectNotificationPolyfill(view: WebView?)`**
    *   **作用**：在网页加载开始和结束时注入 JavaScript 代码。
    *   **逻辑**：重写网页的 `window.Notification` 对象，将其请求转发给 Android 原生接口 `Android.showNotification`，从而实现网页通知在系统状态栏显示。

*   **`applySettings()`**
    *   **作用**：读取用户偏好设置并应用到当前环境。
    *   **逻辑**：根据 `SharedPreferences` 的值，调用 `applyUserAgent`、`hideAddressBar`、`enableKeepAlive` 等具体方法。

*   **`enableKeepAlive()` / `disableKeepAlive()`**
    *   **作用**：开启或关闭后台保活模式。
    *   **逻辑**：
        *   启动/停止 `KeepAliveService` 前台服务。
        *   请求 `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 权限（忽略电池优化），防止系统在后台休眠应用。

*   **`hideStatusBar()` / `showStatusBar()`**
    *   **作用**：控制系统状态栏的显示与隐藏。
    *   **逻辑**：
        *   使用 `WindowInsetsController` 隐藏/显示状态栏。
        *   设置状态栏颜色（隐藏时透明，显示时跟随主题）。
        *   调用 `ViewCompat.requestApplyInsets` 触发布局更新。

*   **`hideAddressBar()` / `showAddressBar()`**
    *   **作用**：控制顶部地址栏的显示与隐藏。
    *   **逻辑**：
        *   切换 `AppBarLayout` 的可见性 (`GONE` / `VISIBLE`)。
        *   调用 `ViewCompat.requestApplyInsets` 强制触发根布局的 Insets 监听器，以便重新计算 WebView 的位置。

#### 内部类

*   **`inner class WebAppInterface`**
    *   **作用**：供 JavaScript 调用的原生接口。
    *   **方法**：
        *   `@JavascriptInterface showNotification(title: String, body: String)`: 接收网页通知内容，并构建发送 Android Notification。

### 2.2 KeepAliveService.kt

用于维持应用在后台运行的前台服务。

#### 核心方法

*   **`onStartCommand(...)`**
    *   **作用**：服务启动入口。
    *   **逻辑**：
        *   创建并显示一个持续的 Notification（"正在后台运行..."），将服务提升为前台服务 (`startForeground`)。
        *   适配 Android 14，指定 `FOREGROUND_SERVICE_TYPE_DATA_SYNC` 类型。
        *   调用 `acquireWakeLock()`。

*   **`acquireWakeLock()`**
    *   **作用**：申请电源锁 (WakeLock)。
    *   **逻辑**：获取 `PARTIAL_WAKE_LOCK`，确保 CPU 在屏幕关闭后继续运行，防止网络连接断开或 JS 暂停。

### 2.3 SettingsActivity.kt

基于 `PreferenceFragmentCompat` 的设置页面，负责加载 `xml/preferences.xml` 并处理用户交互。

### 2.4 BookmarkManager.kt

负责书签数据的持久化存储和管理。

*   **功能**：
    *   使用 `SharedPreferences` 存储书签列表（JSON 格式）。
    *   提供 `addBookmark`、`getBookmarks`、`removeBookmark` 等方法。
    *   自动按时间戳排序。

### 2.5 CameraActivity.kt

基于 CameraX 实现的自定义相机界面。

*   **功能**：
    *   集成 CameraX 预览 (`Preview`) 和拍照 (`ImageCapture`) 用例。
    *   提供简单的拍照 UI（快门按钮）。
    *   拍照后将图片保存到应用缓存目录，并通过 `setResult` 返回 URI。

## 3. 关键技术实现细节

### 3.1 解决键盘遮挡与全屏白条问题

为了同时解决"键盘遮挡输入框"和"隐藏地址栏后顶部留白"的问题，本项目放弃了传统的 `fitsSystemWindows="true"` 和 `CoordinatorLayout` 的自动行为，转为**完全手动控制 Insets**。

1.  **全局 Edge-to-Edge**：在 `onCreate` 中设置 `setDecorFitsSystemWindows(false)`，让内容延伸到屏幕边缘。
2.  **手动 Padding 计算** (`OnApplyWindowInsetsListener`)：
    *   **底部**：直接将 `ime` (输入法) 和 `systemBars` (导航栏) 的高度应用为 Root View 的底部 Padding。这样键盘弹出时，整个布局会被顶起。
    *   **顶部**：
        *   若 **地址栏显示**：计算 `ActionBar` 高度 + `StatusBar` 高度，设为 WebView 的 `paddingTop`。
        *   若 **地址栏隐藏**：强制 WebView 的 `paddingTop` 为 0。这使得 WebView 内容能直接延伸到状态栏区域（沉浸式），消除了白条。

### 3.2 网页通知实现原理

Android WebView 默认不支持 HTML5 Notification API。本项目通过以下步骤实现：
1.  **Native 端**：定义 `WebAppInterface`，提供 `showNotification` 方法发送系统通知。
2.  **JS 注入**：在 `onPageStarted` 中注入 JS 代码，劫持 `window.Notification` 构造函数。
3.  **桥接**：当网页调用 `new Notification()` 时，实际上执行的是 Native 的 `showNotification`，从而在手机状态栏显示通知。

## 4. 权限说明

*   `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_DATA_SYNC`: 用于后台保活服务。
*   `POST_NOTIFICATIONS`: 用于发送状态栏通知（Android 13+）。
*   `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: 用于申请白名单，防止电池优化杀后台。
*   `WAKE_LOCK`: 用于保持 CPU 唤醒。
*   `INTERNET`: 网络访问。
*   `CAMERA`: 用于网页内拍照上传。

## 5. 新增功能实现细节 (v1.1)

### 5.1 书签功能

*   **入口**：地址栏右侧的书签按钮。
*   **交互**：
    *   点击按钮弹出悬浮列表 (`PopupWindow`)。
    *   列表位置根据地址栏位置（顶部/底部）自动调整，避免遮挡。
    *   支持添加当前网页为书签，自动预填充标题和 URL。
    *   数据存储在本地 `SharedPreferences` 中。

### 5.2 可拖动悬浮按钮 (FAB)

为了解决悬浮按钮遮挡网页内容的问题，实现了可拖动逻辑：
*   **实现**：在 `MainActivity` 中使用 `setOnTouchListener` 替代 `setOnClickListener`。
*   **逻辑**：
    *   监听 `ACTION_MOVE` 更新按钮位置，实现全屏拖动。
    *   监听 `ACTION_UP` 判断是点击还是拖动（基于位移和时间阈值）。
    *   实现**自动吸附**：松手后按钮自动动画吸附到屏幕左侧或右侧边缘，保持界面整洁。

### 5.3 文件上传与相机集成

解决了 WebView 中 `<input type="file">` 无法使用的问题，并优化了拍照体验。

1.  **智能分发 (`onShowFileChooser`)**：
    *   重写 `WebChromeClient.onShowFileChooser`。
    *   检查 `fileChooserParams.isCaptureEnabled`。
    *   **直接拍照**：如果网页请求捕获（如点击相机图标），直接启动 `CameraActivity`。
    *   **文件选择**：如果网页请求文件（如点击附件图标），直接启动系统文件选择器。
2.  **CameraX 集成**：
    *   使用 Jetpack CameraX 库替代系统相机应用，提供更统一的拍照体验。
    *   自定义 `CameraActivity`，包含预览和拍照功能。