# MySQL高可用架构部署

## 架构说明

```
┌─────────────────────────────────────────────────────────────┐
│                      应用服务                                │
│  mall-seckill / mall-order / mall-stock                     │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│              ShardingSphere读写分离                          │
│  写操作 → master                                             │
│  读操作 → slave1 / slave2 (轮询)                             │
└──────────────────────────┬──────────────────────────────────┘
                           │
              ┌────────────┴────────────┐
              ▼                         ▼
┌─────────────────────┐     ┌─────────────────────┐
│   MySQL Master      │     │   MySQL Slave       │
│   mysql-master:3307 │     │   mysql-slave1:3308 │
│   - 订单写入         │     │   mysql-slave2:3309 │
│   - 库存扣减         │     │   - 订单查询         │
│   - 用户信息更新     │     │   - 商品查询         │
└─────────────────────┘     └─────────────────────┘
```

## 快速启动

### 1. 启动MySQL集群

```bash
cd document/docker
docker-compose -f mysql-ha.yml up -d
```

### 2. 配置主从复制

#### 2.1 进入Master节点

```bash
docker exec -it mysql-master mysql -uroot -proot
```

#### 2.2 创建复制账号

```sql
-- 创建从库复制账号
CREATE USER 'repl'@'%' IDENTIFIED BY 'repl123';
GRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'repl'@'%';
FLUSH PRIVILEGES;
```

#### 2.3 查看Master状态

```sql
SHOW MASTER STATUS;
```

记录 `File` 和 `Position` 值，例如：
- File: mysql-bin.000003
- Position: 154

#### 2.4 进入Slave1节点

```bash
docker exec -it mysql-slave1 mysql -uroot -proot
```

#### 2.5 配置Slave1复制

```sql
CHANGE MASTER TO
    MASTER_HOST='mysql-master',
    MASTER_PORT=3306,
    MASTER_USER='repl',
    MASTER_PASSWORD='repl123',
    MASTER_LOG_FILE='mysql-bin.000003',
    MASTER_LOG_POSITION=154,
    GET_MASTER_PUBLIC_KEY=1;

START SLAVE;
SHOW SLAVE STATUS\G
```

确保 `Slave_IO_Running` 和 `Slave_SQL_Running` 都是 `Yes`。

#### 2.6 同样配置Slave2

```bash
docker exec -it mysql-slave2 mysql -uroot -proot
```

```sql
CHANGE MASTER TO
    MASTER_HOST='mysql-master',
    MASTER_PORT=3306,
    MASTER_USER='repl',
    MASTER_PASSWORD='repl123',
    MASTER_LOG_FILE='mysql-bin.000003',
    MASTER_LOG_POSITION=154,
    GET_MASTER_PUBLIC_KEY=1;

START SLAVE;
SHOW SLAVE STATUS\G
```

### 3. 创建数据库和账号

在Master节点执行：

```sql
-- 创建订单库
CREATE DATABASE mall_order DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE mall_seckill DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE mall_stock DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 创建业务账号
CREATE USER 'mall'@'%' IDENTIFIED BY 'mall123';
GRANT ALL PRIVILEGES ON mall_order.* TO 'mall'@'%';
GRANT ALL PRIVILEGES ON mall_seckill.* TO 'mall'@'%';
GRANT ALL PRIVILEGES ON mall_stock.* TO 'mall'@'%';
FLUSH PRIVILEGES;

-- 创建只读账号（用于从库）
CREATE USER 'reader'@'%' IDENTIFIED BY 'reader123';
GRANT SELECT ON mall_order.* TO 'reader'@'%';
GRANT SELECT ON mall_seckill.* TO 'reader'@'%';
GRANT SELECT ON mall_stock.* TO 'reader'@'%';
FLUSH PRIVILEGES;
```

### 4. 验证主从同步

```sql
-- 在Master创建测试数据
USE mall_order;
CREATE TABLE test (id INT PRIMARY KEY, name VARCHAR(100));
INSERT INTO test VALUES (1, 'test');

-- 在Slave1验证
USE mall_order;
SELECT * FROM test;
```

## 应用配置

各模块已配置读写分离，连接信息如下：

| 模块 | 主库 | 从库 |
|------|------|------|
| mall-order | mysql-master:3306/mall_order | mysql-slave1:3306,mysql-slave2:3306/mall_order |
| mall-seckill | mysql-master:3306/mall_seckill | mysql-slave1:3306,mysql-slave2:3306/mall_seckill |
| mall-stock | mysql-master:3306/mall_stock | mysql-slave1:3306,mysql-slave2:3306/mall_stock |

## Druid监控

访问 Druid Web 控制台：
- URL: http://localhost:8080/druid
- 用户名: admin
- 密码: admin123

## 健康检查

```bash
# 检查所有容器状态
docker-compose -f mysql-ha.yml ps

# 查看Master日志
docker logs mysql-master -f

# 查看Slave1日志
docker logs mysql-slave1 -f

# 检查主从同步状态
docker exec -it mysql-slave1 mysql -uroot -proot -e "SHOW SLAVE STATUS\G" | grep -E "Slave_IO_Running|Slave_SQL_Running"
```

## 停止集群

```bash
docker-compose -f mysql-ha.yml down

# 删除数据卷（谨慎！）
docker volume rm docker_mysql-master-data docker_mysql-slave1-data docker_mysql-slave2-data
```

## 常见问题

### 1. 主从同步延迟

```sql
-- 在Slave上查看延迟
SHOW SLAVE STATUS\G
-- 查看 Seconds_Behind_Master 值
```

优化建议：
- 减少大事务
- 启用并行复制：`slave_parallel_workers=4`
- 调整网络带宽

### 2. 复制中断

```bash
# 查看错误日志
docker logs mysql-slave1

# 跳过错误（谨慎使用）
SET GLOBAL SQL_SLAVE_SKIP_COUNTER = 1;
START SLAVE;
```

### 3. 新增从库

```bash
# 1. 启动新从库容器
docker run -d --name mysql-slave3 \
  -v mysql-slave3-data:/var/lib/mysql \
  -v ./mysql/slave.cnf:/etc/mysql/conf.d/slave.cnf \
  mysql:8.0

# 2. 配置复制（同Slave1步骤）
```

## 性能优化参数

### Master优化

```ini
innodb_buffer_pool_size=1G
innodb_log_file_size=256M
max_connections=1000
sync_binlog=1
binlog_expire_logs_seconds=604800
```

### Slave优化

```ini
innodb_buffer_pool_size=1G
slave_parallel_workers=4
slave_parallel_type=LOGICAL_CLOCK
read-only=ON
```