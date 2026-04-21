# MALL 项目启动指南

## 📋 项目概述

MALL 是一个完整的电商平台项目，包含后端（Spring Boot 微服务）和前端（Vue.js/Uni-app）。

### 项目架构

```
MALL/
├── mall-master/              # 后端 Spring Boot 微服务
│   ├── mall-admin           # 管理员后台 API
│   ├── mall-common          # 公共模块
│   ├── mall-mbg             # MyBatis 生成器
│   ├── mall-security        # 安全认证模块
│   ├── mall-demo            # 演示模块
│   ├── mall-search          # 搜索服务
│   └── mall-portal          # 商城门户 API
├── mall-admin-web-master/   # 管理员后台前端 (Vue.js)
├── mall-app-web-master/     # 移动应用前端 (Uni-app)
└── mall-learning-master/    # 学习示例代码
```

---

## 🚀 快速启动

### 前提条件

**方案A（推荐开发）：** Docker + 主机 Java 运行
- **Docker** 和 **Docker Compose**
- **Java 17+**
- **Maven 3.6+**
- **Node.js 18+** 和 **npm 9+**

**方案B（完全容器化）：** 仅需 Docker
- **Docker** 和 **Docker Compose**（无需本地 Java、Maven）

---

### 方案 A：Docker 依赖服务 + 主机 Java 应用（推荐开发）

```bash
# 1. 进入项目根目录
cd /mnt/d/develop/program/MALL

# 2. 启动 Docker 容器（MySQL、Redis、MinIO）
docker-compose up -d

# 3. 等待 30 秒，在新终端启动后端
cd mall-master
mvn clean package -DskipTests
mvn spring-boot:run -pl mall-admin

# 4. 在第三个终端启动前端
cd mall-admin-web-master/mall-admin-web-master
npm install
npm run dev
```

### 方案 B：完全容器化（一键启动）

```bash
# 1. 进入项目根目录
cd /mnt/d/develop/program/MALL

# 2. 构建后端 Docker 镜像（首次需要 5-10 分钟）
docker-compose build

# 3. 启动所有服务
docker-compose up -d

# 4. 在新终端启动前端
cd mall-admin-web-master/mall-admin-web-master
npm install
npm run dev

# 所有后端服务自动运行在容器中！
```

### 访问应用

| 服务 | URL | 用户名 | 密码 |
|------|-----|--------|------|
| 管理员后台 | http://localhost:8888 | admin | 123456 |
| 后端 API | http://localhost:8080 | - | - |
| MinIO 控制台 | http://localhost:9001 | minioadmin | minioadmin |

---

## 📦 详细启动步骤

### 方案 A：主机 Java 运行

#### 步骤 1: 启动 Docker 容器

```bash
# 进入项目根目录
cd /mnt/d/develop/program/MALL

# 启动所有依赖服务
docker-compose up -d

# 查看容器状态
docker-compose ps
```

**预期输出:**
```
NAME                COMMAND                  SERVICE             STATUS
mall-mysql          "docker-entrypoint.s…"   mysql               Up 30 seconds (healthy)
mall-redis          "redis-server --appe…"   redis               Up 30 seconds (healthy)
mall-minio          "/usr/bin/docker-ent…"   minio               Up 30 seconds (healthy)
```

### 步骤 2: 启动后端服务

```bash
# 进入后端项目目录
cd mall-master

# 下载依赖并编译（首次需要较长时间）
mvn clean package -DskipTests

# 启动管理员模块
mvn spring-boot:run -pl mall-admin
```

**预期日志:**
```
INFO  : Started MallAdminApplication in 12.345 seconds
INFO  : Tomcat started on port(s): 8080
```

### 步骤 3: 启动前端服务

```bash
# 进入前端项目目录
cd mall-admin-web-master/mall-admin-web-master

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

**预期日志:**
```
> webpack-dev-server --inline --progress --config build/webpack.dev.conf.js
...
Compiled successfully
Listening on http://localhost:8888
```

### 步骤 4: 验证应用

1. **打开浏览器访问**: http://localhost:8888
2. **登录**: 
   - 用户名: `admin`
   - 密码: `123456`
3. **检查后端 API**: http://localhost:8080/swagger-ui.html

---

### 方案 B：完全容器化运行

#### 步骤 1: 构建 Docker 镜像

```bash
# 进入项目根目录
cd /mnt/d/develop/program/MALL

# 构建后端 Docker 镜像
# 注意：此步骤会在 Docker 容器中运行 Maven 编译，需要 5-10 分钟
docker-compose build

# 查看构建日志
# 成功时会显示 "Successfully tagged mall:latest"
```

**预期输出:**
```
Building mall-admin
Step 1/14 : FROM maven:3.9-eclipse-temurin-17 as builder
 ---> abc123def456
