# MALL 项目 - 快速参考卡

## 🚀 快速启动命令

### 方案 A：Docker 依赖 + 主机 Java（推荐开发）

```bash
# 1. 启动 Docker 依赖服务
docker-compose up -d

# 2. 启动后端（新终端）
cd mall-master
mvn spring-boot:run -pl mall-admin

# 3. 启动前端（新终端）
cd mall-admin-web-master/mall-admin-web-master
npm install
npm run dev

# 访问应用
# 前端: http://localhost:8888
# 后端: http://localhost:8080/swagger-ui.html
```

### 方案 B：完全容器化（一键启动）

```bash
# 1. 构建后端镜像（首次）
docker-compose build

# 2. 启动所有服务
docker-compose up -d

# 3. 启动前端（新终端）
cd mall-admin-web-master/mall-admin-web-master
npm install
npm run dev

# 访问应用
# 前端: http://localhost:8888
# 后端: http://localhost:8080/swagger-ui.html
```

---

## 🛑 常用命令

| 操作 | 命令 |
|------|------|
| 查看容器状态 | `docker-compose ps` |
| 查看服务日志 | `docker-compose logs -f mall-admin` |
| 停止服务 | `docker-compose stop` |
| 完全移除 | `docker-compose down` |
| 删除所有数据 | `docker-compose down -v` |
| 重启服务 | `docker-compose restart` |
| 进入容器 | `docker-compose exec mysql mysql -uroot -proot` |

---

## 📋 服务地址和凭证

| 服务 | URL | 用户名 | 密码 |
|------|-----|--------|------|
| 管理后台 | http://localhost:8888 | admin | 123456 |
| 后端 API | http://localhost:8080 | - | - |
| Swagger UI | http://localhost:8080/swagger-ui.html | - | - |
| MinIO Web | http://localhost:9001 | minioadmin | minioadmin |
| MySQL | localhost:3306 | root | root |
| Redis | localhost:6379 | - | - |

---

## 🔍 故障排除

### 端口冲突

```bash
# 检查端口占用
lsof -i :3306
lsof -i :6379
lsof -i :9000
lsof -i :8080
lsof -i :8888

# 清理容器
docker-compose down
```

### Maven 构建慢

```bash
# 配置阿里云镜像（编辑 ~/.m2/settings.xml）
<mirror>
  <id>aliyun</id>
  <mirrorOf>central</mirrorOf>
  <url>https://maven.aliyun.com/repository/public</url>
</mirror>
```

### npm 安装失败

```bash
# 清除缓存
npm cache clean --force

# 使用淘宝镜像
npm config set registry https://registry.npmmirror.com
npm install
```

### 容器无法连接到数据库

```bash
# 检查网络
docker network inspect mall-network

# 查看容器日志
docker logs mall-mysql
docker logs mall-admin
```

---

## 📝 项目结构

```
MALL/
├── mall-master/                  # 后端 Spring Boot
│   ├── mall-admin/              # 管理员 API
│   ├── mall-common/             # 公共模块
│   ├── mall-security/           # 安全认证
│   └── ... 其他模块
├── mall-admin-web-master/       # 管理后台前端 (Vue.js)
├── mall-app-web-master/         # 移动前端 (Uni-app)
├── docker-compose.yml           # Docker 容器编排
├── Dockerfile                   # 后端镜像定义
├── SETUP.md                     # 详细启动指南
└── mall.sql                     # 数据库初始化脚本
```

---

## 🎯 开发流程

### 修改后端代码

1. 编辑 `mall-master` 中的代码
2. 后端自动重新编译（若支持热更新）
3. 重启应用： `Ctrl+C` 后重新运行

### 修改前端代码

1. 编辑 `mall-admin-web-master` 中的代码
2. 前端支持热更新，浏览器自动刷新

### 修改数据库

```bash
# 连接数据库
mysql -h localhost -uroot -proot mall

# 执行 SQL 脚本
mysql -h localhost -uroot -proot mall < migration.sql
```

---

## ✅ 验证应用正常运行

1. **后端健康检查**
   ```bash
   curl http://localhost:8080/actuator/health
   ```

2. **前端访问**
   - 打开 http://localhost:8888
   - 默认账号: `admin` / `123456`

3. **API 测试**
   - 访问 http://localhost:8080/swagger-ui.html
   - 尝试调用任意接口

4. **数据库连接**
   ```bash
   mysql -h localhost -uroot -proot mall
   show tables;
   exit;
   ```

---

## 📚 相关文档

- [完整启动指南](SETUP.md)
- [项目文档](mall-master/document/)
- [API 文档](http://localhost:8080/swagger-ui.html)（启动后访问）

---

祝开发愉快！🚀 有问题请查看 SETUP.md 中的故障排除部分。
