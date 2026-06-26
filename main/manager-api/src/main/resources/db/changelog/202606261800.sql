-- 为 ai_companion 表添加经期相关字段
ALTER TABLE ai_companion
    ADD COLUMN menstrual_cycle_start DATE NULL COMMENT '经期开始日期',
    ADD COLUMN menstrual_cycle_length INT NULL COMMENT '周期长度（天）',
    ADD COLUMN menstrual_period_length INT NULL COMMENT '经期长度（天）';
