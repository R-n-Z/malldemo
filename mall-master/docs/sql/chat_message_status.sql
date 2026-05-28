-- 聊天消息支持撤回：添加 status 字段
-- 1=正常 2=已撤回
ALTER TABLE chat_message ADD COLUMN status INT NOT NULL DEFAULT 1 COMMENT '1=正常 2=已撤回' AFTER is_read;
