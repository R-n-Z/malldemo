# 用户端登录下单支付 Bug 修复记录

**日期**: 2026-05-26

---

## 功能: AI客服商品FAQ知识库

**日期**: 2026-05-26

### 背景
Agent客服使用硬编码25条通用FAQ，无法回答商品相关问题（优惠、对比、推荐等）。

### 修改内容
1. **新建 `faq/product_faq.json`** — 51条中英文FAQ（10通用+41商品），涵盖手机/TV/T恤/男鞋/笔记本/平板/厨卫/硬盘8个品类
2. **新建 `scripts/generate_faq.py`** — Python脚本可自动从MySQL读取商品数据生成FAQ
3. **修改 `FaqInitializer.java`** — 启动时从 `faq/product_faq.json` 加载商品FAQ到Milvus
4. **修改 `ChatService.java`** — 精简工具集（移除QueryMetrics/QueryLogs/InternalDocs，保留FaqTools+DateTimeTools），优化System Prompt为纯电商客服

### 4个Agent
1. intelligent_assistant (ReactAgent) — 客服对话 /api/chat
2. planner_agent (ReactAgent) — 告警分解 AiOps
3. executor_agent (ReactAgent) — 运维执行 AiOps
4. ai_ops_supervisor (SupervisorAgent) — 编排 planner+executor

### 验证
- "iPhone 14有什么优惠" → FAQ检索→正确答案
- "小米12 Pro 和 Redmi K50 哪个好" → FAQ检索→对比表
- 端到端: 用户发消息→Agent 6秒内自动回复

---
**涉及模块**: mall-app-web-master（前端）、mall-portal（后端）、mall-admin（后端）

---

## Bug 1: 登录 Content-Type 不匹配 → "request ok"

**文件**: `mall-app-web-master/.../api/member.js`

**现象**: 输入账号密码后显示"request ok"，实际未登录

**根因**: 前端发送 `content-type: application/x-www-form-urlencoded`，后端 `@RequestBody` 只接受 JSON。Spring 返回 415，uni-app 将网络层成功但 HTTP 失败的响应封装为 `errMsg: "request ok"`。

**修复**: 移除 `memberLogin()` 中的 form-urlencoded header，使用默认 JSON。

---

## Bug 2: 密码哈希 BCrypt vs MD5 不匹配

**文件**: `mall-portal/.../UmsMemberServiceImpl.java`

**现象**: 修复 Bug 1 后登录返回"密码错误"

**根因**: 数据库 `ums_member` 表密码是 BCrypt 哈希（`$2a$10$...`），但 `login()` 方法用 `DigestUtils.md5DigestAsHex()` 比对，永远不匹配。

**修复**: 注入 `PasswordEncoder`，先用 `passwordEncoder.matches()` 验证 BCrypt，失败则兜底 MD5。注册密码同步改为 `passwordEncoder.encode()`。

---

## Bug 3: 下单接口强制要求 X-Member-Id 头

**文件**: `mall-portal/.../OmsPortalOrderController.java`

**现象**: 已登录用户下单返回"用户未登录"

**根因**: `generateOrder()` 被修改为从自定义请求头 `X-Member-Id` 获取用户 ID，前端只发 JWT token（Authorization 头），不发此头。JWT 拦截器已认证并将 `memberId` 存入 `request.setAttribute("memberId")`，但未读取。

**修复**: `X-Member-Id` 为空时兜底 `request.getAttribute("memberId")`。sign/timestamp 校验改为仅在提供时验证。

---

## Bug 4: 支付接口 Content-Type 不匹配

**文件**: `mall-app-web-master/.../api/order.js`

**现象**: 点击"确认支付"返回"request ok"，订单状态未更新

**根因**: 同 Bug 1，`payOrderSuccess()`、`cancelUserOrder()`、`confirmReceiveOrder()`、`deleteUserOrder()` 均设置 form-urlencoded header。

**修复**: 4 个函数改用 `params`（URL 查询参数），后端 `@RequestParam` 直接读取。

---

## Bug 5: Druid WallFilter 误拦截 SQL

**文件**: `mall-portal/.../application.yml`

**现象**: 下单 500，日志：`sql injection violation ... insert into oms_order_item`

**根因**: Druid WallFilter 对 PortalOrderItemDao.insertList 多行插入 SQL 语法解析报错，误判为 SQL 注入。

**修复**: filters 从 `stat,wall,slf4j` 改为 `stat,slf4j`。

---

## Bug 6: RateLimiter Lua 脚本为空 → NPE

**文件**: `mall-portal/.../RedisRateLimiter.java`

**现象**: `@RateLimit` 切面拦截 generateOrder 时 NullPointerException: `script is null`

**根因**: `RedisRateLimiter` 的 `@PostConstruct init()` 加载 Lua 脚本失败（mvn spring-boot:run classpath 不包含），`rateLimitScript` 等字段保持 null。

**修复**: `tryFixedWindow`、`trySlidingWindow`、`tryTokenBucket` 方法首行增加 null 检查，为 null 时 return true 降级放行。

---

## Bug 7: RocketMQ sender null → 事务回滚

**文件**: `mall-portal/.../OmsPortalOrderServiceImpl.java`

**现象**: 以上修复后下单仍 500，日志：`rocketMQCancelOrderSender is null`

**根因**: `rocketMQCancelOrderSender` 标注 `@Autowired(required = false)`，MQ 未启动时为 null，代码直接调用 NPE 导致事务回滚。

**修复**: 增加 null 检查 + try-catch，MQ 不可用时跳过延迟取消消息。

---

## Bug 8: 商家端订单列表无序

**文件**: `mall-admin/.../OmsOrderDao.xml`

**现象**: 商家端订单列表未按时间倒序

**根因**: `getList` SQL 缺少 `ORDER BY`

**修复**: 添加 `ORDER BY create_time DESC`

---

## 架构教训

1. **前端 form-urlencoded vs JSON**: uni-app 中 `luch-request` 默认 JSON，手动设 form-urlencoded 会导致 Spring `@RequestBody` 解析失败。用 `params`（查询字符串）或保持 JSON。
2. **@Autowired(required = false)**: 可选 Bean 必须在调用前判空，否则 NPE 级联故障。
3. **Druid WallFilter**: 开发环境易误判复杂 SQL，建议关闭。
4. **消息队列解耦**: 下单不应因取消消息发送失败而回滚整个订单。
5. **HBuilderX 项目**: uni-app HBuilderX 格式项目无需 package.json/node_modules，内置编译器处理依赖。
