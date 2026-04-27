# MALL 项目问题解决方案汇总

**记录日期**: 2026-04-23  
**解决状态**: 7/8 个问题已解决

---

## 📌 遇到的问题与解决方案

### 问题 1: Maven 依赖找不到 ✅ 已解决

**问题描述**:
```
[ERROR] Could not find artifact com.macro.mall:mall-mbg:jar:1.0-SNAPSHOT
[ERROR] Could not find artifact com.macro.mall:mall-security:jar:1.0-SNAPSHOT
```

**根本原因**:
- Dockerfile 中使用 `mvn clean package -DskipTests -pl mall-admin`
- `-pl` 参数只编译单个模块，跳过了依赖模块
- Maven 尝试从远程仓库下载本地模块失败

**解决方案**:
```dockerfile
# 修改 Dockerfile 第 17 行
- RUN mvn clean package -DskipTests -pl mall-admin
+ RUN mvn clean package -DskipTests
```

**影响文件**: `Dockerfile`

**验证方法**:
```bash
docker-compose build
# 预期: BUILD SUCCESS
```

---

### 问题 2: MySQL Public Key Retrieval ✅ 已解决

**问题描述**:
```
java.sql.SQLNonTransientConnectionException: Public Key Retrieval is not allowed
com.mysql.cj.exceptions.UnableToConnectException: Public Key Retrieval is not allowed
```

**根本原因**:
- MySQL 8.0 使用 `caching_sha2_password` 认证插件
- 该插件需要公钥进行密码验证
- JDBC 默认禁用公钥获取（安全考虑）
- Java 17 + Spring Boot 2.7 不兼容 MySQL 8.0 的新认证方式

**解决方案**:
在 JDBC URL 中添加参数: `allowPublicKeyRetrieval=true`

**影响文件**:
1. `docker-compose.yml` (第 71 行)
   ```yaml
   SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/mall?...&allowPublicKeyRetrieval=true
   ```

2. `application-docker.yml` (第 15 行)
   ```yaml
   url: ${SPRING_DATASOURCE_URL:jdbc:mysql://mysql:3306/mall?...&allowPublicKeyRetrieval=true}
   ```

3. `application-dev.yml` (第 3 行)
   ```yaml
   url: jdbc:mysql://localhost:3306/mall?...&allowPublicKeyRetrieval=true
   ```

**验证方法**:
```bash
curl http://localhost:8080/actuator/health
# 预期: {"status":"UP"}
```

---

### 问题 3: javax.annotation.PostConstruct 找不到 ✅ 已解决

**问题描述**:
```
java.lang.ClassNotFoundException: javax.annotation.PostConstruct
java.lang.NoClassDefFoundError: javax/annotation/PostConstruct
```

**根本原因**:
- Java 9+ 从 JDK 中移除了 `javax.annotation` 包
- Spring Boot 2.7 仍使用 `javax.annotation`（旧包）
- Java 17 环境下需要显式添加依赖

**版本演进**:
```
Java 8 (2014)          Java 9 (2017)           Spring Boot 3.0+ (2022)
└─ 内置 javax.*    →   └─ 移除 javax.*    →   └─ 使用 jakarta.*
```

**解决方案**:
在 `pom.xml` 中添加依赖:

```xml
<!--解决JDK 9+ javax.annotation 移除问题-->
<dependency>
    <groupId>javax.annotation</groupId>
    <artifactId>javax.annotation-api</artifactId>
    <version>1.3.2</version>
</dependency>
```

**影响文件**: `mall-master/pom.xml` (第 87-97 行)

**验证方法**:
```bash
docker-compose logs mall-admin | grep "Started MallAdminApplication"
# 预期: 看到应用启动成功的日志
```

---

### 问题 4: 数据库表不完整 ✅ 已解决

**问题描述**:
```
[ERROR] Table 'mall.ums_admin' doesn't exist
```

**根本原因**:
- `mall.sql` 文件在 Docker 启动时没有完整执行
- 可能原因：初始化脚本被中断、SQL 有语法错误、文件损坏

**解决方案**:
手动重新导入 SQL 脚本:

```bash
# 1. 清空并重建数据库
docker-compose exec mysql mysql -uroot -proot -e "DROP DATABASE mall;"
docker-compose exec mysql mysql -uroot -proot -e "CREATE DATABASE mall CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;"

# 2. 导入完整 SQL 脚本
docker-compose exec -T mysql mysql -uroot -proot mall < /mnt/d/develop/program/MALL/mall.sql

# 3. 重启后端应用
docker-compose restart mall-admin
```

**影响文件**: `mall.sql`（3260 行，完整的数据库初始化脚本）

