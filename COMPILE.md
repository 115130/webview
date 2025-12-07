# 编译指南

## 快速开始

本项目已配置好所有编译环境，你可以非常方便地进行编译。

### 使用编译脚本（推荐）

我们提供了一个便捷的编译脚本 [`build.sh`](build.sh)，支持多种编译选项：

```bash
# 构建 Release 版本（默认，已签名）
./build.sh

# 或者明确指定
./build.sh release

# 构建 Debug 版本
./build.sh debug

# 构建并直接安装到设备（Debug）
./build.sh install

# 构建并直接安装到设备（Release）
./build.sh install-release

# 构建所有版本
./build.sh all

# 清理项目
./build.sh clean
```

### 使用 Gradle 命令

如果你更喜欢使用 Gradle 命令：

```bash
# 构建 Release APK（已签名）
./gradlew assembleRelease

# 构建 Debug APK
./gradlew assembleDebug

# 安装 Debug 版本到设备
./gradlew installDebug

# 安装 Release 版本到设备
./gradlew installRelease

# 清理项目
./gradlew clean
```

## 输出位置

编译完成后，APK 文件会生成在以下位置：

- **Debug 版本**: `app/build/outputs/apk/debug/app-debug.apk`
- **Release 版本**: `app/build/outputs/apk/release/app-release.apk`

## 签名配置

项目已经配置好签名：

- **密钥库文件**: [`app/webview-browser.jks`](app/webview-browser.jks)
- **密钥库密码**: `87dfx35m`
- **密钥别名**: `webview-browser`
- **密钥密码**: `87dfx35m`

签名配置存储在 [`keystore.properties`](keystore.properties) 文件中，Release 版本会自动使用此签名。

## SDK 配置

Android SDK 路径已配置在 [`local.properties`](local.properties) 文件中：

```properties
sdk.dir=/home/hui/Android/Sdk
```

## 系统要求

- **JDK**: 8 或更高版本
- **Android SDK**: API 24 (Android 7.0) 或更高
- **Gradle**: 8.2 或更高（项目自带 Gradle Wrapper）

## 首次编译

首次编译时，Gradle 会自动下载所需的依赖，可能需要几分钟时间。请确保网络连接正常。

## 故障排除

### 权限问题

如果遇到权限问题，请确保脚本有执行权限：

```bash
chmod +x build.sh
chmod +x gradlew
```

### Gradle 下载慢

如果 Gradle 下载依赖很慢，可以配置国内镜像。编辑 [`build.gradle`](build.gradle)，在 repositories 中添加阿里云镜像：

```gradle
repositories {
    maven { url 'https://maven.aliyun.com/repository/google' }
    maven { url 'https://maven.aliyun.com/repository/public' }
    google()
    mavenCentral()
}
```

### 签名错误

如果遇到签名相关错误，请检查：

1. [`keystore.properties`](keystore.properties) 文件是否存在
2. [`app/webview-browser.jks`](app/webview-browser.jks) 密钥库文件是否存在
3. 密码是否正确

## 版本说明

- **Debug 版本**: 
  - 包名: `com.webview.browser.debug`
  - 版本名后缀: `-debug`
  - 未混淆，便于调试

- **Release 版本**:
  - 包名: `com.webview.browser`
  - 已签名
  - 已混淆和资源压缩
  - 适合发布使用

## 快捷命令总结

```bash
# 最常用的命令
./build.sh                    # 构建 Release 版本
./build.sh install           # 构建并安装 Debug 版本
./build.sh install-release   # 构建并安装 Release 版本
```

编译成功后，你就可以在 Android 设备上安装和使用这个浏览器了！