Step 2/14 : WORKDIR /build
 ...
Step 14/14 : ENTRYPOINT ["java", "-jar", "app.jar"]
 ---> Running in xyz789
Successfully tagged mall-admin:latest
```

#### 步骤 2: 启动所有容器

```bash
# 启动所有服务（MySQL、Redis、MinIO、Java 后端）
docker-compose up -d

# 查看容器运行状态
docker-compose ps

# 查看后端应用日志
docker-compose logs -f mall-admin

# 等待应用启动完成（通常需要 30-60 秒）
# 日志中显示 "Started MallAdminApplication" 表示启动成功
```

**预期输出:**
```
NAME                COMMAND                  SERVICE             STATUS              PORTS
mall-mysql          "docker-entrypoint.s…"   mysql               Up 1 minute         0.0.0.0:3306->3306/tcp
mall-redis          "redis-server --appe…"   redis               Up 1 minute         0.0.0.0:6379->6379/tcp
mall-minio          "/usr/bin/docker-ent…"   minio               Up 1 minute         0.0.0.0:9000->9000/tcp, 0.0.0.0:9001->9001/tcp
mall-admin          "java -jar app.jar"      mall-admin          Up 1 minute         0.0.0.0:8080->8080/tcp
```

#### 步骤 3: 启动前端服务

```bash
# 在新终端启动前端
cd mall-admin-web-master/mall-admin-web-master

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

#### 步骤 4: 验证应用

1. **打开浏览器访问**: http://localhost:8888
2. **登录**: 
   - 用户名: `admin`
   - 密码: `123456`
3. **检查后端 API**: http://localhost:8080/swagger-ui.html
4. **查看 MinIO 文件存储**: http://localhost:9001

---

---

## 🛑 停止服务

### 方案 A：停止 Docker 和主机应用

```bash
# 停止但保留容器和数据
docker-compose stop

# 完全移除容器（数据会保留在 volume 中）
docker-compose down

# 删除所有数据（谨慎操作）
docker-compose down -v

# 停止主机运行的后端和前端
# - 后端终端: 按 Ctrl+C
# - 前端终端: 按 Ctrl+C
```

### 方案 B：停止所有容器

```bash
# 停止所有容器（包括后端）
docker-compose stop

# 完全移除所有容器
docker-compose down

# 删除所有数据
docker-compose down -v

# 停止前端（仍需在主机运行）
# 前端终端: 按 Ctrl+C
```

### 查看日志

```bash
# 查看后端应用日志（实时）
docker-compose logs -f mall-admin

# 查看特定容器日志
docker-compose logs mysql
docker-compose logs redis
docker-compose logs minio

# 查看最后 100 行日志
docker-compose logs --tail=100
```

---

## 🔧 常见问题

### 问题 1: 端口被占用

```bash
# 检查端口占用
lsof -i :3306   # MySQL
lsof -i :6379   # Redis
lsof -i :9000   # MinIO
lsof -i :8080   # 后端
lsof -i :8888   # 前端

# 释放被占用的容器
docker-compose down
```

### 问题 2: Maven 构建太慢

在 `~/.m2/settings.xml` 中配置阿里云镜像：

```xml
<mirrors>
  <mirror>
    <id>aliyun</id>
    <mirrorOf>central</mirrorOf>
    <url>https://maven.aliyun.com/repository/public</url>
  </mirror>
</mirrors>
```

### 问题 3: npm 安装依赖失败

```bash
# 清除 npm 缓存
npm cache clean --force

# 使用淘宝镜像
npm config set registry https://registry.npmmirror.com

# 重新安装
npm install
```

### 问题 4: Docker 容器无法连接

```bash
# 检查容器网络
docker network ls
docker network inspect mall-network

# 查看容器日志
docker logs mall-mysql
docker logs mall-redis
docker logs mall-minio
```

---

## 🐳 Docker 详细配置说明

### Dockerfile 多阶段构建

项目根目录的 `Dockerfile` 使用多阶段构建来优化镜像大小：

**第一阶段（编译）- maven:3.9-eclipse-temurin-17**
```dockerfile
FROM maven:3.9-eclipse-temurin-17 as builder
# 包含完整的 Maven 和 Java 开发工具
# 在此阶段下载依赖、编译源代码、生成 JAR 文件
# 体积较大（~1.5GB），但只在构建时使用
```

**第二阶段（运行）- eclipse-temurin:17-jre-alpine**
```dockerfile
FROM eclipse-temurin:17-jre-alpine
# 只包含 Java 运行时环境（JRE），不包含开发工具
# 复制第一阶段生成的 JAR 文件
# 最终镜像体积小（~200MB）
```

