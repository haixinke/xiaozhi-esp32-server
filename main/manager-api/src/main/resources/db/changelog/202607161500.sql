-- 重命名收藏卡URL字段为场景图URL字段
ALTER TABLE `ai_pet` DROP COLUMN `collection_card_url`;
ALTER TABLE `ai_pet` ADD COLUMN `scene_url` VARCHAR(500) NULL COMMENT '场景图URL' AFTER `avatar_url`;
