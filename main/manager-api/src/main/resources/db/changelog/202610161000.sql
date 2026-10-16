-- 游戏管理模块：游戏配置表 + GAME_TYPE 字典初始化
-- 游戏是星星激励体系的一部分，用户玩游戏可以赚取或消耗星星
-- 本 changeset 只交付配置层；玩游戏时实际发放/扣减星星的执行逻辑由后续业务模块接入

-- 游戏配置表
CREATE TABLE `ai_game` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `game_type` VARCHAR(64) NOT NULL COMMENT '游戏类型(字典GAME_TYPE的code)',
  `earn_stars` INT NOT NULL DEFAULT 0 COMMENT '赚取星星数(非负)',
  `consume_stars` INT NOT NULL DEFAULT 0 COMMENT '消耗星星数(非负)',
  `daily_limit` INT NOT NULL DEFAULT 0 COMMENT '每日每设备可玩次数上限(0=不限)',
  `icon` VARCHAR(512) NULL COMMENT '游戏图标OSS URL',
  `description` VARCHAR(500) NULL COMMENT '游戏说明',
  `entrances` VARCHAR(128) NOT NULL COMMENT '游戏入口(逗号分隔枚举code,如NOTE,ACTIVITY)',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态:1启用 0停用',
  `creator` BIGINT NULL COMMENT '创建者',
  `create_date` DATETIME NULL COMMENT '创建时间',
  `updater` BIGINT NULL COMMENT '更新者',
  `update_date` DATETIME NULL COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='游戏配置';

-- 初始化 GAME_TYPE 字典类型（id 106，之前 101-105 已被占用）
delete from `sys_dict_type` where `id` = 106;
INSERT INTO `sys_dict_type` (`id`, `dict_type`, `dict_name`, `remark`, `sort`, `creator`, `create_date`, `updater`, `update_date`) VALUES
(106, 'GAME_TYPE', '游戏类型', '游戏管理中游戏的类型', 0, 1, NOW(), 1, NOW());

-- 初始化 GAME_TYPE 字典数据（5条）
delete from `sys_dict_data` where `dict_type_id` = 106;
INSERT INTO `sys_dict_data` (`id`, `dict_type_id`, `dict_label`, `dict_value`, `remark`, `sort`, `creator`, `create_date`, `updater`, `update_date`) VALUES
(106001, 106, '一起画', 'DRAW_TOGETHER', '', 1, 1, NOW(), 1, NOW()),
(106002, 106, '听你说', 'LISTEN_TO_YOU', '', 2, 1, NOW(), 1, NOW()),
(106003, 106, '遇见你', 'MEET_YOU', '', 3, 1, NOW(), 1, NOW()),
(106004, 106, '旅行', 'TRAVEL', '', 4, 1, NOW(), 1, NOW()),
(106005, 106, '打工', 'WORK', '', 5, 1, NOW(), 1, NOW());
