-- 创建数据库mall
CREATE DATABASE `mall` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
-- 创建用户druid及登录密码
CREATE USER 'druid'@'%' IDENTIFIED BY 'druid705';
-- 将mall数据库的各项操作权限授予druid用户
GRANT CREATE,DROP,ALTER,INSERT,UPDATE,SELECT,DELETE on mall.* to 'druid'@'%' with grant OPTION;
-- 刷新授权
FLUSH PRIVILEGES;
