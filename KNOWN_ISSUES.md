# MALL 项目已知问题清单

**最后更新**: 2026-04-23  
**优先级分布**: 🔴 高 3个 | 🟡 中 2个 | 🟢 低 3个

---

## 🔴 高优先级问题（需立即修复）

### 问题 A: 登陆认证失败

**问题ID**: MALL-AUTH-001  
**严重程度**: 🔴 阻塞型（无法登陆）  
**发现时间**: 2026-04-23 21:08  
**当前状态**: 🔍 诊断中  

**问题描述**:
```
POST /admin/login 返回 500，错误信息: "密码不正确"
即使输入正确的账号密码 (admin/123456) 也无法登陆
```

**表现**:
- ✅ 数据库中有 admin 用户
- ✅ 密码字段有数据
- ❌ 密码验证总是失败

**影响范围**:
- 前端管理系统无法使用
- 无法进行管理操作

**可能原因**:
1. SQL 中预设的密码哈希值不是 123456
2. BCrypt 验证逻辑有 bug
3. 密码字段在导入时被损坏
4. 密码加密/解密方式不匹配

**诊断步骤**:
```bash
# 1. 查看后端完整错误日志
docker-compose logs mall-admin | grep -A 20 "admin"

# 2. 查看数据库中的密码哈希
docker-compose exec -T mysql mysql -uroot -proot mall \
  -e "SELECT username, password FROM ums_admin WHERE username='admin';"

# 3. 用 Java 验证 BCrypt 哈希值是否正确
# (需要写一个小程序或查看源代码中的验证逻辑)

# 4. 尝试用其他用户登陆
curl -X POST http://localhost:8080/admin/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"123456"}'
```

**待办项**:
- [ ] 获取完整的登陆异常栈
- [ ] 查看 UmsAdminController 的登陆实现
- [ ] 检查 BCryptPasswordEncoder 的配置
- [ ] 生成正确的 123456 对应的 BCrypt 哈希值
- [ ] 更新数据库密码并重新测试

---

### 问题 B: Swagger API 文档无法访问

**问题ID**: MALL-UI-002  
**严重程度**: 🔴 影响调试  
**发现时间**: 2026-04-23 20:52  
**当前状态**: 🔍 未诊断  

**问题描述**:
```
http://localhost:8080/swagger-ui.html 无法打开
返回 404 或空白页
```

**影响范围**:
- 无法使用 Swagger UI 进行 API 测试
- 开发人员无法查看 API 文档

**可能原因**:
1. Swagger 依赖缺失
2. Swagger 路由被拦截
3. Spring Security 配置问题
4. Swagger 资源路径配置错误

**诊断步骤**:
```bash
# 1. 检查后端日志
docker-compose logs mall-admin | grep -i swagger

# 2. 直接访问 API
curl -v http://localhost:8080/swagger-ui.html

# 3. 检查 POM 中的 Swagger 依赖
grep -A 2 "springfox" /mnt/d/develop/program/MALL/mall-master/pom.xml
```

**待办项**:
- [ ] 诊断 Swagger 无法访问的原因
- [ ] 检查安全配置中的白名单
- [ ] 确认 Swagger 依赖版本

---

### 问题 C: 前端 npm install 非常慢

**问题ID**: MALL-ENV-003  
**严重程度**: 🔴 开发效率低  
**发现时间**: 2026-04-23 20:55  
**当前状态**: ✅ 已有解决方案  

**问题描述**:
```
npm install 可能需要 30+ 分钟
npm 源指向国外官方源
```

**快速解决**:
```bash
npm config set registry https://registry.npmmirror.com
npm cache clean --force
rm -rf node_modules package-lock.json
npm install
```

**待办项**:
- [ ] 所有开发人员切换到国内镜像源

---

## 🟡 中优先级问题（应尽快修复）

### 问题 D: 前端打包体积过大

**问题ID**: MALL-PERF-004  
**严重程度**: 🟡 性能问题  
**发现时间**: 2026-04-23  
**当前状态**: 📋 未开始  

**问题描述**:
- node_modules 大小：249MB
- 未配置 tree-shaking
- 可能有重复依赖

**影响范围**:
- 构建速度慢
- 部署体积大
- 首屏加载慢

**改进方案**:
1. 分析并移除未使用的依赖
2. 启用 tree-shaking
3. 按需加载组件库
4. 配置 gzip 压缩

**待办项**:
- [ ] 运行 npm audit 找出依赖问题
- [ ] 配置 webpack 优化
- [ ] 分析首屏加载时间

