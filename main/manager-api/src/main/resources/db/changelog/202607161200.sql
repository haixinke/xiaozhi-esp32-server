CREATE TABLE `ai_pet_collection_card` (
    `id` VARCHAR(32) NOT NULL COMMENT '收藏卡唯一标识',
    `pet_id` VARCHAR(32) NOT NULL COMMENT '关联宠物ID',
    `image_url` VARCHAR(1024) NOT NULL COMMENT '收藏卡图片URL',
    `brief` VARCHAR(100) COMMENT '一句话简介',
    `source` VARCHAR(50) NOT NULL DEFAULT 'HATCH' COMMENT '来源类型: HATCH-破壳首卡',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序序号(0=最先获取)',
    `creator` BIGINT COMMENT '创建者',
    `create_date` DATETIME COMMENT '创建时间',
    `updater` BIGINT COMMENT '更新者',
    `update_date` DATETIME COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_pet_card_image` (`pet_id`, `image_url`),
    INDEX `idx_pet_card_sort` (`pet_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='宠物收藏卡表';
