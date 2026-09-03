-- liquibase formatted sql

-- changeset minwang:202609031000
-- 故事当前状态增加窗户标签图配文快照：选中动作中 tag='窗户' 图片的 captions（| 分隔），随主图切换/轮换一并快照
ALTER TABLE `ai_pet_story_state`
    ADD COLUMN `tag_image_caption` VARCHAR(1000) NULL COMMENT '窗户标签图配文快照(选中动作中tag=窗户的图片captions,|分隔)' AFTER `tag_image_url`;
