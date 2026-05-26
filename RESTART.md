# MALL 平台重启说明

## 终端要求

使用 **Git Bash**（不是 CMD、不是 PowerShell），它支持标准 Linux 命令。

## 第一步：设置 Maven

```bash
export PATH="/c/Users/JD-MY/.m2/wrapper/dists/apache-maven-3.9.9/937e263b/bin:$PATH"
```

## 第二步：启动依赖服务（如果没跑）

```bash
cd D:/DownLoad/malldemo-main
docker-compose up -d
docker ps  # 确认 mysql、redis、minio 都在跑
```

## 第三步：启动后端（按顺序，开 3 个终端）

### 终端 1 — Admin 后端 :8080

```bash
export PATH="/c/Users/JD-MY/.m2/wrapper/dists/apache-maven-3.9.9/937e263b/bin:$PATH"
cd D:/DownLoad/malldemo-main/mall-master
mvn spring-boot:run -pl mall-admin
```

看到 `Started MallAdminApplication` 表示成功。

### 终端 2 — Portal 后端 :8085

```bash
export PATH="/c/Users/JD-MY/.m2/wrapper/dists/apache-maven-3.9.9/937e263b/bin:$PATH"
cd D:/DownLoad/malldemo-main/mall-master
mvn spring-boot:run -pl mall-portal
```

### 终端 3 — SuperBizAgent :9900

```bash
export PATH="/c/Users/JD-MY/.m2/wrapper/dists/apache-maven-3.9.9/937e263b/bin:$PATH"
cd "D:/DownLoad/SuperBizAgent-release-2026-05-17/SuperBizAgent-release-2026-05-17"
mvn spring-boot:run
```

## 第四步：启动前端

登陆商家端：http://localhost:8888
用户端：需通过HBuilder X运行 mall-app-web-master 文件夹下的项目

Admin登录到商家管理后台后，可添加客服账号并配置会话。客服模块支持基于 DashScope API 的意图识别与自动回复功能。验证FAQ匹配效果，确认agent是否成功读取milvus知识库。

## 常见问题

### 端口被占用
```bash
# 查看端口占用
netstat -ano | grep ":8085"
# 杀进程（替换 PID）
taskkill //F //PID 12345
```

### Maven 找不到
```bash
export PATH="/c/Users/JD-MY/.m2/wrapper/dists/apache-maven-3.9.9/937e263b/bin:$PATH"
```

## 服务地址

| 服务 | 端口 | 说明 |
|------|------|------|
| Admin API | 8080 | 商家管理后端 |
| Portal API | 8085 | 用户端后端 |
| SuperBizAgent | 9900 | AI Agent 客服 |
| Admin UI | 8888 | 商家后台前端 |
| MySQL | 3306 | 数据库 |
| Redis | 6379 | 缓存 |
| Milvus | 19530 | 向量知识库 |
