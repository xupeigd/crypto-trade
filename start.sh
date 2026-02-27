#!/bin/bash

# =====================================================
# Crypto-Trade 应用启动脚本
# 功能: 启动包含前后端的完整Spring Boot应用
# =====================================================

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 项目根目录
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$PROJECT_ROOT/backend"
JAR_FILE="$BACKEND_DIR/target/crypto-trade-backend-1.0.0.jar"

# JVM优化参数
JAVA_OPTS="-XX:ReservedCodeCacheSize=256m -Xmx2g -Xms512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# 加载环境变量
ENV_FILE="$PROJECT_ROOT/backend/.env"
if [ -f "$ENV_FILE" ]; then
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}加载环境变量${NC}"
    echo -e "${BLUE}========================================${NC}"
    echo ""

    # 读取.env文件并export环境变量(跳过注释行和空行)
    while IFS= read -r line || [ -n "$line" ]; do
        # 跳过注释行和空行
        if [[ ! "$line" =~ ^#.*$ ]] && [[ ! -z "$line" ]]; then
            # export环境变量
            export "$line"
            # 显示加载的变量(隐藏敏感信息)
            if [[ "$line" =~ PASSWD ]]; then
                echo -e "  ${GREEN}✓${NC} ${line%%=*}=***"
            else
                echo -e "  ${GREEN}✓${NC} ${line%%=*}=${line#*=}"
            fi
        fi
    done < "$ENV_FILE"

    echo ""
    echo -e "${GREEN}✓ 环境变量加载完成${NC}"
    echo ""
else
    echo -e "${YELLOW}警告: 未找到.env文件 ($ENV_FILE)${NC}"
    echo -e "${YELLOW}将使用默认配置启动应用${NC}"
    echo ""
fi

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Crypto-Trade 应用启动${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# 1. 检查jar文件是否存在
if [ ! -f "$JAR_FILE" ]; then
    echo -e "${RED}错误: 未找到jar文件${NC}"
    echo -e "${YELLOW}  期望位置: $JAR_FILE${NC}"
    echo ""
    echo -e "${YELLOW}请先运行构建脚本:${NC}"
    echo -e "  ./build-all.sh"
    echo ""
    exit 1
fi

echo -e "${GREEN}✓ 找到jar文件${NC}"
echo -e "  $JAR_FILE"
echo ""

# 2. 检查Java环境
if ! command -v java &> /dev/null; then
    echo -e "${RED}错误: 未找到Java${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Java版本: $(java -version 2>&1 | head -n 1)${NC}"
echo ""

# 3. 检查8080端口是否被占用
if lsof -Pi :8080 -sTCP:LISTEN -t >/dev/null 2>&1 ; then
    echo -e "${YELLOW}警告: 端口8080已被占用${NC}"
    echo ""
    echo -e "${YELLOW}占用进程:${NC}"
    lsof -Pi :8080 -sTCP:LISTEN -t
    echo ""

    read -p "是否要终止占用端口的进程并继续? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo -e "${YELLOW}正在终止占用端口的进程...${NC}"
        lsof -ti :8080 -sTCP:LISTEN | xargs kill -9
        sleep 1
        echo -e "${GREEN}✓ 端口已释放${NC}"
    else
        echo -e "${RED}启动已取消${NC}"
        exit 1
    fi
fi

# 4. 启动应用
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}正在启动应用...${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "${YELLOW}JVM参数:${NC}"
echo "  $JAVA_OPTS"
echo ""
echo -e "${YELLOW}访问地址:${NC}"
echo -e "  前端界面: ${GREEN}http://localhost:8080/${NC}"
echo -e "  后端API:  ${GREEN}http://localhost:8080/api/health${NC}"
echo ""
echo -e "${YELLOW}日志输出:${NC}"
echo -e "  保存到: ${BLUE}logs/application.log${NC}"
echo ""
echo -e "${YELLOW}按 Ctrl+C 停止应用${NC}"
echo ""

# 启动jar文件
cd "$BACKEND_DIR"
java $JAVA_OPTS -jar "$JAR_FILE"

# 应用退出后的清理
echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}应用已停止${NC}"
echo -e "${BLUE}========================================${NC}"
