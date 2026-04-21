# Nacos配置中心部署

## 架构说明

```
┌─────────────────────────────────────────────────────────────┐
│                      应用服务                                │
│  mall-gateway / mall-order / mall-seckill / mall-stock      │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                    Nacos Config                              │
│  - 配置管理                                                   │
│  - 配置热更新                                                 │
│  - 配置隔离（namespace/group）                                │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                    Nacos Discovery                           │
│  - 服务注册                                                   │
│  - 服务发现                                                   │
│  - 健康检查                                                   │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                    MySQL（存储配置）                          │
└─────────────────────────────────────────────────────────────┘
```

## 快速启动

### 1. 启动MySQL（如果还未启动）

```bash
docker-compose -f mysql-ha.yml up -d
```

### 2. 创建Nacos数据库

```sql
-- 在MySQL Master执行
CREATE DATABASE nacos_config DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 创建Nacos表
docker exec -it mysql-master mysql -uroot -proot nacos_config < nacos-mysql.sql
```

Nacos SQL脚本下载地址：https://github.com/alibaba/nacos/blob/master/config/db/nacos-mysql.sql

### 3. 启动Nacos

```bash
docker-compose -f nacos.yml up -d
```

### 4. 访问Nacos控制台

- URL: http://localhost:8848/nacos
- 用户名: nacos
- 密码: nacos

## 配置管理

### 1. 创建命名空间

在Nacos控制台创建以下命名空间：

| 命名空间ID | 命名空间名称 | 说明 |
|-----------|-------------|------|
| public | 公共命名空间 | 默认 |
| dev | 开发环境 | 开发环境配置 |
| prod | 生产环境 | 生产环境配置 |

### 2. 创建配置文件

#### 公共配置（common.yaml）

```yaml
# Data ID: common.yaml
# Group: DEFAULT_GROUP
# Namespace: public

# Redis配置
redis:
  host: redis-master
  port: 6379
  password: 123456
  database: 0
  timeout: 3000ms

# RocketMQ配置
rocketmq:
  name-server: rocketmq:9876
  producer:
    group: seckill-producer-group
  consumer:
    group: seckill-consumer-group

# 日志配置
logging:
  level:
    root: INFO
    com.macro.mall: DEBUG
```

#### 网关配置（mall-gateway.yaml）

```yaml
# Data ID: mall-gateway.yaml
# Group: DEFAULT_GROUP
# Namespace: dev

server:
  port: 9200

spring:
  cloud:
    gateway:
      routes:
        - id: mall-seckill
          uri: lb://mall-seckill
          predicates:
            - Path=/api/seckill/**
          filters:
            - RewritePath=/api/seckill(?<segment>/?.*), /$\{segment}
            - RequestRateLimiter=redis://redis-master
        
        - id: mall-order
          uri: lb://mall-order
          predicates:
            - Path=/api/order/**
          filters:
            - RewritePath=/api/order(?<segment>/?.*), /$\{segment}
        
        - id: mall-stock
          uri: lb://mall-stock
          predicates:
            - Path=/api/stock/**
          filters:
            - RewritePath=/api/stock(?<segment>/?.*), /$\{segment}

# Sa-Token配置
sa-token:
  token-name: Authorization
  timeout: 604800
  is-concurrent: true
  is-share: false
  token-style: uuid
  is-read-header: true
  token-prefix: Bearer
  jwt-secret-key: ${JWT_SECRET_KEY:your-secret-key}
  is-print: false

# Sentinel配置
spring-cloud-alibaba-sentinel:
  datasource:
    ds:
      nacos:
        server-addr: ${NACOS_SERVER_ADDR:localhost:8848}
        data-id: ${spring.application.name}-sentinel
        group-id: DEFAULT_GROUP
        rule-type: flow
```

#### 秒杀服务配置（mall-seckill.yaml）

