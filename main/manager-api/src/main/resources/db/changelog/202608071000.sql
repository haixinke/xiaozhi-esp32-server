-- liquibase formatted sql

-- changeset xiaozhi:202608071000
-- 故事引擎内容运营：大场景 / 小场景 / 动作 / 动作图片

CREATE TABLE IF NOT EXISTS `ai_story_big_scene` (
    `id`          VARCHAR(32)  NOT NULL COMMENT '主键UUID',
    `name`        VARCHAR(64)  NOT NULL COMMENT '大场景名称（如：在家、旅行、上学、打工）',
    `sort_order`  INT          NOT NULL DEFAULT 0 COMMENT '排序序号，越小越靠前',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 0=禁用',
    `creator`     BIGINT       NULL COMMENT '创建者ID',
    `create_date` DATETIME     NULL COMMENT '创建时间',
    `updater`     BIGINT       NULL COMMENT '更新者ID',
    `update_date` DATETIME     NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_status_sort` (`status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='故事引擎-大场景';

CREATE TABLE IF NOT EXISTS `ai_story_small_scene` (
    `id`               VARCHAR(32)  NOT NULL COMMENT '主键UUID',
    `big_scene_id`     VARCHAR(32)  NOT NULL COMMENT '所属大场景ID',
    `name`             VARCHAR(100) NOT NULL COMMENT '小场景名称（如：卧室、北京-故宫、快餐厅）',
    `weight_night`     INT          NOT NULL DEFAULT 0 COMMENT '深夜时段(00:00~05:59)权重百分比',
    `weight_morning`   INT          NOT NULL DEFAULT 0 COMMENT '上午时段(06:00~11:59)权重百分比',
    `weight_afternoon` INT          NOT NULL DEFAULT 0 COMMENT '下午时段(12:00~17:59)权重百分比',
    `weight_evening`   INT          NOT NULL DEFAULT 0 COMMENT '傍晚时段(18:00~23:59)权重百分比',
    `sort_order`       INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
    `status`           TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 0=禁用',
    `creator`          BIGINT       NULL COMMENT '创建者ID',
    `create_date`      DATETIME     NULL COMMENT '创建时间',
    `updater`          BIGINT       NULL COMMENT '更新者ID',
    `update_date`      DATETIME     NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_big_scene_status` (`big_scene_id`, `status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='故事引擎-小场景';

CREATE TABLE IF NOT EXISTS `ai_story_action` (
    `id`              VARCHAR(32)  NOT NULL COMMENT '主键UUID',
    `small_scene_id`  VARCHAR(32)  NOT NULL COMMENT '所属小场景ID',
    `name`            VARCHAR(100) NOT NULL COMMENT '动作名称（如：小憩、看书、故宫红墙前散步）',
    `duration_min`    INT          NOT NULL DEFAULT 1 COMMENT '最短时长（小时）',
    `duration_max`    INT          NOT NULL DEFAULT 2 COMMENT '最长时长（小时）',
    `sort_order`      INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
    `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 0=禁用',
    `creator`         BIGINT       NULL COMMENT '创建者ID',
    `create_date`     DATETIME     NULL COMMENT '创建时间',
    `updater`         BIGINT       NULL COMMENT '更新者ID',
    `update_date`     DATETIME     NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_small_scene_status` (`small_scene_id`, `status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='故事引擎-蛋宝宝动作';

CREATE TABLE IF NOT EXISTS `ai_story_action_image` (
    `id`            VARCHAR(32)   NOT NULL COMMENT '主键UUID',
    `action_id`     VARCHAR(32)   NOT NULL COMMENT '所属动作ID',
    `pet_prototype` VARCHAR(20)   NOT NULL COMMENT '宠物原型：锦鲤 / 玉兔',
    `time_of_day`   VARCHAR(10)   NOT NULL COMMENT '时段类型：白天 / 落日 / 黑夜',
    `image_url`     VARCHAR(512)  NOT NULL COMMENT '图片OSS完整URL',
    `captions`      VARCHAR(1000) NULL COMMENT '图片配文，多句用|分隔，前端随机展示一句',
    `sort_order`    INT           NOT NULL DEFAULT 0 COMMENT '排序序号（同组多图排序）',
    `creator`       BIGINT        NULL COMMENT '创建者ID',
    `create_date`   DATETIME      NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_action_proto_time` (`action_id`, `pet_prototype`, `time_of_day`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='故事引擎-动作图片';