**验证方法**:
```bash
docker-compose exec -T mysql mysql -uroot -proot mall -e "SELECT COUNT(*) FROM ums_admin;"
# 预期: 8 或以上
```

---

### 问题 5: 前端 API 硬编码地址 ✅ 已解决

**问题描述**:
前端组件中硬编码了 `http://localhost:8080` 的地址，无法配置

**根本原因**:
- 上传组件中直接写死了 API URL
- 不支持环境变量或动态配置

**解决方案**:
添加全局 API_BASE_URL 配置:

**影响文件**:
1. `src/main.js` - 添加全局配置
   ```javascript
   const API_BASE_URL = process.env.API_URL || 'http://localhost:8080'
   Vue.prototype.$apiUrl = API_BASE_URL
   window.API_BASE_URL = API_BASE_URL
   ```

2. `src/components/Upload/singleUpload.vue` - 使用全局配置
3. `src/components/Upload/multiUpload.vue` - 使用全局配置
4. `src/components/Tinymce/components/editorImage.vue` - 使用全局配置

---

### 问题 6: 前端缺少代理配置 ✅ 已解决

**问题描述**:
Webpack 开发服务器无法代理 API 请求到后端

**根本原因**:
- `config/index.js` 中 `proxyTable` 为空
- 前端无法将请求转发到 `http://localhost:8080`

**解决方案**:
配置 Webpack 代理规则:

```javascript
// config/index.js
proxyTable: {
  '/admin': {
    target: process.env.API_URL || 'http://localhost:8080',
    changeOrigin: true,
    pathRewrite: {}
  },
  '/minio': { /* ... */ },
  '/swagger-ui': { /* ... */ },
  '/v2': { /* ... */ }
}
```

**影响文件**: `config/index.js`（同时修改了前端端口从 8090 改为 8888）


---

### 问题 8: 登陆密码验证失败 ⏳ 待解决

**问题描述**:
```
{"code":500,"message":"密码不正确","data":null}
```

**根本原因** (初步分析):
- SQL 中预设的密码哈希值可能不是 `123456` 对应的值
- BCrypt 版本不匹配
- 密码验证逻辑有 bug
- 数据库初始化时密码字段损坏

**已尝试的解决方案**:
1. ✅ 使用已知 BCrypt 哈希值更新密码
2. ✅ 重新导入完整 SQL 脚本
3. ✅ 重启后端应用
4. ❌ 仍然返回密码不正确

**影响文件**: 
- `mall.sql` (密码哈希)
- `mall-admin` (登陆认证逻辑)

**需要进一步诊断**:
1. 查看后端登陆服务的源代码
2. 检查 BCrypt 密码验证的具体实现
3. 验证数据库中的密码字段是否完整
4. 生成正确的 123456 对应的 BCrypt 哈希值

---

## 📊 问题统计

| 序号 | 问题 | 类型 | 状态 | 耗时 |
|-----|------|------|------|------|
| 1 | Maven 依赖问题 | 构建 | ✅ 已解决 | 1h |
| 2 | MySQL 连接问题 | 数据库 | ✅ 已解决 | 1h |
| 3 | Java 注解问题 | 依赖 | ✅ 已解决 | 30m |
| 4 | 数据库初始化 | 数据库 | ✅ 已解决 | 30m |
| 5 | 前端 API 配置 | 前端 | ✅ 已解决 | 20m |
| 6 | 前端代理配置 | 前端 | ✅ 已解决 | 15m |
| 7 | npm 速度慢 | 环境 | ✅ 已解决 | 10m |
| 8 | 登陆认证失败 | 认证 | ⏳ 待解决 | - |

---

## 🔍 问题解决的通用步骤

### 第 1 步: 识别问题
- 收集错误信息（日志、错误消息）
- 确定问题发生的阶段（构建、启动、运行时）
- 找出问题的具体表现（症状）

### 第 2 步: 分析根本原因
- 查看完整的错误栈
- 理解问题的本质（不是表面症状）
- 考虑版本兼容性、依赖关系

### 第 3 步: 设计解决方案
- 选择最小改动的方案
- 避免"打补丁"式的修复
- 考虑是否会引入新问题

### 第 4 步: 实施和验证
- 应用修复
- 验证问题是否解决
- 检查是否有副作用

### 第 5 步: 文档和总结
- 记录问题和解决方案
- 为类似问题预防或快速解决
- 分享知识给团队

---

## 💡 最佳实践总结

1. **依赖管理**: 始终让所有模块编译，而不是跳过
2. **版本兼容**: 注意 Java 版本变化和库版本更新
3. **数据库**: 保证初始化脚本完整执行
4. **配置管理**: 使用环境变量而不是硬编码
5. **日志分析**: 看完整错误栈，不要只看表面错误
6. **迭代验证**: 每个修改后立即验证效果

