# Bug 修复记录

**项目**: MALL 电商平台  
**最后更新**: 2026-05-27

---

## 2026-05-27 修复汇总

### Bug 1: 智能客服 Agent 不可用 → "Agent服务暂不可用"

**文件**: `super-biz-agent` 服务（独立进程，端口 9900）、`mall-portal/.../ChatServiceImpl.java`  
**现象**: 用户发送消息后返回 "NEED_HUMAN: Agent服务暂不可用"  
**根因**: super-biz-agent 是独立 Spring Boot 服务，不会随 mall-admin(8080) 或 mall-portal(8085) 自动启动。AgentClient 请求 `http://localhost:9900/api/chat` 时连接被拒绝  
**排查**: `netstat -ano | findstr :9900` 无监听 → 确认 agent 未启动  
**修复**: `java -jar super-biz-agent-1.0-SNAPSHOT.jar` 手动启动 agent，端口 9900 恢复监听

**复盘**: 
- docker-compose.yml 不包含 super-biz-agent 容器，IDE 也不自动启动它
- 建议：在 docker-compose 中添加 agent 服务，或编写启动脚本统一管理

---

### Bug 2: return 包编译报错 → package 关键字冲突 + 文件残留

**文件**: `super-biz-agent/.../agent/tool/return/*.java`（4 个 tool 类 + service + controller）  
**现象**: IDE 中 `org.example.agent.tool.return` 包下全部文件报红，编译失败 `需要<标识符>`  
**根因**:  
1. **`return` 是 Java 关键字**：包名 `tool.return` 中 `return` 在部分 javac 版本下被误判为语句关键字，编译失败  
2. **源文件残留**：`tool/ReturnAuditTools.java`（旧位置，package 声明不匹配）与新位置 `tool/audit/ReturnAuditTools.java` 同时存在，Spring `@ComponentScan` 发现同名 Bean → `ConflictingBeanDefinitionException`  
3. **文件意外丢失**：`ReturnAuditTools.java` 创建后又消失，`ReturnAgentService` 中 `@Autowired` 找不到依赖类

**修复**:
- 包名 `tool.return` → `tool.audit`（避免 Java 关键字）
- 删除残留旧文件 `tool/ReturnAuditTools.java`
- 补全丢失文件 `ReturnAuditTools.java`
- 更新 `ReturnAgentService.java` 中 import 引用

**复盘**:
- 命名包/类时避免 Java 保留字（`return`、`class`、`new` 等）
- 移动/重命名包时应确保旧路径文件完全删除
- 运行中的 JAR 会锁定 target 目录，需先 `taskkill` 再 `mvn clean`

---

### Bug 3: Milvus FAQ 集合加载失败 → 65535 collection not found

**文件**: `super-biz-agent/.../service/FaqInitializer.java`  
**现象**: 启动日志 `LoadCollectionRequest collectionName:faq failed, error code: 65535, collection not found`  
**根因**: `FaqInitializer.init()` 每次启动都 `dropCollection("faq")` + 立即重建。Milvus 的 `dropCollection` 是**异步操作**，删除尚未完全生效时 `loadCollection` 已执行，导致 65535  
**连锁影响**: 每次启动 150 条 FAQ 重新向量化 → 启动耗时 ~100 秒  
**修复**: 已存在集合跳过删除/重建，只调用 `loadCollection` 确保加载到内存。启动时间从 ~100 秒降至 **~5 秒**

**复盘**:
- 分布式/异步系统（如 Milvus）中，删除操作后不能假设结果立即可见
- 初始化逻辑应设计为幂等：已存在的资源直接复用，避免 destroy + recreate
- 每次启动重新向量化 150 条数据是对 DashScope API 配额和启动时间的浪费

---

## 2026-05-27 功能增强：会话上下文绑定

### 改动背景

Agent 对话链中缺失关键上下文：agent 不知道**哪个用户**在问**哪个商品**。
- 只有 `productName` 字符串，无 `productId`
- 无 `userId`/`memberId`，agent 无法个性化
- 客服会话历史仅存 agent 内存，重启即丢失
- 商家端回复完全绕过 agent

### 改动清单

**super-biz-agent（新增 3，修改 2）**

