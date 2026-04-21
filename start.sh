#!/bin/bash

# MALL 项目快速启动脚本

set -e

echo "=========================================="
echo "MALL 电商平台 - 完整启动流程"
echo "=========================================="

# 检查 Docker 和 Docker Compose
if ! command -v docker &> /dev/null; then
    echo "❌ 错误: 未检测到 Docker，请先安装 Docker"
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    echo "❌ 错误: 未检测到 Docker Compose，请先安装"
    exit 1
fi

echo "✅ Docker 和 Docker Compose 检查通过"

# 启动 Docker 容器
echo ""
echo "========== 步骤 1: 启动依赖服务 =========="
echo "启动 MySQL、Redis、MinIO..."
docker-compose up -d

echo "⏳ 等待服务启动完成..."
sleep 15

# 检查服务状态
echo ""
echo "========== 步骤 2: 检查服务状态 =========="

# 检查 MySQL
if docker exec mall-mysql mysqladmin ping -h localhost > /dev/null 2>&1; then
    echo "✅ MySQL 服务运行正常"
else
    echo "⚠️  MySQL 服务启动中，请等待..."
fi

# 检查 Redis
if docker exec mall-redis redis-cli ping > /dev/null 2>&1; then
    echo "✅ Redis 服务运行正常"
else
    echo "⚠️  Redis 服务启动中，请等待..."
fi

# 检查 MinIO
if docker exec mall-minio curl -s http://localhost:9000/minio/health/live > /dev/null 2>&1; then
    echo "✅ MinIO 服务运行正常"
else
    echo "⚠️  MinIO 服务启动中，请等待..."
fi

echo ""
echo "========== 步骤 3: 后端启动 =========="
echo ""
echo "现在你可以在另一个终端启动后端服务："
echo ""
echo "  cd mall-master"
echo "  mvn clean package -DskipTests"
echo "  mvn spring-boot:run -pl mall-admin"
echo ""
echo "后端服务将运行在: http://localhost:8080"

echo ""
echo "========== 步骤 4: 前端启动 =========="
echo ""
echo "在第三个终端启动前端服务："
echo ""
echo "  cd mall-admin-web-master/mall-admin-web-master"
echo "  npm install"
echo "  npm run dev"
echo ""
echo "前端服务将运行在: http://localhost:8888"

echo ""
echo "========== 服务地址汇总 =========="
echo "MySQL:        localhost:3306 (用户名: root, 密码: root)"
echo "Redis:        localhost:6379"
echo "MinIO Web UI: http://localhost:9001 (用户名: minioadmin, 密码: minioadmin)"
echo "MinIO API:    http://localhost:9000"
echo "后端 API:     http://localhost:8080"
echo "前端应用:     http://localhost:8888"
echo ""
echo "========== 停止所有服务 =========="
echo "运行: docker-compose down"
echo ""
echo "✅ 所有依赖服务启动完成！"
