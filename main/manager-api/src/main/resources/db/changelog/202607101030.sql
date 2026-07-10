-- 蛋宝宝孵化型宠物模型补充
-- 依据 PRD 5.2 孵化机制 / 5.4 破壳分享卡片 / 5.5 每日状态
-- 现有 birthDate 语义为"创建即出生"(演示逻辑)，孵化流程接入后将由 hatch_start_time/hatched_at 承担时间语义

ALTER TABLE `ai_pet`
    ADD COLUMN `hatch_status` VARCHAR(20) NOT NULL DEFAULT 'HATCHED' COMMENT '孵化状态: EGG-孵化中, HATCHED-已破壳' AFTER `today_mood`,
    ADD COLUMN `hatch_start_time` DATETIME NULL COMMENT '孵化开始时间(完成首个修炼任务时刻,7天倒计时起点)' AFTER `hatch_status`,
    ADD COLUMN `expected_hatch_time` DATETIME NULL COMMENT '预计破壳时间(=孵化开始时间+周期-已加速时长)' AFTER `hatch_start_time`,
    ADD COLUMN `hatched_at` DATETIME NULL COMMENT '实际破壳时间(分享卡片生日)' AFTER `expected_hatch_time`,
    ADD COLUMN `accelerated_minutes` INT NOT NULL DEFAULT 0 COMMENT '累计已加速孵化时长(分钟)' AFTER `hatched_at`,
    ADD COLUMN `avatar_url` VARCHAR(500) NULL COMMENT 'IP形象照片/头像URL(破壳时AI生成)' AFTER `accelerated_minutes`,
    ADD COLUMN `prototype` VARCHAR(50) NULL COMMENT 'IP形象原型(锦鲤/玉兔等)' AFTER `avatar_url`,
    ADD COLUMN `gender` VARCHAR(10) NULL COMMENT '性别: MALE/FEMALE/OTHER' AFTER `prototype`,
    ADD COLUMN `blood_type` VARCHAR(10) NULL COMMENT '血型(随机分配)' AFTER `gender`,
    ADD COLUMN `personality_brief` VARCHAR(50) NULL COMMENT '性格简介(20字以内,卡片展示用),与personality(系统提示词)区分' AFTER `blood_type`,
    ADD COLUMN `today_mood_date` DATE NULL COMMENT '今日心情对应日期(跨天重算判断)' AFTER `personality_brief`,
    ADD COLUMN `today_mood_sentence` VARCHAR(200) NULL COMMENT '今日心情一句话' AFTER `today_mood_date`;
