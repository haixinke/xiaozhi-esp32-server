-- 动态亲密度：为 ai_companion 增加互动追踪字段，并为聊天历史加日窗查询索引。
ALTER TABLE ai_companion
    ADD COLUMN last_active_date DATE NULL COMMENT '最近活跃日（有用户消息的最后一天）',
    ADD COLUMN active_streak INT NOT NULL DEFAULT 0 COMMENT '连续活跃天数',
    ADD COLUMN intimacy_updated_date DATE NULL COMMENT '亲密度最近处理日期（防同日重复处理）';

-- 每日批处理按 chat_type + created_at 日窗聚合，补充覆盖索引
ALTER TABLE ai_agent_chat_history
    ADD INDEX idx_ai_agent_chat_history_type_created (chat_type, created_at);
