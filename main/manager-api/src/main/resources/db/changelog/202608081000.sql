-- liquibase formatted sql

-- changeset minwang:202608081000
CREATE TABLE `ai_pet_story_state` (
    `id` VARCHAR(32) NOT NULL COMMENT '主键UUID',
    `pet_prototype` VARCHAR(20) NOT NULL COMMENT '宠物原型：锦鲤/玉兔',
    `runtime_status` VARCHAR(20) NOT NULL DEFAULT 'UNINITIALIZED' COMMENT 'UNINITIALIZED/ACTIVE',
    `big_scene_id` VARCHAR(32) NULL,
    `big_scene_name` VARCHAR(64) NULL,
    `small_scene_id` VARCHAR(32) NULL,
    `small_scene_name` VARCHAR(100) NULL,
    `action_id` VARCHAR(32) NULL,
    `action_name` VARCHAR(100) NULL,
    `action_image_id` VARCHAR(32) NULL,
    `weight_period` VARCHAR(20) NULL COMMENT 'NIGHT/MORNING/AFTERNOON/EVENING',
    `image_time_of_day` VARCHAR(20) NULL COMMENT 'DAY/SUNSET/NIGHT',
    `image_url` VARCHAR(512) NULL,
    `caption` VARCHAR(1000) NULL,
    `duration_hours` INT NULL,
    `started_at` DATETIME NULL,
    `expected_end_at` DATETIME NULL,
    `last_evaluated_hour` DATETIME NULL COMMENT 'Asia/Shanghai整点时槽',
    `creator` BIGINT NULL,
    `create_date` DATETIME NULL,
    `updater` BIGINT NULL,
    `update_date` DATETIME NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pet_story_state_prototype` (`pet_prototype`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='宠物原型共享故事当前状态';

CREATE TABLE `ai_pet_story_history` (
    `id` VARCHAR(32) NOT NULL COMMENT '历史主键UUID',
    `pet_prototype` VARCHAR(20) NOT NULL,
    `big_scene_id` VARCHAR(32) NOT NULL,
    `big_scene_name` VARCHAR(64) NOT NULL,
    `small_scene_id` VARCHAR(32) NOT NULL,
    `small_scene_name` VARCHAR(100) NOT NULL,
    `action_id` VARCHAR(32) NOT NULL,
    `action_name` VARCHAR(100) NOT NULL,
    `action_image_id` VARCHAR(32) NOT NULL,
    `weight_period` VARCHAR(20) NOT NULL,
    `image_time_of_day` VARCHAR(20) NOT NULL,
    `image_url` VARCHAR(512) NOT NULL,
    `caption` VARCHAR(1000) NULL,
    `duration_hours` INT NOT NULL,
    `started_at` DATETIME NOT NULL,
    `expected_end_at` DATETIME NOT NULL,
    `archived_at` DATETIME NOT NULL,
    `creator` BIGINT NULL,
    `create_date` DATETIME NULL,
    PRIMARY KEY (`id`),
    KEY `idx_pet_story_history_prototype_started` (`pet_prototype`, `started_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='宠物原型共享故事历史快照';

INSERT INTO `ai_pet_story_state` (`id`, `pet_prototype`, `runtime_status`, `create_date`)
VALUES
    (REPLACE(UUID(), '-', ''), '锦鲤', 'UNINITIALIZED', NOW()),
    (REPLACE(UUID(), '-', ''), '玉兔', 'UNINITIALIZED', NOW());
