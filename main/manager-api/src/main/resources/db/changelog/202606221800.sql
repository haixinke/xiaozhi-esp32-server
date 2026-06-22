-- 为 ai_agent_chat_audio 表添加 oss_key 字段
ALTER TABLE ai_agent_chat_audio
ADD COLUMN oss_key VARCHAR(256) DEFAULT NULL COMMENT 'OSS对象存储路径' AFTER audio;
