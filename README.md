# MALL 电商平台

## 项目简介

MALL 是一套完整的电商系统，包含商家管理后台、用户移动端商城、客服系统。

- **后端**：Spring Boot 2.7.5 + MyBatis + MySQL 8.0 + Redis 7
- **商家端前端**：Vue 2.x + Element UI
- **用户端前端**：Uni-app（移动端）
- **数据库**：MySQL 8.0，MyBatis ORM
- **缓存**：Redis 7
- **文件存储**：MinIO（S3 兼容）
- **消息中间件**：支持 Kafka / RocketMQ / RabbitMQ（可选）

---

## 服务地址

### 基础设施

| 服务 | 地址 | 账号 / 密码 |
|------|------|-------------|
| MySQL | `localhost:3306` | `root` / `root` |
| Redis | `localhost:6379` | 无密码 |
| MinIO Console | `http://localhost:9003` | `minioadmin` / `minioadmin` |
| MinIO API | `http://localhost:9002` | 同上 |

### 后端服务

| 模块 | 端口 | Swagger 文档 | 说明 |
|------|------|-------------|------|
| mall-admin | `8080` | http://localhost:8080/swagger-ui/index.html | 商家管理后台 API |
| mall-portal | `8085` | http://localhost:8085/swagger-ui/index.html | 用户端商城 API |

### 前端应用

| 应用 | 地址 | 说明 |
|------|------|------|
| 商家管理后台 | `http://localhost:8888` | Vue 2.x 管理界面 |
| 用户移动端 | `http://localhost:8060` | Uni-app 商城（需 HBuilder X 运行） |

---

## 账号信息

### 商家端

| 账号 | 密码 | 说明 |
|------|------|------|
| `admin` | `123456` | 管理员 |

### 用户端

| 账号 | 密码 | 说明 |
|------|------|------|
| `test` | `123456` | 测试用户 |
| 自行注册 | — | 在移动端注册页注册 |

---

## 项目结构

```
malldemo-main/
├── mall-master/                          # 后端（Spring Boot 多模块）
│   ├── mall-common/                      # 公共模块（工具类、响应封装、切面）
│   ├── mall-mbg/                         # MyBatis Generator 生成的 DAO 和实体
│   ├── mall-security/                    # Spring Security + JWT 认证模块
│   ├── mall-admin/                       # 商家管理后台 API（端口 8080）
│   ├── mall-portal/                      # 用户端商城 API（端口 8085，含客服模块）
│   ├── mall-search/                      # 搜索服务（Elasticsearch / 可选）
│   └── mall-demo/                        # 示例代码
├── mall-admin-web-master/                # 商家端前端（Vue 2.x + Element UI）
│   └── mall-admin-web-master/
├── mall-app-web-master/                  # 用户端前端（Uni-app 移动端）
│   └── mall-app-web-master/
├── docker-compose.yml                    # Docker 编排（MySQL、Redis、MinIO）
├── start.bat                             # Windows 一键启动脚本
├── start.sh                              # Linux / Git Bash 一键启动脚本
└── README.md                             # 本文件
```

---

## 功能清单

### 用户端（移动端商城）

- [x] 用户注册 / 登录
- [x] 首页商品展示、分类浏览
- [x] 商品搜索、筛选、排序
- [x] 商品详情页（规格选择、优惠券、评价、品牌信息）
- [x] 购物车
- [x] 订单创建与管理
- [x] 会员中心（收藏、地址、优惠券）
- [x] **客服咨询**（与商家端实时对话）
- [x] 秒杀活动
- [x] 支付宝支付（沙箱环境 / 可选）

### 商家端（管理后台）

- [x] 仪表盘
- [x] 商品管理（分类、品牌、属性、SKU）
- [x] 订单管理
- [x] 营销管理（优惠券、秒杀、广告、专题）
- [x] 会员管理
- [x] 权限管理（角色、菜单、资源）
- [x] **客服工作台**（与用户端实时对话）

---

## 快速启动

### 前提条件

- Docker Desktop（运行中）
- Java 17+
- Maven 3.6+
- Node.js 18+

### 命令行启动

**Windows（PowerShell）**：
```powershell
.\start.bat
```

**Git Bash / Linux**：
```bash
bash start.sh
```

### 手动启动（多终端）

**终端 1 — 基础服务**：
```powershell
# 启动 MySQL、Redis、MinIO
docker-compose up -d
```

**终端 2 — 商家端后端（8080）**：
```powershell
cd mall-master
mvn spring-boot:run -pl mall-admin
```

**终端 3 — 商家端前端（8888）**：
```powershell
cd mall-admin-web-master\mall-admin-web-master
npm install
npm run dev
```

**终端 4 — 用户端后端 8085（可选）**：
```powershell
cd mall-master
mvn spring-boot:run -pl mall-portal
```

### 前端配置说明

`config/dev.env.js` 中 `BASE_API` 需指向后端地址 `http://localhost:8080`：
```javascript
// config/dev.env.js
BASE_API: '"http://localhost:8080"'
```
若改为其他地址，需同步修改 `config/index.js` 的 `proxyTable` 代理规则，否则前端 API 请求将返回 404。

---

## 客服模块

### 架构

```
用户端(Uni-app) ──HTTP/WebSocket──▶ Portal API (8085) ──DB──▶ Admin API (8080) ◀── 商家端(Vue)
```

- 用户端入口：商品详情页底部「客服」按钮
- 商家端入口：`http://localhost:8080/chat/sessions`（API，前端页面待完善）
- 消息通过 HTTP 发送，3 秒轮询拉取新消息
- 支持 WebSocket（`ws://localhost:8085/ws`，STOMP 协议，可选升级）

### 客服 API

| 接口 | 方法 | 说明 |
|------|------|------|
| `/chat/session/create` | POST | 用户创建/获取会话（需登录） |
| `/chat/messages/{sessionId}` | GET | 获取消息历史 |
| `/chat/send` | POST | 发送消息 |
| `/chat/sessions` | GET | 商家查看所有待处理会话 |
| `/chat/session/take/{sessionId}` | POST | 商家接管会话 |
| `/chat/session/close/{sessionId}` | POST | 关闭会话 |

---

## 用户端 API 白名单

以下路径无需登录即可访问：

| 路径 | 说明 |
|------|------|
| `/sso/login` | 登录 |
| `/sso/register` | 注册 |
| `/sso/getAuthCode` | 获取短信验证码 |
| `/home/**` | 首页内容 |
| `/product/**` | 商品浏览 |
| `/brand/**` | 品牌浏览 |
| `/alipay/**` | 支付宝回调 |
| `/ws/**` | WebSocket 连接 |
| `/swagger-ui/**` | API 文档 |

---

## 数据库

- **Schema**：`mall`
- **初始化脚本**：根目录 `mall.sql`（Docker 启动时自动导入）
- **客服表**：`chat_session`、`chat_message`（新增）

---

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Spring Boot 2.7.5 |
| 安全认证 | Spring Security + JWT（jjwt 0.12.5） |
| ORM | MyBatis + PageHelper |
| 数据库 | MySQL 8.0 + Druid 连接池 |
| 缓存 | Redis 7（Lettuce 客户端） |
| 对象存储 | MinIO |
| 消息队列 | RabbitMQ / Kafka / RocketMQ（可选） |
| 即时通讯 | Spring WebSocket + STOMP |
| 商家端前端 | Vue 2.x + Element UI + Vuex + Vue Router |
| 用户端前端 | Uni-app（Vue 2.x 兼容） |
| API 文档 | Springfox Swagger 2 |
| 构建工具 | Maven 3.9 |