```yaml
# Data ID: mall-seckill.yaml
# Group: DEFAULT_GROUP
# Namespace: dev

server:
  port: 8089

spring:
  application:
    name: mall-seckill
  datasource:
    url: jdbc:mysql://mysql-master:3306/mall_seckill?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: root
    druid:
      initial-size: 10
      min-idle: 10
      max-active: 50
  data:
    redis:
      sentinel:
        master: mymaster
        nodes:
          - redis-sentinel1:26379
          - redis-sentinel2:26379
          - redis-sentinel3:26379
        database: 0
      password: 123456
      timeout: 3000ms

# 自定义秒杀配置
seckill:
  rate-limit:
    enabled: true
    max-qps: 1000
    per-user-qps: 10
  stock:
    cache-key: "seckill:stock:"
    local-cache-size: 1000
    local-cache-timeout: 5000
    expire-seconds: 3600
  order:
    topic: seckill-order-topic
    retry-count: 3
    retry-delay: 5000

# 缓存保护配置
cache:
  protection:
    bloom-filter:
      expected-insertions: 100000
      false-positive-rate: 0.01
    lock:
      timeout-seconds: 10
      retry-times: 3
    null-value:
      expire-seconds: 60

# RocketMQ配置
rocketmq:
  name-server: rocketmq:9876
  producer:
    group: seckill-producer-group
  consumer:
    group: seckill-consumer-group
  topic:
    seckill-order: seckill-order-topic
    stock-deduct: stock-deduct-topic
    order-persist: order-persist-topic
    order-timeout: order-timeout-topic

# Sa-Token配置
sa-token:
  token-name: Authorization
  timeout: 604800
  is-concurrent: true
  is-share: false
  token-style: uuid
  is-read-header: true
  token-prefix: Bearer
  jwt-secret-key: ${JWT_SECRET_KEY:your-secret-key}
  is-print: false

# Sentinel配置
spring-cloud-alibaba-sentinel:
  datasource:
    ds:
      nacos:
        server-addr: ${NACOS_SERVER_ADDR:localhost:8848}
        data-id: mall-seckill-sentinel
        group-id: DEFAULT_GROUP
        rule-type: flow

knife4j:
  enable: true
  setting:
    language: zh_CN
```

#### 订单服务配置（mall-order.yaml）

```yaml
# Data ID: mall-order.yaml
# Group: DEFAULT_GROUP
# Namespace: dev

server:
  port: 8087

spring:
  application:
    name: mall-order
  datasource:
    url: jdbc:mysql://mysql-master:3306/mall_order?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: root
    druid:
      initial-size: 10
      min-idle: 10
      max-active: 50
  data:
    redis:
      sentinel:
        master: mymaster
        nodes:
          - redis-sentinel1:26379
          - redis-sentinel2:26379
          - redis-sentinel3:26379
        database: 0
      password: 123456
      timeout: 3000ms

# RocketMQ配置
rocketmq:
  name-server: rocketmq:9876
  producer:
    group: seckill-order-producer-group
  consumer:
    group: seckill-order-consumer-group
  topic:
    seckill-order: seckill-order-topic
    stock-deduct: stock-deduct-topic
    order-persist: order-persist-topic
    order-timeout: order-timeout-topic

# 订单超时配置
order:
  timeout:
    default-minutes: 30
    scan-interval-seconds: 60

# Sa-Token配置
sa-token:
  token-name: Authorization
  timeout: 604800
  is-concurrent: true
  is-share: false
  token-style: uuid
  is-read-header: true
  token-prefix: Bearer
  jwt-secret-key: ${JWT_SECRET_KEY:your-secret-key}
  is-print: false

knife4j:
  enable: true
  setting:
    language: zh_CN
```

#### 库存服务配置（mall-stock.yaml）

```yaml
# Data ID: mall-stock.yaml
# Group: DEFAULT_GROUP
# Namespace: dev

server:
  port: 8088

spring:
  application:
    name: mall-stock
  datasource:
    url: jdbc:mysql://mysql-master:3306/mall_stock?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: root
    druid:
      initial-size: 10
      min-idle: 10
      max-active: 50
  data:
    redis:
      sentinel:
        master: mymaster
        nodes:
          - redis-sentinel1:26379
          - redis-sentinel2:26379
          - redis-sentinel3:26379
        database: 0
      password: 123456
      timeout: 3000ms

# RocketMQ配置
rocketmq:
  name-server: rocketmq:9876
  producer:
    group: stock-producer-group
  consumer:
    group: stock-consumer-group
  topic:
    stock-deduct: stock-deduct-topic

# Sa-Token配置
sa-token:
  token-name: Authorization
  timeout: 604800
  is-concurrent: true
  is-share: false
  token-style: uuid
  is-read-header: true
  token-prefix: Bearer
  jwt-secret-key: ${JWT_SECRET_KEY:your-secret-key}
  is-print: false

knife4j:
  enable: true
  setting:
    language: zh_CN
```

## 环境变量配置

### 1. 创建环境变量文件

```bash
# .env
export NACOS_SERVER_ADDR=localhost:8848
export NACOS_DISCOVERY_NAMESPACE=public
export NACOS_CONFIG_NAMESPACE=public
export SPRING_PROFILES_ACTIVE=dev
export JWT_SECRET_KEY=your-secret-key-change-in-production
```

### 2. 加载环境变量

```bash
source .env
```

### 3. Docker Compose环境变量

```yaml
# docker-compose.yml
services:
  mall-seckill:
    image: mall/mall-seckill:latest
    environment:
      - NACOS_SERVER_ADDR=nacos:8848
      - NACOS_DISCOVERY_NAMESPACE=public
      - NACOS_CONFIG_NAMESPACE=public
      - SPRING_PROFILES_ACTIVE=prod
      - JWT_SECRET_KEY=${JWT_SECRET_KEY}
    depends_on:
      - nacos
      - mysql-master
      - redis-sentinel
      - rocketmq
```

## 服务注册发现

