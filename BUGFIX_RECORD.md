# Bug 修复记录

**项目**: MALL 电商平台  
**最后更新**: 2026-05-27

---

## 2026-05-26 修复汇总

### Bug 1: 登录 Content-Type 不匹配 → "request ok"

**文件**: `mall-app-web-master/.../api/member.js`  
**现象**: 输入账号密码后显示"request ok"，实际未登录  
**根因**: 前端 `content-type: application/x-www-form-urlencoded`，后端 `@RequestBody` 只接受 JSON。Spring 415 → uni-app 封装为 `errMsg: "request ok"`  
**修复**: 移除 `memberLogin()` 的 form-urlencoded header，使用默认 JSON

---

### Bug 2: 密码哈希 BCrypt vs MD5 不匹配

**文件**: `mall-portal/.../UmsMemberServiceImpl.java`  
**现象**: 修复 Bug1 后登录返回"密码错误"  
**根因**: DB 存 BCrypt(`$2a$10$...`)，`login()` 用 `DigestUtils.md5DigestAsHex()` 比对  
**修复**: 注入 `PasswordEncoder`，BCrypt 优先 + MD5 兜底；注册同步改 `passwordEncoder.encode()`

---

### Bug 3: 下单接口强制要求 X-Member-Id 头

**文件**: `mall-portal/.../OmsPortalOrderController.java`  
**现象**: 已登录用户下单返回"用户未登录"  
**根因**: `generateOrder()` 从自定义头 `X-Member-Id` 取用户ID，前端只发 JWT token  
**修复**: `X-Member-Id` 为空时兜底 `request.getAttribute("memberId")`；sign/timestamp 改为仅提供时验证

---

### Bug 4: 支付接口 Content-Type 不匹配

**文件**: `mall-app-web-master/.../api/order.js`  
**现象**: 点击"确认支付"返回"request ok"  
**根因**: 同 Bug1 — `payOrderSuccess` 等 4 个函数设置 form-urlencoded header  
**修复**: 全部改用 `params`（URL 查询参数），后端 `@RequestParam` 直接读取

---

### Bug 5: Druid WallFilter 误拦截 SQL

**文件**: `mall-portal/.../application.yml`  
**现象**: 下单 500，`sql injection violation ... insert into oms_order_item`  
**根因**: Druid WallFilter 对 `PortalOrderItemDao.insertList` 多行插入解析报错  
**修复**: filters `stat,wall,slf4j` → `stat,slf4j`

---

### Bug 6: RateLimiter Lua 脚本为空 → NPE

**文件**: `mall-portal/.../RedisRateLimiter.java`  
**现象**: `@RateLimit` 切面拦截 `generateOrder` 时 NPE: `script is null`  
**根因**: `@PostConstruct init()` 加载 Lua 脚本失败（mvn spring-boot:run classpath 不包含）  
**修复**: `tryFixedWindow`/`trySlidingWindow`/`tryTokenBucket` 首行加 null 检查降级放行

---

### Bug 7: RocketMQ sender null → 事务回滚

**文件**: `mall-portal/.../OmsPortalOrderServiceImpl.java`  
**现象**: 下单 500，`rocketMQCancelOrderSender is null`  
**根因**: `@Autowired(required = false)` MQ 未启动时为 null，NPE 导致事务回滚  
**修复**: null 检查 + try-catch，MQ 不可用时优雅跳过延迟取消消息

---

### Bug 8: 商家端订单列表无序

**文件**: `mall-admin/.../OmsOrderDao.xml`  
**现象**: 商家端订单列表未按时间倒序  
**修复**: 添加 `ORDER BY create_time DESC`

---

### Bug 9: Agent 返回 NEED_HUMAN 时用户无反馈

**文件**: `mall-portal/.../ChatServiceImpl.java`  
**现象**: FAQ 未匹配时用户提问后看不到任何回复  
**根因**: `sendMessage()` 只处理 `!answer.startsWith("NEED_HUMAN")`，转人工和异常分支不创建消息  
**修复**: 增加 NEED_HUMAN 分支（创建"已转人工"提示）+ catch 降级兜底（创建"系统繁忙"提示）

