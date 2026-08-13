-- liquibase formatted sql

-- changeset minwang:202608131100
-- 故事当前状态增加窗户标签图 URL 快照：选中动作中 tag='窗户' 的图片 URL，按时段取当前时段候选图中的首张
ALTER TABLE `ai_pet_story_state`
    ADD COLUMN `tag_image_url` VARCHAR(512) NULL COMMENT '窗户标签图URL快照(选中动作中tag=窗户的图片,取当前时段首张)' AFTER `image_url`;