### 1. 验证服务注册

访问Nacos控制台 -> 服务管理 -> 服务列表，应该能看到以下服务：

| 服务名 | 状态 | 实例数 |
|--------|------|--------|
| mall-gateway | UP | 1 |
| mall-seckill | UP | 1 |
| mall-order | UP | 1 |
| mall-stock | UP | 1 |
| mall-portal | UP | 1 |
| mall-admin | UP | 1 |
| mall-auth | UP | 1 |
| mall-search | UP | 1 |

### 2. 查看服务详情

点击服务名可以查看：
- 实例列表（IP、端口、健康状态）
- 元数据
- 权重配置

## 配置热更新

### 1. 修改配置

在Nacos控制台修改配置后，点击发布。

### 2. 验证热更新

```bash
# 查看应用日志，应该能看到配置更新日志
curl http://localhost:8089/actuator/refresh
```

### 3. 监听配置变化

```java
@RefreshScope
@Configuration
public class AppConfig {
    
    @Value("${seckill.rate-limit.max-qps:1000}")
    private int maxQps;
    
    // 配置变化后，maxQps会自动更新
}
```

## 高可用部署

### 1. Nacos集群

```yaml
# nacos-cluster.yml
version: '3.8'

services:
  nacos1:
    image: nacos/nacos-server:v2.3.2
    container_name: nacos1
    environment:
      - MODE=cluster
      - NACOS_SERVERS=nacos1:8848,nacos2:8848,nacos3:8848
      - SPRING_DATASOURCE_PLATFORM=mysql
      - MYSQL_SERVICE_HOST=mysql-master
      - MYSQL_SERVICE_PORT=3306
      - MYSQL_SERVICE_DB_NAME=nacos_config
      - MYSQL_SERVICE_USER=root
      - MYSQL_SERVICE_PASSWORD=root
    ports:
      - "8848:8848"
      - "9848:9848"
      - "9849:9849"
    volumes:
      - nacos1-data:/home/nacos/data
      - nacos1-logs:/home/nacos/logs
    networks:
      - mall-network

  nacos2:
    image: nacos/nacos-server:v2.3.2
    container_name: nacos2
    environment:
      - MODE=cluster
      - NACOS_SERVERS=nacos1:8848,nacos2:8848,nacos3:8848
      - SPRING_DATASOURCE_PLATFORM=mysql
      - MYSQL_SERVICE_HOST=mysql-master
      - MYSQL_SERVICE_PORT=3306
      - MYSQL_SERVICE_DB_NAME=nacos_config
      - MYSQL_SERVICE_USER=root
      - MYSQL_SERVICE_PASSWORD=root
    ports:
      - "8849:8848"
      - "9850:9848"
      - "9851:9849"
    volumes:
      - nacos2-data:/home/nacos/data
      - nacos2-logs:/home/nacos/logs
    networks:
      - mall-network

  nacos3:
    image: nacos/nacos-server:v2.3.2
    container_name: nacos3
    environment:
      - MODE=cluster
      - NACOS_SERVERS=nacos1:8848,nacos2:8848,nacos3:8848
      - SPRING_DATASOURCE_PLATFORM=mysql
      - MYSQL_SERVICE_HOST=mysql-master
      - MYSQL_SERVICE_PORT=3306
      - MYSQL_SERVICE_DB_NAME=nacos_config
      - MYSQL_SERVICE_USER=root
      - MYSQL_SERVICE_PASSWORD=root
    ports:
      - "8850:8848"
      - "9852:9848"
      - "9853:9849"
    volumes:
      - nacos3-data:/home/nacos/data
      - nacos3-logs:/home/nacos/logs
    networks:
      - mall-network

volumes:
  nacos1-data:
  nacos1-logs:
  nacos2-data:
  nacos2-logs:
  nacos3-data:
  nacos3-logs:

networks:
  mall-network:
    external: true
```

### 2. Nginx负载均衡

```nginx
# nginx.conf
upstream nacos {
    server nacos1:8848;
    server nacos2:8848;
    server nacos3:8848;
}

server {
    listen 8848;
    location / {
        proxy_pass http://nacos;
    }
}
```

## 常见问题

### 1. 服务无法注册

```bash
# 检查网络
docker exec -it mall-seckill ping nacos

# 检查日志
docker logs mall-seckill

# 检查配置
curl http://localhost:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=10
```

### 2. 配置无法热更新

```java
// 确保使用@RefreshScope
@RefreshScope
@Configuration
public class Config {
    @Value("${key}")
    private String value;
}
```

### 3. Nacos启动失败

```bash
# 查看日志
docker logs nacos

# 检查MySQL连接
docker exec -it nacos mysql -h mysql-master -uroot -proot
```

## 停止服务

```bash
# 停止Nacos
docker-compose -f nacos.yml down

# 删除数据卷（谨慎！）
docker volume rm docker_nacos-data docker_nacos-logs
```