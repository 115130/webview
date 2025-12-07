#!/bin/bash

# WebView 浏览器编译脚本
# 使用方法: ./build.sh [debug|release|install|clean]

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}================================${NC}"
echo -e "${GREEN}WebView 浏览器编译脚本${NC}"
echo -e "${GREEN}================================${NC}"

# 检查是否安装了 Gradle
if ! command -v ./gradlew &> /dev/null; then
    echo -e "${RED}错误: gradlew 未找到${NC}"
    exit 1
fi

# 赋予 gradlew 执行权限
chmod +x ./gradlew

# 默认操作
ACTION=${1:-release}

case $ACTION in
    debug)
        echo -e "${YELLOW}正在构建 Debug 版本...${NC}"
        ./gradlew assembleDebug
        echo -e "${GREEN}✓ Debug APK 构建成功!${NC}"
        echo -e "${GREEN}输出位置: app/build/outputs/apk/debug/app-debug.apk${NC}"
        ;;
    
    release)
        echo -e "${YELLOW}正在构建 Release 版本...${NC}"
        ./gradlew assembleRelease
        echo -e "${GREEN}✓ Release APK 构建成功!${NC}"
        echo -e "${GREEN}输出位置: app/build/outputs/apk/release/app-release.apk${NC}"
        ;;
    
    install)
        echo -e "${YELLOW}正在构建并安装 Debug 版本到设备...${NC}"
        ./gradlew installDebug
        echo -e "${GREEN}✓ 应用已安装到设备!${NC}"
        ;;
    
    install-release)
        echo -e "${YELLOW}正在构建并安装 Release 版本到设备...${NC}"
        ./gradlew installRelease
        echo -e "${GREEN}✓ 应用已安装到设备!${NC}"
        ;;
    
    clean)
        echo -e "${YELLOW}正在清理项目...${NC}"
        ./gradlew clean
        echo -e "${GREEN}✓ 清理完成!${NC}"
        ;;
    
    all)
        echo -e "${YELLOW}正在构建所有版本...${NC}"
        ./gradlew assembleDebug assembleRelease
        echo -e "${GREEN}✓ 所有版本构建成功!${NC}"
        echo -e "${GREEN}Debug APK: app/build/outputs/apk/debug/app-debug.apk${NC}"
        echo -e "${GREEN}Release APK: app/build/outputs/apk/release/app-release.apk${NC}"
        ;;
    
    *)
        echo -e "${RED}未知操作: $ACTION${NC}"
        echo ""
        echo "使用方法: ./build.sh [选项]"
        echo ""
        echo "可用选项:"
        echo "  debug          - 构建 Debug 版本"
        echo "  release        - 构建 Release 版本 (默认)"
        echo "  install        - 构建并安装 Debug 版本到设备"
        echo "  install-release- 构建并安装 Release 版本到设备"
        echo "  clean          - 清理项目"
        echo "  all            - 构建所有版本"
        exit 1
        ;;
esac

echo ""
echo -e "${GREEN}================================${NC}"
echo -e "${GREEN}编译完成!${NC}"
echo -e "${GREEN}================================${NC}"