-- AI生成破壳收藏卡图片URL
ALTER TABLE `ai_pet` ADD COLUMN `collection_card_url` VARCHAR(1024) NULL COMMENT 'AI生成的破壳收藏卡图片URL' AFTER `avatar_url`;
