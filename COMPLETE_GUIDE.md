# MALL 项目完整指南

**最后更新**: 2026-04-23  
**当前版本**: 1.0  
**状态**: 🔄 进行中（数据库认证问题待解决）

---

## 📋 目录

1. [项目概述](#项目概述)
2. [架构设计](#架构设计)
3. [快速启动](#快速启动)
4. [技术栈](#技术栈)
5. [服务地址](#服务地址)
6. [故障排除](#故障排除)
7. [命令参考](#命令参考)

---

## 项目概述

MALL 是一个完整的电商平台，包含：
- **后端**: Spring Boot 2.7.5 + Java 17
- **前端**: Vue.js 2.x + Element UI
- **移动前端**: Uni-app
- **数据库**: MySQL 8.0
- **缓存**: Redis 7
- **文件存储**: MinIO
- **消息队列**: Kafka、RocketMQ
- **搜索**: Elasticsearch

---

## 架构设计

### 系统整体架构

```
┌─────────────────────────────────────────────┐
│          前端层 (Vue.js 2.x)                 │
├─────────────────────────────────────────────┤
│  管理后台 (8888)  │  移动前端 (Uni-app)     │
└──────────┬───────────────────────┬──────────┘
           │                       │
           └───────┬───────────────┘
                   │ HTTP/API
        ┌──────────▼──────────┐
        │   后端 API (8080)   │
        │  Spring Boot 2.7.5  │
        │    Java 17          │
        └──────────┬──────────┘
                   │
        ┌──────────▼──────────────────┐
        │   基础设施层                  │
        ├──────────────────────────────┤
        │ MySQL 8.0 (3306)            │
        │ Redis 7 (6379)              │
        │ MinIO (9000/9001)           │
        │ Kafka / RocketMQ            │
        │ Elasticsearch               │
        └──────────────────────────────┘
```

### 后端模块结构

```
mall-master/
├── mall-common           # 公共工具、常量、响应包装
├── mall-mbg             # MyBatis 生成的 DAO 和 mapper
├── mall-security        # JWT 认证、Spring Security
├── mall-admin           # 管理后台 API (主程序)
├── mall-portal          # 商城门户 API (用户侧)
├── mall-search          # 搜索服务 (Elasticsearch)
└── mall-demo            # 演示代码
```

### 数据库设计

**核心表组**:
- `ums_*` - 用户管理（admin、member、role、permission）
- `pms_*` - 商品管理（product、category、attribute）
- `oms_*` - 订单管理（order、cart、return）
- `cms_*` - 内容管理（topic、subject、help）
- `sms_*` - 营销管理（coupon、flashsale、activity）

**数据初始化**: 76 个表，包含完整的示例数据

---

## 快速启动

### 方案 A: Docker 完全容器化（推荐）

```bash
# 1. 构建镜像（首次，5-10 分钟）
docker-compose build

# 2. 启动所有服务
docker-compose up -d

# 3. 验证后端就绪（等待 30-60 秒）
# curl http://localhost:8080/actuator/health
curl http://localhost:8888/actuator/health

# 4. 启动前端开发服务（新终端）
cd mall-admin-web-master/mall-admin-web-master
npm install
npm run dev

# 5. 访问应用
# 前端: http://localhost:8888

# 后端：http://localhost:8888/swagger-ui/index.html
```

### 方案 B: Docker 依赖 + 主机 Java

```bash
# 1. 启动 Docker 依赖服务
docker-compose up -d mysql redis minio

# 2. 启动后端（在 mall-master 目录）
mvn spring-boot:run -pl mall-admin

# 3. 启动前端（在 mall-admin-web-master/mall-admin-web-master）
npm install
npm run dev
```

---

## 技术栈

### 后端技术栈

| 组件 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 2.7.5 | 框架 |
| Java | 17 | 运行时 |
| MySQL | 8.0 | 主数据库 |
| MyBatis | 3.5.10 | ORM |
| Druid | 1.2.14 | 连接池 |
| Spring Security | - | 认证授权 |
| JWT | 0.12.5 | Token 认证 |
| Swagger/Springfox | 3.0.0 | API 文档 |
| Redis | 7 | 缓存 |
| MinIO | 8.4.5 | 文件存储 |
| Kafka | - | 消息队列 |
| RocketMQ | 4.9.4 | 消息队列 |

### 前端技术栈

| 组件 | 版本 | 用途 |
|------|------|------|
| Vue.js | 2.x | 框架 |
| Element UI | - | UI 组件库 |
| Webpack | - | 打包工具 |
| Babel | - | 代码转译 |
| Axios | - | HTTP 客户端 |
| Vuex | - | 状态管理 |
| Vue Router | - | 路由 |

---

## 服务地址

| 服务 | URL | 凭证 | 说明 |
|------|-----|------|------|
| 管理后台 | http://localhost:8888 | admin/123456 | Vue.js 管理界面 |
| 后端 API | http://localhost:8080 | - | Spring Boot API |
| API 文档 | http://localhost:8080/swagger-ui.html | - | Swagger UI |
| MinIO 控制台 | http://localhost:9001 | minioadmin/minioadmin | 文件管理 |
| MySQL | localhost:3306 | root/root | 数据库 |
| Redis | localhost:6379 | - | 缓存 |

---

## 故障排除

### Docker 镜像构建失败

**症状**: `BUILD FAILURE` 或 `Could not find artifact`

**原因**: Maven 编译时依赖解析失败

**解决**:
1. 确保 Dockerfile 使用 `mvn clean package -DskipTests`（不用 `-pl` 参数）
2. 检查 POM 中是否包含所有依赖
3. 清理旧镜像: `docker rmi mall-mall-admin:latest`
4. 重新构建: `docker-compose build`

### MySQL 连接失败

**症状**: `Public Key Retrieval is not allowed`

**原因**: MySQL 8.0 的 caching_sha2_password 认证需要特殊配置

**解决**:
1. 在 JDBC URL 中添加: `&allowPublicKeyRetrieval=true`
2. 修改文件:
   - `docker-compose.yml`
   - `application-dev.yml`
   - `application-docker.yml`

### 应用启动失败

**症状**: `ClassNotFoundException: javax.annotation.PostConstruct`

**原因**: Java 17 中 javax.annotation 被移除了

**解决**: 在 `pom.xml` 中添加依赖:
```xml
<dependency>
    <groupId>javax.annotation</groupId>
    <artifactId>javax.annotation-api</artifactId>
    <version>1.3.2</version>
</dependency>
```

### 前端无法调用 API

**症状**: 网络错误、CORS 错误

**原因**: Webpack 代理配置不正确

**解决**:
1. 检查 `config/index.js` 中的 `proxyTable` 配置
2. 确保 API URL 正确: `http://localhost:8080`
3. 清除浏览器缓存

### 登陆失败

**症状**: 密码不正确（即使输入正确密码）

**原因**: 数据库中的密码哈希值问题

**解决**:
1. 检查数据库是否完整初始化
2. 验证 ums_admin 表有数据
3. 重新初始化数据库:
   ```bash
   docker-compose exec mysql mysql -uroot -proot mall < mall.sql
   ```

---

## 命令参考

### Docker Compose 命令

```bash
# 启动所有服务
docker-compose up -d

# 停止所有服务
docker-compose stop

# 完全移除容器（保留数据）
docker-compose down

# 删除所有数据
docker-compose down -v

# 查看容器状态
docker-compose ps

# 查看日志
docker-compose logs -f mall-admin

# 进入容器
docker-compose exec mysql bash
```

### Maven 命令

```bash
# 构建所有模块
mvn clean package -DskipTests

# 构建特定模块
mvn clean package -DskipTests -pl mall-admin

# 运行应用
mvn spring-boot:run -pl mall-admin

# 运行测试
mvn test -pl mall-portal
```

### npm 命令

```bash
# 安装依赖
npm install

# 开发服务器（热更新）
npm run dev

# 生产构建
npm run build

# 清除缓存
npm cache clean --force

# 切换 npm 源
npm config set registry https://registry.npmmirror.com
```

### 数据库命令

```bash
# 连接数据库
docker-compose exec mysql mysql -uroot -proot mall

# 导入 SQL 脚本
docker-compose exec mysql mysql -uroot -proot mall < mall.sql

# 查看表
SHOW TABLES;

# 查看用户
SELECT * FROM ums_admin;

# 重置密码
UPDATE ums_admin SET password = 'xxx' WHERE username = 'admin';
```

---

## 开发建议

### 后端开发

1. 在 `mall-master` 目录启动 Spring Boot
2. 修改代码后需要重启应用（不支持热重载）
3. 使用 Swagger UI 测试 API: `http://localhost:8080/swagger-ui.html`
4. 查看日志: `docker-compose logs -f mall-admin`

### 前端开发

1. 在 `mall-admin-web-master/mall-admin-web-master` 运行 `npm run dev`
2. 前端支持热更新，修改后浏览器自动刷新
3. 使用浏览器 F12 开发者工具调试
4. 检查 Network 标签验证 API 调用是否正确

### 数据库开发

1. 不要修改 mall.sql，改为编写迁移脚本
2. 使用 MySQL 客户端连接: `mysql -h localhost -u root -proot mall`
3. 新增表时更新对应的 MyBatis mapper

---

## 参考资源

- 项目 GitHub: https://github.com/macrozheng/mall
- Spring Boot 文档: https://docs.spring.io/spring-boot/docs/2.7.5/reference/html/
- Vue.js 2.x 文档: https://v2.vuejs.org/
- MySQL 8.0 文档: https://dev.mysql.com/doc/refman/8.0/en/
- Docker 文档: https://docs.docker.com/

---

**上次更新**: 2026-04-23 21:15  
**下一步**: 解决登陆认证问题，完成前端/后端集成测试

