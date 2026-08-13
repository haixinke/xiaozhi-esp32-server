-- liquibase formatted sql

-- changeset minwang:202608131000
-- 动作图片支持单标签（管理端分类标注，最长64字符），不参与运行时图片匹配
ALTER TABLE `ai_story_action_image`
    ADD COLUMN `tag` VARCHAR(64) NULL COMMENT '图片标签（管理端分类标注，最长64字符）' AFTER `captions`;
