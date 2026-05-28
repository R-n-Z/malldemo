# MALL 电商平台

## 项目简介

MALL 是一套完整的电商系统，包含商家管理后台、用户移动端商城、AI 客服系统。

- **后端**：Spring Boot 2.7.5 + MyBatis + MySQL 8.0 + Redis 7
- **商家端前端**：Vue 2.x + Element UI
- **用户端前端**：Uni-app（移动端 H5）
- **AI 客服**：Spring AI + DashScope + Milvus 向量知识库
- **数据库**：MySQL 8.0，MyBatis ORM
- **缓存**：Redis 7
- **文件存储**：MinIO（S3 兼容）

---

## 服务地址

### 基础设施（Docker）

| 服务 | 端口 | 账号/密码 |
|------|------|-----------|
| MySQL | `3306` | `root` / `root` |
| Redis | `6379` | 无密码 |
| MinIO Console | `9003` | `minioadmin` / `minioadmin` |
| Milvus | `19530` | 无 |
| Milvus Attu（管理UI） | `8000` | 无 |

### 后端服务

| 模块 | 端口 | 说明 |
|------|------|------|
| **mall-admin** | `8080` | 商家管理后台 API |
| **mall-portal** | `8085` | 用户端商城 API（含客服） |
| **super-biz-agent** | `9900` | AI 客服 Agent（Spring AI） |

### 前端应用

| 应用 | 端口 | 说明 |
|------|------|------|
| 商家管理后台 | `8888` | Vue 2.x 商家端 |
| 用户移动端 H5 | `8060` | Uni-app 商城 |

---

## 账号信息

| 端 | 账号 | 密码 |
|----|------|------|
| 商家端 | `admin` | `123456` |
| 用户端 | `test` | `123456` |

---

## 项目结构

```
malldemo-main/
├── mall-master/                          # 后端（Spring Boot 2.7.5 多模块）
│   ├── mall-common/                      # 公共模块
│   ├── mall-mbg/                         # MyBatis 生成的 DAO/实体
│   ├── mall-security/                    # JWT + Spring Security
│   ├── mall-admin/                       # 商家端 API (8080)
│   └── mall-portal/                      # 用户端 API (8085，含客服)
├── mall-admin-web-master/                # 商家端前端（Vue 2.x）
│   └── mall-admin-web-master/
├── mall-app-web-master/                  # 用户端前端（Uni-app）
│   └── mall-app-web-master/
├── super-biz-agent/                      # AI 客服 Agent（Spring AI + Milvus）
│   ├── vector-database.yml               #   Docker 编排（Milvus 向量库）
│   ├── faq/product_faq.json              #   商品FAQ知识库（150条）
│   └── scripts/generate_faq.py           #   FAQ自动生成脚本
├── docker-compose.yml                    # Docker 编排（MySQL/Redis/MinIO/ES/Mongo/RabbitMQ）
├── README.md
└── BUGFIX_RECORD.md                      # Bug修复记录
```

---

## 启动命令

### 方式一：一键启动

**Windows**：
```powershell
# 1. 启动所有基础服务
docker-compose up -d
cd super-biz-agent
docker-compose -f vector-database.yml up -d
cd ..

# 2. 启动所有后端
cd mall-master
start "Admin API" mvnw.cmd spring-boot:run -pl mall-admin -Dspring-boot.run.arguments=--spring.profiles.active=dev
start "Portal API" mvnw.cmd spring-boot:run -pl mall-portal -Dspring-boot.run.arguments=--spring.profiles.active=dev
cd ..

# 3. 构建并启动 Agent（首次或代码修改后需构建）
cd super-biz-agent
..\mall-master\mvnw.cmd clean package -DskipTests -f pom.xml
java -jar target\super-biz-agent-1.0-SNAPSHOT.jar
cd ..

# 4. 启动前端
cd mall-admin-web-master\mall-admin-web-master
start "Admin Web" cmd /c "npm run dev"
```

### 方式二：分步启动

#### 1. 基础服务（Docker）
```bash
# MySQL + Redis + MinIO + Elasticsearch + MongoDB + RabbitMQ
docker-compose up -d

# Milvus 向量数据库（AI 客服需要）
cd super-biz-agent
docker-compose -f vector-database.yml up -d
cd ..
```

#### 2. 商家端后端（8080）
```bash
cd mall-master
mvnw.cmd spring-boot:run -pl mall-admin -Dspring-boot.run.arguments=--spring.profiles.active=dev
```
- Swagger: http://localhost:8080/swagger-ui/index.html
- 登录API: `POST http://localhost:8080/admin/login`

#### 3. 用户端后端（8085）
```bash
cd mall-master
mvnw.cmd spring-boot:run -pl mall-portal -Dspring-boot.run.arguments=--spring.profiles.active=dev
```
- Swagger: http://localhost:8085/swagger-ui/index.html
- 登录API: `POST http://localhost:8085/sso/login`

#### 4. 商家端前端（8888）
```bash
cd mall-admin-web-master\mall-admin-web-master
npm install   # 首次运行
npm run dev
```
- 地址: http://localhost:8888
- 默认附带了一个后台代理服务，所有 `/admin/**` 请求都转发到 8080 端口