| 文件 | 改动 |
|------|------|
| `dto/ContextEnvelope.java` | **新增**：统一上下文对象（UserInfo + ProductInfo + MessageInfo + HistoryMessage），替代零散 Map 传参 |
| `controller/ChatController.java` | **修改**：`/api/chat` 同时兼容旧 ChatRequest 和新 ContextEnvelope；新增 `/api/chat/context/sync` 端点供商家端同步；`SessionInfo` 新增 `replaceHistory`/`appendSingle` |
| `service/ChatService.java` | **修改**：`createSupervisorAgent` 新增 `contextSummary` 参数；4 个子 Agent（售前/售后/升级/闲聊）+ Supervisor 的 system prompt 全部注入 `userId/productId/conversationId` |

**mall-portal（修改 3）**

| 文件 | 改动 |
|------|------|
| `dao/ChatDao.java` + `ChatDao.xml` | **修改**：新增 `getRecentMessages(sessionId, limit)` 查询 DB 中最近消息 |
| `component/AgentClient.java` | **重写**：`ask()` 方法改为构建完整 ContextEnvelope（userId/productId/productPic/history），同时保留旧字段兼容 |
| `service/impl/ChatServiceImpl.java` | **修改**：`sendMessage()` 从 session 提取 memberId/productId/productPic；新增 `buildHistory()` 从 DB 加载消息历史传给 AgentClient |

**mall-admin（修改 1）**

| 文件 | 改动 |
|------|------|
| `controller/ChatAdminController.java` | **修改**：商家回复后异步 `POST /api/chat/context/sync` 通知 agent 同步上下文 |
| `application.yml` | **修改**：新增 `agent.url` 配置项 |

### 核心设计决策

| 决策 | 理由 |
|------|------|
| 用一个 ContextEnvelope 替代零散字段 | 扩展性好，新增字段不破坏现有接口；一次传递全部所需信息 |
| 历史从 DB 传入而非依赖 agent 内存 | agent 重启不丢上下文；商家端回复不会导致 agent 视图不同步 |
| 所有子 Agent 统一注入 userId+productId | 修复了 post_sales/escalation/chitchat 缺少产品上下文的问题 |
| 商家端异步通知而非同步阻塞 | 不增加商家回复延迟，agent 上下文最终一致即可 |
| 兼容旧格式 | 旧 ChatRequest 仍可正常工作，平滑迁移 |

### 经验总结

1. **上下文传递要"一次给够"**：不要在调用链的每个环节逐个补字段，设计一个统一上下文对象，所有调用方都使用同一结构。零散的 Map 传参维护成本高，字段容易遗漏。

2. **前端传 ID，后端补上下文**：前端只传 productId 即可，后端从 DB 查商品详情、从 session 查用户信息，然后构建完整 ContextEnvelope 发给 agent。前端不应该承担构造上下文的职责。

3. **agent 内存状态不可信**：会话历史必须从 DB（source of truth）加载，agent 内存只是缓存。商家端的回复必须同步回 agent，否则 agent 的对话视图与用户实际体验脱节。

4. **子 Agent 要平等对待**：SupervisorAgent 的每个子 Agent 都应该收到完整的上下文信息。如果只给 pre_sales 注入 productName 而 post_sales 没有，当 supervisor 路由到 post_sales 时会导致上下文丢失。

5. **向后兼容很重要**：ContextEnvelope 是新格式，但旧 ChatRequest 格式的调用方（如 `/chat_stream` SSE 端点）仍需保持工作。采用 `@RequestBody String` + 手动解析的方案，根据请求体是否包含 `user`/`message` 字段自动判断格式。

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

---

## 2026-05-27 功能更新

### 接入退货审核规则到客服对话

**修改文件**: `ChatService.java`  
**内容**: post_sales_agent 新增 `ReturnRuleKnowledgeTools` + `DateTimeCalculateTools`，用户问"能不能退"时可实时查询：
- `queryStrictRejectRules` — 检查商品是否属于不可退货品类（生鲜/数字商品/内衣等）
- `queryAutoApproveRules` — 查询自动通过退货的条件
- `queryReceiptThresholds` — 7天/15天/30天退货窗口
- `calculateReceiveDays` — 根据收货时间计算处于哪个窗口
- `checkEscalationKeywords` — 检测投诉/12315/律师等敏感词自动升级

**退货审核Agent架构**（`ReturnAgentService`）: Planner-Executor-Supervisor 三体模式，9个工具，独立 `POST /api/return/audit` API，供商家后台调用
