#!/bin/bash

# =====================================================
# Crypto-Trade 前后端二合一构建脚本
# 功能: 构建包含前后端的完整Spring Boot jar包
# =====================================================

set -e  # 遇到错误立即退出

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 项目根目录
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$PROJECT_ROOT/backend"
FRONTEND_DIR="$PROJECT_ROOT/frontend"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Crypto-Trade 二合一构建脚本${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# 1. 检查Node.js和npm
echo -e "${YELLOW}[1/5] 检查构建环境...${NC}"
if ! command -v node &> /dev/null; then
    echo -e "${RED}错误: 未找到Node.js，请先安装Node.js${NC}"
    echo -e "${YELLOW}下载地址: https://nodejs.org/${NC}"
    exit 1
fi

if ! command -v npm &> /dev/null; then
    echo -e "${RED}错误: 未找到npm${NC}"
    exit 1
fi

NODE_VERSION=$(node -v)
NPM_VERSION=$(npm -v)
echo -e "${GREEN}✓ Node.js版本: $NODE_VERSION${NC}"
echo -e "${GREEN}✓ npm版本: $NPM_VERSION${NC}"
echo ""

# 2. 检查Java和Maven
echo -e "${YELLOW}[2/5] 检查Java环境...${NC}"
if ! command -v java &> /dev/null; then
    echo -e "${RED}错误: 未找到Java，请先安装Java 17${NC}"
    exit 1
fi

if ! command -v mvn &> /dev/null; then
    echo -e "${RED}错误: 未找到Maven${NC}"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -n 1)
MVN_VERSION=$(mvn -version | head -n 1)
echo -e "${GREEN}✓ Java版本: $JAVA_VERSION${NC}"
echo -e "${GREEN}✓ Maven版本: $MVN_VERSION${NC}"
echo ""

# 3. 安装前端依赖
echo -e "${YELLOW}[3/5] 安装前端依赖...${NC}"
cd "$FRONTEND_DIR"
if [ ! -d "node_modules" ]; then
    echo "正在安装npm依赖..."
    npm install
    echo -e "${GREEN}✓ 前端依赖安装完成${NC}"
else
    echo -e "${GREEN}✓ 前端依赖已存在，跳过安装${NC}"
fi
echo ""

# 4. 构建前端
echo -e "${YELLOW}[4/5] 构建前端React应用...${NC}"
echo "正在构建前端..."
npm run build

if [ ! -d "dist" ]; then
    echo -e "${RED}错误: 前端构建失败，未找到dist目录${NC}"
    exit 1
fi

echo -e "${GREEN}✓ 前端构建完成${NC}"
echo -e "${GREEN}  构建输出: $FRONTEND_DIR/dist/${NC}"
echo ""

# 5. 构建后端(包含前端资源)
echo -e "${YELLOW}[5/5] 构建后端Spring Boot应用...${NC}"
cd "$BACKEND_DIR"

echo "正在清理旧构建..."
mvn clean

echo "正在打包后端(Maven会自动集成前端资源)..."
mvn package -DskipTests

JAR_FILE="$BACKEND_DIR/target/crypto-trade-backend-1.0.0.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo -e "${RED}错误: 后端构建失败，未找到jar文件${NC}"
    exit 1
fi

# 获取jar文件大小
JAR_SIZE=$(du -h "$JAR_FILE" | cut -f1)

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}✓ 构建成功!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "${BLUE}jar文件位置:${NC}"
echo -e "  $JAR_FILE"
echo -e "${BLUE}文件大小:${NC} $JAR_SIZE"
echo ""
echo -e "${YELLOW}启动应用:${NC}"
echo -e "  cd backend"
echo -e "  java -jar target/crypto-trade-backend-1.0.0.jar"
echo ""
echo -e "${YELLOW}访问应用:${NC}"
echo -e "  前端界面: ${GREEN}http://localhost:8080/${NC}"
echo -e "  后端API:  ${GREEN}http://localhost:8080/api/xxx${NC}"
echo ""