#### 5. 用户端前端（8060）
用 **HBuilder X** 打开 `mall-app-web-master\mall-app-web-master` 目录，点击 **运行 → 运行到浏览器 → Chrome**。
- 地址: http://localhost:8060
- 如无 HBuilder X，可用以下命令（需要 Node 18）：

```bash
cd mall-app-web-master\mall-app-web-master
# 首次运行需先创建 package.json 并 npm install（参考 CLAUDE.md）
npm run dev:h5
```

#### 6. AI 客服 Agent（9900）
```bash
cd super-biz-agent

# 首次或代码修改后需构建（使用 mall-master 下的 mvnw）
..\mall-master\mvnw.cmd clean package -DskipTests -f pom.xml

# 启动
java -jar target\super-biz-agent-1.0-SNAPSHOT.jar
```
- API: `POST http://localhost:9900/api/chat`
- 需要 Milvus（19530）和 DashScope API Key

#### 7. 重新生成商品 FAQ 知识库
```bash
cd super-biz-agent
python scripts\generate_faq.py
# FAQ JSON 生成在 faq/product_faq.json，Agent 启动时自动加载
```

---

## AI 客服模块

### 架构
```
用户端(Uni-app H5)
  │  POST /chat/send
  ▼
Portal API (8085) ──ChatServiceImpl──▶ AgentClient ──HTTP──▶ Agent (9900)
  │                                                              │
  │  WebSocket / 轮询                                  FaqTools.searchFaq()
  ▼                                                              ▼
用户端  ◀── AI客服回复 ──── 异步 ────   Agent ◀── Milvus FAQ 知识库
                                                  (150条商品 Q&A)
```

### 核心流程
1. 用户在商品详情页点击"客服"→ 创建会话（绑定 productId）
2. 用户发送消息 → Portal 异步调用 Agent
3. Agent 使用 `searchFaq` 工具查询 Milvus FAQ 知识库
4. 匹配到答案 → AI客服自动回复（带 `[自动回复]` 标识）
5. 匹配不到 → 返回"您的问题已转接人工客服"
6. 用户说"这个""它"等指代词 → Agent 根据会话绑定的商品名自动关联

### 客服 API

| 接口 | 方法 | 说明 |
|------|------|------|
| `/chat/session/create` | POST | 创建/获取会话（需登录，绑定 productId） |
| `/chat/send` | POST | 用户发送消息（触发 AI 自动回复） |
| `/chat/messages/{sessionId}` | GET | 获取消息历史 |
| `/chat/sessions` | GET | 商家端查看所有会话 |
| `/chat/session/take/{sessionId}` | POST | 商家接管会话 |
| `/chat/session/close/{sessionId}` | POST | 关闭会话 |

### Agent API

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/chat` | POST | 对话（支持 productName 上下文） |
| `/api/chat_stream` | POST | 流式对话（SSE） |
| `/api/chat/session/{sessionId}` | GET | 查询会话信息 |

### 项目中的 4 个 Agent

| # | 名称 | 类型 | 用途 |
|---|------|------|------|
| 1 | intelligent_assistant | ReactAgent | 客服对话 `/api/chat` |
| 2 | planner_agent | ReactAgent | 告警分解（AiOps） |
| 3 | executor_agent | ReactAgent | 运维执行（AiOps） |
| 4 | ai_ops_supervisor | SupervisorAgent | 编排 planner+executor |

### FAQ 知识库
- **条目数**：150 条（10 通用 + 140 商品）
- **覆盖品类**：手机通讯(56)、T恤(20)、电视(14)、男鞋(14)、厨卫/平板/硬盘/笔记本(各7)、品牌(8)
- **每商品覆盖**：价格、优惠、性价比、库存、购买、赠品、适用人群、送礼、特点、配置、质量、品牌、正品、对比、规格选配、保修、发货
- **生成脚本**：`super-biz-agent/scripts/generate_faq.py`（从 MySQL 自动生成）
- **存储**：Milvus `faq` collection（1024 维向量）

### 用户端 API 白名单
以下路径无需登录即可访问：
`/sso/login` `/sso/register` `/sso/getAuthCode` `/home/**` `/product/**` `/brand/**` `/alipay/**` `/ws/**` `/swagger-ui/**`

---

## 数据库

- **Schema**：`mall`
- **初始化脚本**：根目录 `mall.sql`（Docker 启动时自动导入）
- **客服表**：`chat_session`、`chat_message`

---

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Spring Boot 2.7.5 |
| 安全认证 | Spring Security + JWT |
| ORM | MyBatis + PageHelper |
| 数据库 | MySQL 8.0 + Druid 连接池 |
| 缓存 | Redis 7（Lettuce） |
| 对象存储 | MinIO |
| 商家端前端 | Vue 2.x + Element UI |
| 用户端前端 | Uni-app（Vue 2.x） |
| AI 客服 | Spring AI Alibaba + DashScope (qwen3-max) |
| 向量数据库 | Milvus 2.5.10 |
| Embedding | DashScope text-embedding-v4 (1024维) |
| API 文档 | Springfox Swagger 2 |
| 构建工具 | Maven 3.9 |