---

### 问题 E: 数据库连接参数不完善

**问题ID**: MALL-DB-005  
**严重程度**: 🟡 安全和可靠性  
**发现时间**: 2026-04-23  
**当前状态**: 📋 未开始  

**问题描述**:
- MySQL 连接超时时间默认
- 连接池参数（初始化大小、最大连接数）可能不合理
- 没有重试机制

**影响范围**:
- 高并发下可能连接耗尽
- 网络波动时可能频繁断连

**改进方案**:
```yaml
# application-docker.yml
druid:
  initial-size: 10
  min-idle: 20
  max-active: 50
  max-wait: 60000
  test-on-borrow: true
  validation-query: "SELECT 1"
```

**待办项**:
- [ ] 调整连接池参数
- [ ] 添加连接健康检查
- [ ] 配置失败重试

---

## 🟢 低优先级问题（可选优化）

### 问题 F: Docker Compose 版本提示

**问题ID**: MALL-DEV-006  
**严重程度**: 🟢 仅是警告  
**发现时间**: 2026-04-23  
**当前状态**: 📋 未开始  

**问题描述**:
```
WARN[0000] /mnt/d/develop/program/MALL/docker-compose.yml: the attribute `version` is obsolete
```

**解决方案**:
从 docker-compose.yml 中删除 `version: '3.8'` 行

**影响**: 仅是警告，不影响功能

**待办项**:
- [ ] 更新 docker-compose.yml

---

### 问题 G: 缺少生产环境配置

**问题ID**: MALL-OPS-007  
**严重程度**: 🟢 不影响开发  
**发现时间**: 2026-04-23  
**当前状态**: 📋 未开始  

**问题描述**:
- application-prod.yml 配置不完善
- 没有环境变量示例
- 缺少生产部署文档

**改进方案**:
1. 完善 application-prod.yml
2. 编写 .env.example 示例
3. 编写生产部署指南

**待办项**:
- [ ] 创建 .env.example
- [ ] 更新 application-prod.yml
- [ ] 编写部署文档

---

### 问题 H: 缺少错误处理和验证

**问题ID**: MALL-CODE-008  
**严重程度**: 🟢 代码质量  
**发现时间**: 2026-04-23  
**当前状态**: 📋 未开始  

**问题描述**:
- 部分接口缺少输入验证
- 错误提示信息不统一
- 缺少业务异常处理

**改进方案**:
1. 添加 @Validated 和 @NotNull 等注解
2. 创建统一的异常处理器
3. 标准化错误响应格式

**待办项**:
- [ ] 审查现有验证逻辑
- [ ] 完善错误处理
- [ ] 更新 API 文档

---

## 📋 问题优先级矩阵

```
          │ 影响范围大
          │
   高优先 │ ★ 问题 A（登陆失败）
   级别   │ ★ 问题 B（Swagger）
   ▲     │ ★ 问题 C（npm 慢）
   │     │
   │     │ ◆ 问题 D（包体积）
   │     │ ◆ 问题 E（DB 参数）
   │     │
   │     │ ◇ 问题 F（Docker）
   │     │ ◇ 问题 G（Prod 配置）
   │     │ ◇ 问题 H（错误处理）
   └─────┴──────────────────────▶
         影响范围小    影响范围大
```

---

## 🔧 按优先级处理计划

### 第 1 阶段（立即处理）
```
问题 A: 登陆认证 - 无此功能无法测试
问题 B: Swagger UI - 影响开发效率  
问题 C: npm 慢 - 影响开发体验
```

### 第 2 阶段（1 周内）
```
问题 D: 包体积优化
问题 E: 数据库参数
问题 F: Docker 版本
```

### 第 3 阶段（2 周内）
```
问题 G: 生产配置
问题 H: 错误处理
```

---

## 📝 问题追踪模板

```markdown
**问题ID**: MALL-XXX-NNN
**标题**: [问题简短描述]
**严重程度**: 🔴 / 🟡 / 🟢
**发现日期**: YYYY-MM-DD
**发现人**: [名字]
**状态**: 📋 未开始 / 🔍 诊断中 / 🛠 处理中 / ✅ 已解决

**问题描述**:
[详细描述]

**影响范围**:
- [影响项 1]
- [影响项 2]

**根本原因**:
[分析根本原因]

**解决方案**:
[提出解决方案]

**验证方法**:
[如何验证修复成功]

**预期完成时间**: [日期]
**负责人**: [名字]
```

---

**最后更新**: 2026-04-23 21:20  
**下一次审查**: 2026-04-30