**优势**:
- ✅ 最终镜像只有 200MB（vs 1.5GB+）
- ✅ 启动速度快
- ✅ 生产环境更安全（无编译工具）

### docker-compose.yml 服务详解

#### mysql - 主数据库
```yaml
mysql:
  image: mysql:8.0
  container_name: mall-mysql
  environment:
    MYSQL_ROOT_PASSWORD: root      # root 用户密码
    MYSQL_DATABASE: mall           # 初始化数据库
  ports:
    - "3306:3306"                 # 主机:容器 端口映射
  volumes:
    - mysql_data:/var/lib/mysql   # 数据持久化
    - ./mall.sql:/docker-entrypoint-initdb.d/init.sql:ro  # 初始化脚本
  healthcheck:
    test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
    interval: 10s
```

#### redis - 缓存服务
```yaml
redis:
  image: redis:7-alpine
  container_name: mall-redis
  ports:
    - "6379:6379"
  volumes:
    - redis_data:/data
  command: redis-server --appendonly yes  # 启用 AOF 持久化
```

#### minio - 对象存储
```yaml
minio:
  image: minio/minio:latest
  environment:
    MINIO_ROOT_USER: minioadmin     # 访问密钥
    MINIO_ROOT_PASSWORD: minioadmin # 秘钥
  ports:
    - "9000:9000"   # API 端口
    - "9001:9001"   # 管理控制台
  command: server /data --console-address ":9001"
```

#### mall-admin - Java 后端应用（仅方案 B）
```yaml
mall-admin:
  build:
    context: .
    dockerfile: Dockerfile         # 使用项目根目录的 Dockerfile
  environment:
    SPRING_PROFILES_ACTIVE: docker # 使用 docker 配置文件
    SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/mall  # 服务名而非 localhost
    SPRING_REDIS_HOST: redis
    MINIO_ENDPOINT: http://minio:9000
  depends_on:
    mysql:
      condition: service_healthy   # 等待 MySQL 启动完成
```

### 容器间通信

Docker Compose 自动创建 `mall-network` 网络，容器间可直接通信：

**在容器内访问其他服务:**
```
MySQL:  mysql://mysql:3306/mall
Redis:  redis://redis:6379
MinIO:  http://minio:9000
```

**从主机访问容器:**
```
MySQL:  localhost:3306
Redis:  localhost:6379
MinIO:  http://localhost:9000 (API)
MinIO:  http://localhost:9001 (Web UI)
```

### 新增配置文件

**application-docker.yml** 配置文件：
```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:mysql://mysql:3306/mall}
    username: ${SPRING_DATASOURCE_USERNAME:root}
  redis:
    host: ${SPRING_REDIS_HOST:redis}
    port: ${SPRING_REDIS_PORT:6379}
minio:
  endpoint: ${MINIO_ENDPOINT:http://minio:9000}
```

特点：
- 使用环境变量配置，便于容器化
- 提供默认值，支持本地开发
- 路径使用 `mysql`、`redis`、`minio` 等服务名

---

## 📝 配置文件说明

### 后端配置

**开发环境** (`application-dev.yml`)：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/mall
    username: root
    password: root
  redis:
    host: localhost
    port: 6379
minio:
  endpoint: http://localhost:9000
```

**Docker 环境** (`application-docker.yml`)：
```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:mysql://mysql:3306/mall}
    username: ${SPRING_DATASOURCE_USERNAME:root}
  redis:
    host: ${SPRING_REDIS_HOST:redis}
    port: ${SPRING_REDIS_PORT:6379}
minio:
  endpoint: ${MINIO_ENDPOINT:http://minio:9000}
```

**选择配置方式:**
- 方案 A（主机 Java）：自动使用 `application-dev.yml`（localhost）
- 方案 B（容器化）：通过 `SPRING_PROFILES_ACTIVE=docker` 使用 `application-docker.yml`

### 前端配置

前端 API 请求基地址配置在 `src/main.js`:

```javascript
// 根据环境动态配置
const apiBaseUrl = process.env.API_URL || 'http://localhost:8080'
```

---

## 📚 更多信息

- **项目文档**: 见 `mall-master/document/` 目录
- **API 文档**: 后端启动后访问 http://localhost:8080/swagger-ui.html
- **原始仓库**: 基于 [macrozheng/mall](https://github.com/macrozheng/mall) 项目

---

## 🎯 下一步

启动成功后，你可以：

1. **浏览管理后台**: 体验商品管理、订单管理等功能
2. **调用后端 API**: 使用 Swagger UI 测试各个接口
3. **修改代码**: 后端修改后重启应用，前端支持热更新
4. **查看数据库**: 使用 MySQL 客户端连接到 localhost:3306

祝你开发愉快！🚀