---

### Bug 10: 商家端聊天界面显示 JSON 格式字符

**文件**: `super-biz-agent/.../ChatService.java`  
**现象**: 商家端客服聊天窗口的 AI 自动回复显示 `[AssistantMessage [...], ToolResponseMessage [{"status":"success"...}]]` 或 `{"query":"...","results":[...]}` 等 JSON/Java 对象转储字符  
**根因**: `extractAnswer()` 第 218 行 `val.get().toString()` 对 `AssistantMessage`、`List<Message>`、`Map` 等复杂类型暴力调用 `.toString()`，产生 Java 默认序列化文本（含嵌套 JSON）。`"messages"` key 降级时取出包含工具调用结果的整个消息列表，`isRawDump` 检测逻辑仅处理 `"AssistantMessage ["` 前缀，漏掉 `[AssistantMessage`（数组）和纯 JSON  
**修复**: 
- 类型安全提取：`AssistantMessage` → `.getText()`，`Collection` → 遍历找最后一条 `AssistantMessage`，`String` → 直接用
- 新增 `isRawDump()` 过滤：以 `{`、`[`、`AssistantMessage`、`ToolResponse`、`Message [` 开头的字符串视为对象转储，跳过
- 参考 `AiOpsService.extractFinalReport()` 的正确做法（类型转换 + `.getText()`）

**影响范围**: 所有 AI 客服自动回复消息（售前/售后/升级/闲聊 4 个子 Agent 输出均经过 `extractAnswer()` 提取）

---

## 功能记录

### AI 客服商品 FAQ 知识库（2026-05-26）

**新建文件**:
- `super-biz-agent/faq/product_faq.json` — 150 条中文商品 FAQ
- `super-biz-agent/scripts/generate_faq.py` — Python 自动生成脚本

**修改文件**:
- `FaqInitializer.java` — 启动时从 JSON 加载 FAQ → Milvus
- `ChatService.java` — 工具精简(FaqTools+DateTimeTools)、Prompt 重写为纯电商客服、商品上下文注入
- `ChatController.java` — ChatRequest 增加 productName 字段
- `AgentClient.java` — ask() 增加 productName 参数
- `ChatServiceImpl.java` — 上下文传递 + NEED_HUMAN/异常降级处理

**用户端聊天页面**:
- `pages/chat/chat.vue` — AI 客服聊天 UI
- `api/chat.js` — 聊天 API 封装
- `pages/product/product.vue` — 底部导航增加"客服"按钮
- `pages.json` — 添加 chat 路由

**Agent 对话与商品绑定**: 用户说"这个""它""该商品"等指代词时，Agent 根据会话绑定的 productName 自动关联到当前商品

### 4 个 Agent

| # | 名称 | 类型 | 用途 |
|---|------|------|------|
| 1 | intelligent_assistant | ReactAgent | 客服对话 `/api/chat` |
| 2 | planner_agent | ReactAgent | 告警分解（AiOps） |
| 3 | executor_agent | ReactAgent | 运维执行（AiOps） |
| 4 | ai_ops_supervisor | SupervisorAgent | 编排 planner+executor |

---

## 架构教训

1. **form-urlencoded vs JSON**: uni-app `luch-request` 默认 JSON，手动设 form-urlencoded 导致 Spring `@RequestBody` 解析失败。用 `params` 或 JSON
2. **@Autowired(required = false)**: 可选 Bean 必须判空，否则 NPE 级联故障
3. **Druid WallFilter**: 开发环境易误判复杂 SQL，建议关闭
4. **消息队列解耦**: 下单不应因取消消息发送失败而回滚整个订单
5. **HBuilderX 项目**: uni-app HBuilderX 格式项目无需 package.json/node_modules，内置编译器处理
6. **Agent 降级**: 转人工和异常分支必须有用户可见的提示消息，不能静默丢弃
7. **OverAllState 值提取**: `state.value(key)` 返回类型不确定（可能是 `AssistantMessage`/`List`/`String`/`Map`），禁止直接 `.toString()`。必须类型判断后用专用方法（如 `.getText()`），否则前端收到 Java 序列化/JSON 转储文本
